package jdk.internal.io;

import jdk.internal.misc.Blocker;
import jdk.internal.ref.PhantomCleanable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class InternalFileDescriptor {

    private int fd;

    private long handle;

    private Closeable parent;
    private List<Closeable> otherParents;
    private boolean closed;

    /**
     * true, if file is opened for appending.
     */
    private boolean append;

    static {
        initIDs();
    }

    /**
     * Cleanup in case FileDescriptor is not explicitly closed.
     */
    private PhantomCleanable<FileDescriptor> cleanup;

    /**
     * Constructs an (invalid) FileDescriptor object.
     * The fd or handle is set later.
     */
    public InternalFileDescriptor() {
        fd = -1;
        handle = -1;
    }

    /**
     * Used for standard input, output, and error only.
     * For Windows the corresponding handle is initialized.
     * For Unix the append mode is cached.
     * @param fd the raw fd number (0, 1, 2)
     */
    public InternalFileDescriptor(int fd) {
        this.fd = fd;
        this.handle = getHandle(fd);
        this.append = getAppend(fd);
    }


    /**
     * Tests if this file descriptor object is valid.
     *
     * @return  {@code true} if the file descriptor object represents a
     *          valid, open file, socket, or other active I/O connection;
     *          {@code false} otherwise.
     */
    public boolean valid() {
        return (handle != -1) || (fd != -1);
    }

    /**
     * Force all system buffers to synchronize with the underlying
     * device.  This method returns after all modified data and
     * attributes of this FileDescriptor have been written to the
     * relevant device(s).  In particular, if this FileDescriptor
     * refers to a physical storage medium, such as a file in a file
     * system, sync will not return until all in-memory modified copies
     * of buffers associated with this FileDescriptor have been
     * written to the physical medium.
     *
     * sync is meant to be used by code that requires physical
     * storage (such as a file) to be in a known state  For
     * example, a class that provided a simple transaction facility
     * might use sync to ensure that all changes to a file caused
     * by a given transaction were recorded on a storage medium.
     *
     * sync only affects buffers downstream of this FileDescriptor.  If
     * any in-memory buffering is being done by the application (for
     * example, by a BufferedOutputStream object), those buffers must
     * be flushed into the FileDescriptor (for example, by invoking
     * OutputStream.flush) before that data will be affected by sync.
     *
     * @throws SyncFailedException
     *        Thrown when the buffers cannot be flushed,
     *        or because the system cannot guarantee that all the
     *        buffers have been synchronized with physical media.
     * @since     1.1
     */
    public void sync() throws SyncFailedException {
        boolean attempted = Blocker.begin();
        try {
            sync0();
        } finally {
            Blocker.end(attempted);
        }
    }

    /* fsync/equivalent this file descriptor */
    private native void sync0() throws SyncFailedException;

    /* This routine initializes JNI field offsets for the class */
    private static native void initIDs();

    /*
     * On Windows return the handle for the standard streams.
     */
    private static native long getHandle(int d);

    /**
     * Returns true, if the file was opened for appending.
     */
    private static native boolean getAppend(int fd);

    /**
     * Set the fd.
     * Used on Unix and for sockets on Windows and Unix.
     * If setting to -1, clear the cleaner.
     * The {@link #registerCleanup} method should be called for new fds.
     * @param fd the raw fd or -1 to indicate closed
     */
    public synchronized void set(int fd) {
        if (fd == -1 && cleanup != null) {
            cleanup.clear();
            cleanup = null;
        }
        this.fd = fd;
    }

    /**
     * Set the handle.
     * Used on Windows for regular files.
     * If setting to -1, clear the cleaner.
     * The {@link #registerCleanup} method should be called for new handles.
     * @param handle the handle or -1 to indicate closed
     */
    void setHandle(long handle) {
        if (handle == -1 && cleanup != null) {
            cleanup.clear();
            cleanup = null;
        }
        this.handle = handle;
    }

    /**
     * Register a cleanup for the current handle.
     * Used directly in java.io and indirectly via fdAccess.
     * The cleanup should be registered after the handle is set in the FileDescriptor.
     * @param cleanable a PhantomCleanable to register
     */
    public synchronized void registerCleanup(PhantomCleanable<FileDescriptor> cleanable) {
        Objects.requireNonNull(cleanable, "cleanable");
        if (cleanup != null) {
            cleanup.clear();
        }
        cleanup = cleanable;
    }

    /**
     * Unregister a cleanup for the current raw fd or handle.
     * Used directly in java.io and indirectly via fdAccess.
     * Normally {@link #close()} should be used except in cases where
     * it is certain the caller will close the raw fd and the cleanup
     * must not close the raw fd.  {@link #unregisterCleanup()} must be
     * called before the raw fd is closed to prevent a race that makes
     * it possible for the fd to be reallocated to another use and later
     * the cleanup might be invoked.
     */
    synchronized void unregisterCleanup() {
        if (cleanup != null) {
            cleanup.clear();
        }
        cleanup = null;
    }

    /**
     * Close the raw file descriptor or handle, if it has not already been closed.
     * The native code sets the fd and handle to -1.
     * Clear the cleaner so the close does not happen twice.
     * Package private to allow it to be used in java.io.
     * @throws IOException if close fails
     */
    public synchronized void close1() throws IOException {
        unregisterCleanup();
        close0();
    }

    /*
     * Close the raw file descriptor or handle, if it has not already been closed
     * and set the fd and handle to -1.
     */
    private native void close0() throws IOException;

    /*
     * Package private methods to track referents.
     * If multiple streams point to the same FileDescriptor, we cycle
     * through the list of all referents and call close()
     */


    protected synchronized void attach0(Closeable c) {
        if (parent == null) {
            // first caller gets to do this
            parent = c;
        } else if (otherParents == null) {
            otherParents = new ArrayList<>();
            otherParents.add(parent);
            otherParents.add(c);
        } else {
            otherParents.add(c);
        }
    }

    protected synchronized void closeAll0(Closeable releaser) throws IOException {
        if (!closed) {
            closed = true;
            IOException ioe = null;
            try (releaser) {
                if (otherParents != null) {
                    for (Closeable referent : otherParents) {
                        try {
                            referent.close();
                        } catch(IOException x) {
                            if (ioe == null) {
                                ioe = x;
                            } else {
                                ioe.addSuppressed(x);
                            }
                        }
                    }
                }
            } catch(IOException ex) {
                /*
                 * If releaser close() throws IOException
                 * add other exceptions as suppressed.
                 */
                if (ioe != null)
                    ex.addSuppressed(ioe);
                ioe = ex;
            } finally {
                if (ioe != null)
                    throw ioe;
            }
        }
    }

    public static class JavaIOFileDescriptorAccessUtilImpl {

        public static void set(FileDescriptor fdo, int fd) {
            fdo.set(fd);
        }

        public static int get(FileDescriptor fdo) {
            return asInternal(fdo).fd;
        }

        public static void setAppend(FileDescriptor fdo, boolean append) {
            asInternal(fdo).append = append;
        }

        public static boolean getAppend(FileDescriptor fdo) {
            return asInternal(fdo).append;
        }

        public static void close(FileDescriptor fdo) throws IOException {
            asInternal(fdo).close0();
        }

        /* Register for a normal FileCleanable fd/handle cleanup. */
        public static void registerCleanup(FileDescriptor fdo) {
            FileCleanable.register(fdo);
        }

        /* Register a custom PhantomCleanup. */
        public static void registerCleanup(FileDescriptor fdo,
                                    PhantomCleanable<FileDescriptor> cleanup) {
            fdo.registerCleanup(cleanup);
        }

        public static void unregisterCleanup(FileDescriptor fdo) {
            asInternal(fdo).unregisterCleanup();
        }

        public static void setHandle(FileDescriptor fdo, long handle) {
            asInternal(fdo).setHandle(handle);
        }

        public static long getHandle(FileDescriptor fdo) {
            return asInternal(fdo).handle;
        }

        private static InternalFileDescriptor asInternal(FileDescriptor fd) {
            return fd;
        }

    }

}
