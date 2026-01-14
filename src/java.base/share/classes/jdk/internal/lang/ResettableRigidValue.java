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

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MutableCallSite;
import java.util.NoSuchElementException;

public final class ResettableRigidValue<T> extends AbstractRigidValue<T> implements RigidValue<T> {

    private static final MethodHandle NULL_TARGET =MethodHandles.constant(Object.class, null);

    @Stable
    private final MutableCallSite contents;
    @Stable
    private final MethodHandle contentsHandle;

    public ResettableRigidValue() {
        this.contents = new MutableCallSite(NULL_TARGET);
        this.contentsHandle = contents.dynamicInvoker();
    }

    @Override
    boolean isStable() {
        return false;
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    T get0() {
        try {
            return (T) contentsHandle.invokeExact();
        } catch (Throwable e) {
            throw new NoSuchElementException(e);
        }
    }

    @Override
    void set0(T newValue) {
        try {
            contents.setTarget(MethodHandles.constant(Object.class, newValue));
            // Todo: synchronize
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }
}
