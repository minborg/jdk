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

package org.openjdk.bench.java.util;

import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(value = 2, jvmArgsAppend = {
        "--enable-preview"
})
public class MapViews {

    @Param({"1", "1000"})
    public int size;

    @Param({"HashMap", "LinkedHashMap", "TreeMap", "ConcurrentHashMap"})
    public String mapType;

    @Param({"false", "true"})
    public String cache;

    private Map<Integer, Integer> map;

    @Setup
    public void setup() {
        map = switch (mapType) {
            case "HashMap"           -> new HashMap<>();
            case "LinkedHashMap"     -> new LinkedHashMap<>();
            case "TreeMap"           -> new TreeMap<>();
            case "ConcurrentHashMap" -> new ConcurrentHashMap<>();
            default                  -> throw new IllegalStateException(mapType);
        };
        if (cache == Boolean.TRUE.toString()) {
            //map = new CachedMap<>(map);
            map = CachedMap.of(map);
        }
        for (int i = 0; i < size; i++) {
            map.put(i, i * i);
        }
    }

    @Benchmark
    public int keySetSize() {
        return map.keySet().size();
    }

    @Benchmark
    public int valuesSize() {
        return map.values().size();
    }

    @Benchmark
    public int entrySetSize() {
        return map.entrySet().size();
    }

    @Benchmark
    public int entrySetIterForEach() {
        int val = 0;
        for (Map.Entry<Integer, Integer> e : map.entrySet()) {
            val += e.getKey() + e.getValue();
        }
        return val;
    }

    // Old-stype caching HashMap
    record CachedMap<K, V>(Map<K, V> delegate,
                           LazyConstant<Set<Entry<K, V>>> lazyEntrySet,
                           LazyConstant<Set<K>> lazyKeySet,
                           LazyConstant<Collection<V>> lazyValues) implements Map<K, V> {

        // Required
        @Override public int              size() { return delegate.size(); }
        @Override public boolean          isEmpty() { return delegate.isEmpty(); }
        @Override public boolean          containsKey(Object key) { return delegate.containsKey(key); }
        @Override public boolean          containsValue(Object value) { return delegate.containsValue(value); }
        @Override public V                get(Object key) { return delegate.get(key); }
        @Override public V                put(K key, V value) { return delegate.put(key, value); }
        @Override public V                remove(Object key) { return delegate.remove(key); }
        @Override public void             putAll(Map<? extends K, ? extends V> m) { delegate.putAll(m); }
        @Override public void             clear() { delegate.clear(); }
        @Override public Set<K>           keySet() { return lazyKeySet.get(); }
        @Override public Collection<V>    values() { return lazyValues.get(); }
        @Override public Set<Entry<K, V>> entrySet() { return lazyEntrySet.get(); }

        // Object
        @Override public boolean          equals(Object obj) { return delegate.equals(obj); }
        @Override public int              hashCode() { return delegate.hashCode(); }
        @Override public String           toString() { return delegate.toString(); }

        // Optimized

        static <K, V> Map<K, V> of(Map<K, V> delegate) {
            return new CachedMap<>(delegate,
                    LazyConstant.of(delegate::entrySet),
                    LazyConstant.of(delegate::keySet),
                    LazyConstant.of(delegate::values));
        }

    }

}