/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary A LazyCaches mode cannot be passed to a LazyUpdaters factory
 * @enablePreview
 * @compile/fail OfLocationModeTypeMismatch.java
 */

import java.lang.invoke.LazyUpdaters;
import java.lang.invoke.MethodHandles;
import java.util.LazyCaches;
import java.util.function.IntSupplier;

class OfLocationModeTypeMismatch {
    private static int value;

    void test() {
        LazyUpdaters.ofLocation(LazyCaches.Mode.PUBLISH_ONCE, MethodHandles.lookup(),
                IntSupplier.class,
                OfLocationModeTypeMismatch.class.getName() + ".value", () -> 42);
    }
}
