/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.jdk.internal;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 2, jvmArgsAppend = {"--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--enable-preview"})
@Threads(Threads.MAX)   // Benchmark under contention
public class SharedSecretsBenchmark {

    static final Map<Class<? extends SharedSecrets.Access>, SharedSecrets.Access> MAP =
            Map.of(JavaLangAccess.class, SharedSecrets.get(JavaLangAccess.class));

    @Setup
    public void setup() throws IOException {

    }

    @Benchmark
    public void explicitMethod(Blackhole bh) {
        bh.consume(SharedSecrets.getJavaLangAccess());
    }

    @Benchmark
    public void classLiteral(Blackhole bh) {
        bh.consume(SharedSecrets.get(JavaLangAccess.class));
    }

    @Benchmark
    public void map(Blackhole bh) {
        bh.consume(MAP.get(JavaLangAccess.class));
    }

}
