/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.foreign;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment.Scope;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.LongAdder;

public sealed class ArenaImpl implements Arena
        permits BufferStack.PerThread.Frame, ThreadConfinedSegmentPool.CachedArena {

    final MemorySessionImpl session;
    final boolean shouldReserveMemory;
    private final boolean collectAllocationHistogram;
    private boolean allocationHistogramRecorded;

    ArenaImpl(MemorySessionImpl session) {
        this(session, false);
    }

    ArenaImpl(MemorySessionImpl session, boolean collectAllocationHistogram) {
        this.session = session;
        shouldReserveMemory = session instanceof ImplicitSession;
        this.collectAllocationHistogram = collectAllocationHistogram && ConfinedArenaHistogram.ENABLED;
        super();
    }

    @Override
    public final Scope scope() {
        return session;
    }

    @Override
    public void close() {
        session.close();
    }

    public NativeMemorySegmentImpl allocateNoInit(long byteSize, long byteAlignment) {
        NativeMemorySegmentImpl segment = SegmentFactories.allocateNativeSegment(byteSize, byteAlignment, session, shouldReserveMemory, false);
        ConfinedArenaHistogram.record(byteSize);
        return segment;
    }

    @Override
    public NativeMemorySegmentImpl allocate(long byteSize, long byteAlignment) {
        NativeMemorySegmentImpl segment = SegmentFactories.allocateNativeSegment(byteSize, byteAlignment, session, shouldReserveMemory, true);
        ConfinedArenaHistogram.record(byteSize);
        return segment;
    }

}

final class ConfinedArenaHistogram {

    private static final String PROPERTY = "java.lang.foreign.native.confined.arena.histogram";
    private static final String OUTPUT = System.getProperty(PROPERTY, "/Users/minborg/dev/minborg-jdk/histograms");
    static final boolean ENABLED = OUTPUT != null;

    private static final LongAdder[] COUNTS = ENABLED ? newCounters() : null;
    private static final LongAdder[] BYTES = ENABLED ? newCounters() : null;

    static {
        if (ENABLED) {
            Runtime.getRuntime().addShutdownHook(new Thread(
                    ConfinedArenaHistogram::dump, "ConfinedArenaHistogram"));
        }
    }

    private ConfinedArenaHistogram() {
    }

    static void record(long byteSize) {
        int bucket = bucket(byteSize);
        COUNTS[bucket].increment();
        BYTES[bucket].add(byteSize);
    }

    private static LongAdder[] newCounters() {
        LongAdder[] counters = new LongAdder[Long.SIZE];
        for (int i = 0; i < counters.length; i++) {
            counters[i] = new LongAdder();
        }
        return counters;
    }

    private static int bucket(long byteSize) {
        return byteSize <= 0 ? 0 : Long.SIZE - 1 - Long.numberOfLeadingZeros(byteSize) + 1;
    }

    @SuppressWarnings("try")
    private static void dump() {
        String histogram = histogram();
        if (histogram.isEmpty()) {
            return;
        }
        if (OUTPUT.isEmpty() || OUTPUT.equals("true") || OUTPUT.equals("stderr")) {
            System.err.print(histogram);
            return;
        }
        try {
            Path path = Path.of(OUTPUT);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (FileChannel channel = FileChannel.open(path,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                 FileLock lock = channel.lock()) {
                channel.write(StandardCharsets.UTF_8.encode(histogram));
                channel.force(true);
            }
        } catch (Throwable ex) {
            System.err.println("Could not write confined arena allocation histogram: " + ex);
            System.err.print(histogram);
        }
    }

    private static String histogram() {
        StringBuilder sb = new StringBuilder();
        boolean header = false;
        for (int i = 0; i < COUNTS.length; i++) {
            long count = COUNTS[i].sum();
            if (count == 0) {
                continue;
            }
            if (!header) {
                sb.append("# confined arena allocation histogram, pid=")
                        .append(ProcessHandle.current().pid())
                        .append(System.lineSeparator())
                        .append("# minBytes,maxBytes,arenaCount,totalBytes")
                        .append(System.lineSeparator());
                header = true;
            }
            sb.append(bucketMin(i)).append(',')
                    .append(bucketMax(i)).append(',')
                    .append(count).append(',')
                    .append(BYTES[i].sum()).append(System.lineSeparator());
        }
        return sb.toString();
    }

    private static long bucketMin(int bucket) {
        return bucket == 0 ? 0 : 1L << (bucket - 1);
    }

    private static long bucketMax(int bucket) {
        return bucket == 0 ? 0 : bucket == Long.SIZE - 1 ? Long.MAX_VALUE : (1L << bucket) - 1;
    }
}
