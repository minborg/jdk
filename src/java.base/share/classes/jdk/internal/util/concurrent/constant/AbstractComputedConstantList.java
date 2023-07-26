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

package jdk.internal.util.concurrent.constant;

import jdk.internal.ValueBased;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@ValueBased
sealed abstract class AbstractComputedConstantList<E>
        extends AbstractList<E>
        implements List<E>
        permits ComputedConstantList,
        IntComputedConstantList {

    protected static final VarHandle OBJECT_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle BYTE_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(byte[].class);

    private final IntFunction<? extends E> presetMapper;

    private final Object[] locks;
    private final byte[] states;

    protected AbstractComputedConstantList(int size, IntFunction<? extends E> presetMapper) {
        this.presetMapper = presetMapper;
        this.locks = new Object[size];
        this.states = new byte[size];
    }

    @Override
    public int size() {
        return states.length;
    }

    @Override
    public E get(int index) {
        E e = element(index);
        if (isNotDefaultValue(e)) {
            return e;
        }
        if (states[index] == ConstantUtil.NULL) {
            return null;
        }
        return slowPath(index);
    }

    private E slowPath(int index) {
        Object lock = lockVolatile(index);
        if (lock instanceof ConstantUtil.Bound) {
            return elementVolatile(index);
        }
        if (lock == null) {
            lock = new Object();
            if (!casLock(index, lock)) {
                // Another thread won
                lock = lockVolatile(index);
            }
        }
        synchronized (lock) {
            return switch (states[index]) {
                case ConstantUtil.BINDING -> throw circular(index);
                case ConstantUtil.NON_NULL -> element(index);
                case ConstantUtil.NULL -> null;
                case ConstantUtil.BIND_ERROR -> throw error(index);
                default -> {
                    setStateVolatile(index, ConstantUtil.BINDING);
                    try {
                        E v = presetMapper.apply(index);
                        if (v == null) {
                            setStateVolatile(index, ConstantUtil.NULL);
                            setLockVolatile(index, ConstantUtil.NULL);
                        } else {
                            casElement(index, v);
                            setStateVolatile(index, ConstantUtil.NON_NULL);
                            setLockVolatile(index, ConstantUtil.NON_NULL);
                        }
                        // We do not need the lock object anymore
                        yield v;
                    } catch (Throwable e) {
                        setStateVolatile(index, ConstantUtil.BIND_ERROR);
                        if (e instanceof Error err) {
                            // Always rethrow errors
                            throw err;
                        }
                        throw new IllegalArgumentException(e);
                    }
                }
            };
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + IntStream.range(0, size())
                .mapToObj(i -> switch (stateVolatile(i)) {
                    case ConstantUtil.BINDING -> ".binding";
                    case ConstantUtil.NON_NULL -> elementVolatile(i).toString();
                    case ConstantUtil.NULL -> "null";
                    case ConstantUtil.BIND_ERROR -> ".error";
                    default -> ".unbound";
                })
                .collect(Collectors.joining()) + "]";
    }


    abstract boolean isNotDefaultValue(E value);

    abstract E element(int index);

    abstract E elementVolatile(int index);

    abstract void casElement(int index, E value);

    private static StackOverflowError circular(int index) {
        return new StackOverflowError("Circular mapper detected for index: " + index);
    }

    private static NoSuchElementException error(int index) {
        return new NoSuchElementException("A previous mapper threw an exception for index: " + index);
    }

    private void setStateVolatile(int index, byte value) {
        BYTE_ARRAY_HANDLE.setVolatile(states, index, value);
    }

    private byte stateVolatile(int index) {
        return (byte) BYTE_ARRAY_HANDLE.getVolatile(states, index);
    }

    private Object lockVolatile(int index) {
        return OBJECT_ARRAY_HANDLE.getVolatile(locks, index);
    }

    private void setLockVolatile(int index, Object lock) {
        OBJECT_ARRAY_HANDLE.setVolatile(locks, index, lock);
    }

    private boolean casLock(int index, Object lock) {
        return OBJECT_ARRAY_HANDLE.compareAndSet(locks, index, null, lock);
    }

}
