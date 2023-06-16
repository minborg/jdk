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

/*
 * @test
 * @summary Inspect the object layout of lazy implementations
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.util.concurrent.lazy
 * @run junit ObjectLayoutTest
 */

import jdk.internal.misc.Unsafe;
import jdk.internal.util.concurrent.lazy.PreEvaluatedLazyValue;
import jdk.internal.util.concurrent.lazy.AbstractLazyValue;
import org.junit.jupiter.api.*;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class ObjectLayoutTest {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /**
     * Inspect the object layout of lazy implementations
     *
     * @param args unused
     */
    public static void main(String[] args) {
        analyze(AbstractLazyValue.class);
        analyze(PreEvaluatedLazyValue.class);
        //analyze(OptimizedReferenceLazyArray.class);
    }

    static void analyze(Class<?> c) {
        System.out.println("Fields of " + c.getName());

/*        intanceFields(c)
                .forEach(System.out::println);*/
/*
        System.out.println("details:");*/

        intanceFields(c)
                .map(cf -> new ClassFieldOffset(cf.clazz().getSimpleName(), cf.fieldName(), (int) UNSAFE.objectFieldOffset(cf.clazz(), cf.fieldName())))
                .sorted(Comparator.comparingInt(ClassFieldOffset::offset))
                .forEach(System.out::println);

        System.out.println();
    }

    @SuppressWarnings("unchecked")
    static <T> Stream<ClassFieldName> intanceFields(Class<T> clazz) {
        return Stream.iterate(clazz, c -> (Class<T>) c.getSuperclass())
                .takeWhile(c -> c != Object.class)
                .flatMap(c -> Arrays.stream(c.getDeclaredFields())
                        .filter(f -> !Modifier.isStatic(f.getModifiers()))
                        .map(f -> new ClassFieldName(c, f.getName())));
    }

    record ClassFieldName(Class<?> clazz, String fieldName){
    }

    record ClassFieldOffset(String klass, String field, int offset) {
    }

    static void printMark(Object o) {
        int[] mark = new int[3];
        for (int i = 0; i < mark.length; i++) {
            mark[i] = UNSAFE.getInt(o, i * Integer.BYTES);
        }

        var hex = Arrays.stream(mark)
                .boxed()
                .map(i -> Integer.toHexString(i))
                .collect(Collectors.joining(", "));

        System.out.println(hex);
    }

}
