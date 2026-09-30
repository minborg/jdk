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
 * @summary Lazy map and set computations receive retained keys and candidates
 * @enablePreview
 * @run junit LazyCanonicalKeysTest
 */

import java.util.Lazy;
import java.util.Lazy.Mode;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static java.util.Lazy.Mode.PUBLISH_ONCE;
import static org.junit.jupiter.api.Assertions.*;

class LazyCanonicalKeysTest {
    // Equality is symmetric across the base and subclass; metadata is not part of it.
    static class Key {
        final int id;

        Key(int id) {
            this.id = id;
        }

        @Override
        public final boolean equals(Object other) {
            return other instanceof Key key && key.id == id;
        }

        @Override
        public final int hashCode() {
            return id;
        }
    }

    static final class DetailedKey extends Key {
        final String metadata;

        DetailedKey(int id, String metadata) {
            super(id);
            this.metadata = metadata;
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void mapComputesFromRetainedKeys(Mode mode) {
        DetailedKey first = new DetailedKey(1, "first retained value");
        DetailedKey second = new DetailedKey(2, "second retained value");
        AtomicInteger calls = new AtomicInteger();
        Map<DetailedKey, String> lazy = Lazy.ofMap(mode, Set.of(first, second), key -> {
            assertSame(key.id == first.id ? first : second, key);
            calls.incrementAndGet();
            return key.metadata;
        });
        DetailedKey absent = new DetailedKey(3, "absent");
        assertNull(lazy.get(absent));
        assertEquals("default", lazy.getOrDefault(absent, "default"));
        assertThrows(NullPointerException.class, () -> lazy.get(null));
        assertThrows(NullPointerException.class, () -> lazy.getOrDefault(null, "default"));
        assertEquals(0, calls.get());

        assertEquals(first.metadata, lazy.get(new DetailedKey(1, "first query value")));
        assertEquals(second.metadata,
                lazy.getOrDefault(new DetailedKey(2, "second query value"), "default"));
        assertEquals(first.metadata, lazy.get(first));
        assertEquals(second.metadata, lazy.get(second));
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void setComputesFromRetainedCandidates(Mode mode) {
        DetailedKey included = new DetailedKey(1, "include");
        DetailedKey excluded = new DetailedKey(2, "exclude");
        AtomicInteger calls = new AtomicInteger();
        Set<DetailedKey> lazy = Lazy.ofSet(mode, Set.of(included, excluded), candidate -> {
            assertSame(candidate.id == included.id ? included : excluded, candidate);
            calls.incrementAndGet();
            return candidate.metadata.equals("include");
        });
        assertFalse(lazy.contains(new DetailedKey(3, "include")));
        assertThrows(NullPointerException.class, () -> lazy.contains(null));
        assertEquals(0, calls.get());

        assertFalse(lazy.contains(new DetailedKey(2, "include")));
        assertFalse(lazy.contains(excluded));
        assertEquals(1, calls.get());
        assertTrue(lazy.contains(new DetailedKey(1, "exclude")));
        assertTrue(lazy.contains(included));
        assertEquals(2, calls.get());
        var iterator = lazy.iterator();
        assertSame(included, iterator.next());
        assertFalse(iterator.hasNext());
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void mapAcceptsEqualQueriesOutsideTheKeyType(Mode mode) {
        DetailedKey retained = new DetailedKey(1, "retained metadata");
        Key query = new Key(1);
        assertEquals(retained, query);
        assertEquals(query, retained);
        AtomicInteger calls = new AtomicInteger();
        Map<DetailedKey, String> lazy = Lazy.ofMap(mode, Set.of(retained), key -> {
            assertSame(retained, key);
            calls.incrementAndGet();
            return key.metadata;
        });

        assertEquals(retained.metadata, lazy.get(query));
        assertEquals(retained.metadata, lazy.getOrDefault(new Key(1), "default"));
        assertEquals(retained.metadata, lazy.get(retained));
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void setAcceptsEqualQueriesOutsideTheCandidateType(Mode mode) {
        DetailedKey retained = new DetailedKey(1, "include");
        Key query = new Key(1);
        assertEquals(retained, query);
        assertEquals(query, retained);
        AtomicInteger calls = new AtomicInteger();
        Set<DetailedKey> lazy = Lazy.ofSet(mode, Set.of(retained), candidate -> {
            assertSame(retained, candidate);
            calls.incrementAndGet();
            return candidate.metadata.equals("include");
        });

        assertTrue(lazy.contains(query));
        assertTrue(lazy.contains(retained));
        assertSame(retained, lazy.iterator().next());
        assertEquals(1, calls.get());
    }

    @Test
    void mapRetryUsesTheRetainedKey() {
        DetailedKey retained = new DetailedKey(1, "retained metadata");
        AtomicInteger calls = new AtomicInteger();
        IllegalArgumentException failure = new IllegalArgumentException("first computation");
        Map<DetailedKey, String> lazy = Lazy.ofMap(PUBLISH_ONCE, Set.of(retained), key -> {
            assertSame(retained, key);
            if (calls.incrementAndGet() == 1) {
                throw failure;
            }
            return key.metadata;
        });

        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> lazy.get(new DetailedKey(1, "first query metadata"))));
        assertEquals(retained.metadata, lazy.get(new DetailedKey(1, "retry metadata")));
        assertEquals(retained.metadata, lazy.get(retained));
        assertEquals(2, calls.get());
    }
}
