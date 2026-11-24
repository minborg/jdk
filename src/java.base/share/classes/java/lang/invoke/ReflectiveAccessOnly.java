/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jdk.internal.javac.PreviewFeature;

/**
 * Indicates that a field or the elements of an array stored in a field are
 * intended to be accessed only through reflective or other indirect access
 * mechanisms, such as {@link VarHandle} or {@link StableFieldAccessors}.
 * <p>
 * By default, this annotation applies to the annotated field. Client code must
 * not directly read or write that field using a Java field access. The intended
 * compiler behavior is to reject such accesses with a compile-time error.
 * <p>
 * When {@link #arrayElements() arrayElements} is {@code true}, this annotation
 * instead applies to the elements of arrays referenced by the annotated field,
 * and the field reference itself may be accessed normally. In this mode the
 * annotated field must have an array type. Client code must not directly read
 * or write those elements using Java array access expressions; it may use an
 * indirect access mechanism to do so.
 * <p>
 * An annotation on a field does not annotate an array object or track its
 * aliases. In particular, a compiler cannot necessarily identify an array
 * access through another reference as accessing an array stored in an annotated
 * field. Clients must respect the restriction even when using such a reference.
 * For a multidimensional array, the element restriction applies to the
 * elements of the array directly referenced by the field, not automatically to
 * the elements of arrays referenced by those elements.
 * <p>
 * The annotation does not change access-control rules for reflective or indirect
 * access. It does not itself provide synchronization or memory-ordering effects.
 *
 * @implNote This annotation is not yet enforced by the Java compiler or the VM.
 *           Direct accesses and use of {@code arrayElements = true} on a
 *           non-array field are not currently rejected by this implementation.
 * @since 29
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
public @interface ReflectiveAccessOnly {
    /**
     * Whether this annotation applies to array elements instead of the field.
     *
     * @return {@code true} to restrict direct access to the elements of arrays
     *         referenced by the annotated field; {@code false} to restrict
     *         direct access to the annotated field itself
     */
    boolean arrayElements() default false;
}
