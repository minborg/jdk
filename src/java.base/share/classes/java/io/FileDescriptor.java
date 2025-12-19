/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.io;

import jdk.internal.io.InternalFileDescriptor;

/**
 * Instances of the file descriptor class serve as an opaque handle
 * to the underlying machine-specific structure representing an open
 * file, an open socket, or another source or sink of bytes.
 * The main practical use for a file descriptor is to create a
 * {@link FileInputStream} or {@link FileOutputStream} to contain it.
 * <p>
 * Applications should not create their own file descriptors.
 *
 * @author  Pavani Diwanji
 * @since   1.0
 */
@SuppressWarnings("exports") // Todo: check this
public final class FileDescriptor extends InternalFileDescriptor {

    /**
     * Constructs an (invalid) FileDescriptor object.
     * The fd or handle is set later.
     */
    public FileDescriptor() {
        super();
    }

    /**
     * Used for standard input, output, and error only.
     * For Windows the corresponding handle is initialized.
     * For Unix the append mode is cached.
     * @param fd the raw fd number (0, 1, 2)
     */
    private FileDescriptor(int fd) {
        super(fd);
    }

    /**
     * A handle to the standard input stream. Usually, this file
     * descriptor is not used directly, but rather via the input stream
     * known as {@code System.in}.
     *
     * @see     java.lang.System#in
     */
    public static final FileDescriptor in = new FileDescriptor(0);

    /**
     * A handle to the standard output stream. Usually, this file
     * descriptor is not used directly, but rather via the output stream
     * known as {@code System.out}.
     * @see     java.lang.System#out
     */
    public static final FileDescriptor out = new FileDescriptor(1);

    /**
     * A handle to the standard error stream. Usually, this file
     * descriptor is not used directly, but rather via the output stream
     * known as {@code System.err}.
     *
     * @see     java.lang.System#err
     */
    public static final FileDescriptor err = new FileDescriptor(2);

    /**
     * Tests if this file descriptor object is valid.
     *
     * @return  {@code true} if the file descriptor object represents a
     *          valid, open file, socket, or other active I/O connection;
     *          {@code false} otherwise.
     */
    public boolean valid() {
        return super.valid();
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
     * @throws    SyncFailedException
     *        Thrown when the buffers cannot be flushed,
     *        or because the system cannot guarantee that all the
     *        buffers have been synchronized with physical media.
     * @since     1.1
     */
    public void sync() throws SyncFailedException {
        super.sync();
    }

    /*
     * Package private methods to track referents.
     * If multiple streams point to the same FileDescriptor, we cycle
     * through the list of all referents and call close()
     */

    /**
     * Attach a Closeable to this FD for tracking.
     * parent reference is added to otherParents when
     * needed to make closeAll simpler.
     */
    void attach(Closeable c) {
        super.attach0(c);
    }

    /**
     * Close the raw file descriptor or handle, if it has not already been closed.
     * The native code sets the fd and handle to -1.
     * Clear the cleaner so the close does not happen twice.
     * Package private to allow it to be used in java.io.
     * @throws IOException if close fails
     */
    void close() throws IOException {
        super.close1();
    }

    /**
     * Cycle through all Closeables sharing this FD and call
     * close() on each one.
     *
     * The caller closeable gets to call close0().
     */
    void closeAll(Closeable releaser) throws IOException {
        super.closeAll0(releaser);
    }

}
