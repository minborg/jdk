/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * A small toolkit of classes that support lock-free thread-safe
 * lazy initialization of values.
 *
 * For example, the class {@code LazyReference} provide atomic
 * lazy evaluation:
 *
 * {@snippet lang = java :
 * class Foo {
 *   private final LazyReference<Bar> bar = LazyReference.of(Bar::new);
 *   public Bar bar() {
 *     return bar.get(); // Bar is computed here at first invocation
 *   }
 * }
 *}
 *
 * All methods of the classes in this package will throw a NullPointerException
 * if a parameter is {@code null}.
 *
 * @since 22
 */
package java.util.concurrent.lazy;
