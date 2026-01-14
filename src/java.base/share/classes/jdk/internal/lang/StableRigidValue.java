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

package jdk.internal.lang;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

public final class StableRigidValue<T> extends AbstractRigidValue<T> implements RigidValue<T> {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long CONTENTS_OFFSET = UNSAFE.objectFieldOffset(StableRigidValue.class, "contents");

    // Used reflectively
    @Stable
    private T contents;

    @Override
    boolean isStable() {
        return true;
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    T get0() {
        return (T) UNSAFE.getReferenceAcquire(this, CONTENTS_OFFSET);
    }

    void set0(T newValue) {
        UNSAFE.putReferenceRelease(this, CONTENTS_OFFSET, newValue);
    }

}
