/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.openjdk.bench.java.lang.foreign;

import org.openjdk.jmh.annotations.*;

import java.lang.foreign.*;
import java.lang.foreign.MemorySegment.Scope;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = { "--enable-native-access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED" })
public class AllocCompoundTest extends CLayouts {

    // Simulate an access pattern of
    // - 8-15 bytes  -> 51%
    // - 0    bytes  -> 48%
    //   16-31 bytes ->  1%
    @Benchmark
    public long alloc_confined() {
        long sum = 0;
        for (int i = 0; i < 51; i++) {
            try (Arena arena = Arena.ofConfined()) {
                sum += arena.allocate(8).address();
            }
        }
        for (int i = 0; i < 48; i++) {
            try (Arena arena = Arena.ofConfined()) {
                // No allocation
                sum += arena.hashCode();
            }
        }
        for (int i = 0; i < 1; i++) {
            try (Arena arena = Arena.ofConfined()) {
                sum += arena.allocate(16).address();
            }
        }
        return sum;
    }

    @Fork(jvmArgsAppend = {"-Djava.lang.foreign.native.confined.arena.pool-slots=0"})
    public static class NoPool extends AllocCompoundTest { }

}
