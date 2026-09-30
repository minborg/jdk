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
 * @summary Lazy field access honors the supplied lookup across module boundaries
 * @enablePreview
 * @library lookupmodules
 * @build lazy.lookup.owner/*
 * @run junit/othervm --add-modules lazy.lookup.owner LazyFieldLookupModulesTest
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.Lazy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LazyFieldLookupModulesTest {
    private static Class<?> owner;
    private static Lookup ownerLookup;

    @BeforeAll
    static void loadOwner() throws ReflectiveOperationException {
        owner = Class.forName("lookupowner.FieldOwner");
        ownerLookup = (Lookup) owner.getMethod("lookup").invoke(null);
        Module ownerModule = owner.getModule();
        Module clientModule = LazyFieldLookupModulesTest.class.getModule();
        assertTrue(ownerModule.isNamed());
        assertNotSame(ownerModule, clientModule);
        assertTrue(ownerModule.isExported(owner.getPackageName(), clientModule));
        assertFalse(ownerModule.isOpen(owner.getPackageName(), clientModule));
        assertSame(owner, ownerLookup.lookupClass());
    }

    @Test
    void clientLookupsCannotAccessThePrivateField() {
        checkPrivateAccessDenied(MethodHandles.lookup());
        checkPrivateAccessDenied(MethodHandles.publicLookup());
    }

    @Test
    void delegatedOwnerLookupAccessesThePrivateField() throws ReflectiveOperationException {
        // This unrelated caller uses authority deliberately delegated by the owner.
        checkAccess(ownerLookup, owner, "privateValue", "privateValue");
    }

    @Test
    void restrictedOwnerLookupCannotAccessThePrivateField() {
        checkPrivateAccessDenied(ownerLookup.dropLookupMode(Lookup.PRIVATE));
    }

    @Test
    void publicLookupAccessesTheExportedPublicField() throws ReflectiveOperationException {
        checkAccess(MethodHandles.publicLookup(), owner, "publicValue", "publicValue");
    }

    @Test
    void privateLookupInRequiresAnOpenPackage() {
        assertThrows(IllegalAccessException.class,
                () -> MethodHandles.privateLookupIn(owner, MethodHandles.lookup()));
    }

    private static void checkPrivateAccessDenied(Lookup lookup) {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofField(lookup, owner, "privateValue", Object.class,
                        receiver -> new Object()));
        assertInstanceOf(IllegalAccessException.class, failure.getCause());
    }

    private static <C> void checkAccess(Lookup lookup, Class<C> owner,
                                       String fieldName, String getterName)
            throws ReflectiveOperationException {
        Object expected = new Object();
        var lazy = Lazy.ofField(lookup, owner, fieldName, Object.class,
                receiver -> expected);
        C receiver = owner.getConstructor().newInstance();
        assertSame(expected, lazy.apply(receiver));
        assertSame(expected, owner.getMethod(getterName).invoke(receiver));
        assertSame(expected, lazy.apply(receiver));
    }
}
