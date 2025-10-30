/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.lang.stable;

import jdk.internal.misc.Unsafe;
import org.openjdk.jmh.annotations.*;

import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

/**
 * Benchmark measuring fence performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 3, jvmArgs = {
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
public class StablePlusFenceBenchmark {

/*
    record Holder(Object object) {}
    private static final Holder HOLDER = new Holder(new Object());
*/

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long OBJECT_OFFSET = UNSAFE.objectFieldOffset(StablePlusFenceBenchmark.class, "object");

    Object object = new Object();

    // Atomics
    @Benchmark
    public Object acquire() {
        return UNSAFE.getReferenceAcquire(this, OBJECT_OFFSET);
    }

    @Benchmark
    public Object plainPlusAcquireFence() {
        var o =  UNSAFE.getReference(this, OBJECT_OFFSET);
        VarHandle.acquireFence();
        return o;
    }

}
