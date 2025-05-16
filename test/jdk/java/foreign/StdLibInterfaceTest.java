/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm/timeout=600 --enable-native-access=ALL-UNNAMED StdLibInterfaceTest
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.testng.Assert.*;

public class StdLibInterfaceTest extends NativeTestHelper {

    static final Linker ABI = Linker.nativeLinker();
    static final FunctionalStdLibHelper HELPER = new FunctionalStdLibHelper();


    public interface BaseRand {
        int rand();
    }

    // Illegal interfaces to be tested

    private interface Private extends BaseRand {} ;

    public sealed interface Sealed extends BaseRand {
        final class SealedImpl implements Sealed {
            @Override public int rand() { return 0; }
        }
    }

    @Test
    void invariants() {
        var randSymbol = ABI.defaultLookup().findOrThrow("rand");
        var randSignature = FunctionDescriptor.of(C_INT);

        var private_ = expectThrows(IllegalArgumentException.class, () -> ABI.downcallHandle(Private.class, randSymbol, randSignature));
        assertEquals(private_.getMessage(), "not a public interface: StdLibInterfaceTest$Private");
        var sealed = expectThrows(IllegalArgumentException.class, () -> ABI.downcallHandle(Sealed.class, randSymbol, randSignature));
        assertEquals(sealed.getMessage(), "a sealed interface: StdLibInterfaceTest$Sealed");
        expectThrows(NullPointerException.class, () -> ABI.downcallHandle(null, randSymbol, randSignature));
    }

    @Test(dataProvider = "stringPairs")
    void test_strcat(String s1, String s2) {
        assertEquals(HELPER.strcat(s1, s2), s1 + s2);
    }

    @Test(dataProvider = "stringPairs")
    void test_strcmp(String s1, String s2) {
        assertEquals(Math.signum(HELPER.strcmp(s1, s2)), Math.signum(s1.compareTo(s2)));
    }

    @Test(dataProvider = "strings")
    void test_puts(String s) {
        assertTrue(HELPER.puts(s) >= 0);
    }

    @Test(dataProvider = "strings")
    void test_strlen(String s) {
        assertEquals(HELPER.strlen(s), s.length());
    }

    @Test(dataProvider = "instants")
    void test_time(Instant instant) {
        FunctionalStdLibHelper.Tm tm = HELPER.gmtime(instant.getEpochSecond());
        LocalDateTime localTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        assertEquals(tm.sec(), localTime.getSecond());
        assertEquals(tm.min(), localTime.getMinute());
        assertEquals(tm.hour(), localTime.getHour());
        //day pf year in Java has 1-offset
        assertEquals(tm.yday(), localTime.getDayOfYear() - 1);
        assertEquals(tm.mday(), localTime.getDayOfMonth());
        //days of week starts from Sunday in C, but on Monday in Java, also account for 1-offset
        assertEquals((tm.wday() + 6) % 7, localTime.getDayOfWeek().getValue() - 1);
        //month in Java has 1-offset
        assertEquals(tm.mon(), localTime.getMonth().getValue() - 1);
        assertEquals(tm.isdst(), ZoneOffset.UTC.getRules()
                .isDaylightSavings(Instant.ofEpochMilli(instant.getEpochSecond() * 1000)));
    }

    @Test(dataProvider = "ints")
    void test_qsort(List<Integer> ints) {
        if (!ints.isEmpty()) {
            int[] input = ints.stream().mapToInt(i -> i).toArray();
            int[] sorted = HELPER.qsort(input);
            Arrays.sort(input);
            assertEquals(sorted, input);
        }
    }

    @Test
    void test_rand() {
        int val = HELPER.rand();
        for (int i = 0 ; i < 100 ; i++) {
            int newVal = HELPER.rand();
            if (newVal != val) {
                return; //ok
            }
            val = newVal;
        }
        fail("All values are the same! " + val);
    }

    public static class FunctionalStdLibHelper {

        @FunctionalInterface public interface StrCat {  MemorySegment strcat(MemorySegment dest, MemorySegment src); }

        static final StrCat STR_CAT = ABI.downcallHandle(StrCat.class, ABI.defaultLookup().findOrThrow("strcat"),
                FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER));

        @FunctionalInterface public interface StrCmp {  int strcmp(MemorySegment s1, MemorySegment s2); }

        static final StrCmp STR_CMP = ABI.downcallHandle(StrCmp.class, ABI.defaultLookup().findOrThrow("strcmp"),
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER));

        @FunctionalInterface public interface PutS {  int puts(MemorySegment s); }

        static final PutS PUT_S = ABI.downcallHandle(PutS.class, ABI.defaultLookup().findOrThrow("puts"),
                FunctionDescriptor.of(C_INT, C_POINTER));

        @FunctionalInterface public interface StrLen {  int strlen(MemorySegment s); }

        static final StrLen STR_LEN = ABI.downcallHandle(StrLen.class, ABI.defaultLookup().findOrThrow("strlen"),
                FunctionDescriptor.of(C_INT, C_POINTER));

        @FunctionalInterface public interface GmTime {  MemorySegment gmtime(MemorySegment timer); }

        static final GmTime GM_TIME = ABI.downcallHandle(GmTime.class, ABI.defaultLookup().findOrThrow("gmtime"),
                FunctionDescriptor.of(C_POINTER.withTargetLayout(Tm.LAYOUT), C_POINTER));

        static final FunctionDescriptor QSORT_COMPARE_FUNCTION = FunctionDescriptor.of(C_INT,
                C_POINTER.withTargetLayout(C_INT), C_POINTER.withTargetLayout(C_INT));

        static final MethodHandle QSORT_COMPARE;

        static {
            try {
                QSORT_COMPARE = MethodHandles.lookup().findStatic(FunctionalStdLibHelper.class, "qsortCompare", QSORT_COMPARE_FUNCTION.toMethodType());
            } catch (ReflectiveOperationException e) {
                throw new InternalError(e);
            }
        }

        @FunctionalInterface public interface QSort {  void qsort(MemorySegment ptr, long count, long size, MemorySegment comp); }
        @FunctionalInterface public interface QSortInt {  void qsort(MemorySegment ptr, int count, int size, MemorySegment comp); }

        // void qsort( void *ptr, size_t count, size_t size, int (*comp)(const void *, const void *) );
        static final QSort Q_SORT;
        static final QSortInt Q_SORT_INT;

        static {
            if (C_SIZE_T.byteSize() == JAVA_LONG.byteSize()) {
                Q_SORT = ABI.downcallHandle(QSort.class, ABI.defaultLookup().findOrThrow("qsort"),
                        FunctionDescriptor.ofVoid(C_POINTER, C_SIZE_T, C_SIZE_T, C_POINTER));
                Q_SORT_INT = null;
            } else {
                Q_SORT = null;
                Q_SORT_INT = ABI.downcallHandle(QSortInt.class, ABI.defaultLookup().findOrThrow("qsort"),
                        FunctionDescriptor.ofVoid(C_POINTER, C_SIZE_T, C_SIZE_T, C_POINTER));;
            }
        }

        @FunctionalInterface public interface Rand {  int rand(); }

        static final Rand RAND = ABI.downcallHandle(Rand.class, ABI.defaultLookup().findOrThrow("rand"),
                FunctionDescriptor.of(C_INT));


        String strcat(String s1, String s2) {
            try (var arena = Arena.ofConfined()) {
                MemorySegment buf = arena.allocate(s1.length() + s2.length() + 1);
                buf.setString(0, s1);
                MemorySegment other = arena.allocateFrom(s2);
                return STR_CAT.strcat(buf, other).getString(0);
            }
        }

        int strcmp(String s1, String s2) {
            try (var arena = Arena.ofConfined()) {
                MemorySegment ns1 = arena.allocateFrom(s1);
                MemorySegment ns2 = arena.allocateFrom(s2);
                return STR_CMP.strcmp(ns1, ns2);
            }
        }

        int puts(String msg) {
            try (var arena = Arena.ofConfined()) {
                MemorySegment s = arena.allocateFrom(msg);
                return PUT_S.puts(s);
            }
        }

        int strlen(String msg) {
            try (var arena = Arena.ofConfined()) {
                MemorySegment s = arena.allocateFrom(msg);
                return STR_LEN.strlen(s);
            }
        }

        Tm gmtime(long arg) {
            try (var arena = Arena.ofConfined()) {
                MemorySegment time = arena.allocate(8);
                time.set(C_LONG_LONG, 0, arg);
                return new Tm(GM_TIME.gmtime(time));
            }
        }

        static class Tm {

            //Tm pointer should never be freed directly, as it points to shared memory
            private final MemorySegment base;

            static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
                    C_INT.withName("sec"),
                    C_INT.withName("min"),
                    C_INT.withName("hour"),
                    C_INT.withName("mday"),
                    C_INT.withName("mon"),
                    C_INT.withName("year"),
                    C_INT.withName("wday"),
                    C_INT.withName("yday"),
                    C_BOOL.withName("isdst"),
                    MemoryLayout.paddingLayout(3)
            );

            Tm(MemorySegment addr) {
                this.base = addr;
            }

            int sec() {
                return base.get(C_INT, 0);
            }
            int min() {
                return base.get(C_INT, 4);
            }
            int hour() {
                return base.get(C_INT, 8);
            }
            int mday() {
                return base.get(C_INT, 12);
            }
            int mon() {
                return base.get(C_INT, 16);
            }
            int year() {
                return base.get(C_INT, 20);
            }
            int wday() {
                return base.get(C_INT, 24);
            }
            int yday() {
                return base.get(C_INT, 28);
            }
            boolean isdst() {
                return base.get(C_BOOL, 32);
            }
        }

        int[] qsort(int[] arr) {
            //init native array
            try (var arena = Arena.ofConfined()) {
                MemorySegment nativeArr = arena.allocateFrom(C_INT, arr);

                //call qsort
                MemorySegment qsortUpcallStub = ABI.upcallStub(QSORT_COMPARE, QSORT_COMPARE_FUNCTION, arena);

                // both of these fit in an int
                // automatically widen them to long on x64
                int count = arr.length;
                int size = (int) C_INT.byteSize();
                if (Q_SORT != null) {
                    Q_SORT.qsort(nativeArr, count, size, qsortUpcallStub);
                } else {
                    Q_SORT_INT.qsort(nativeArr, count, size, qsortUpcallStub);
                }

                //convert back to Java array
                return nativeArr.toArray(C_INT);
            }
        }

        static int qsortCompare(MemorySegment addr1, MemorySegment addr2) {
            return addr1.get(C_INT, 0) -
                    addr2.get(C_INT, 0);
        }

        int rand() {
            return RAND.rand();
        }

    }

    /*** data providers ***/

    @DataProvider
    public static Object[][] ints() {
        return perms(0, new Integer[] { 0, 1, 2, 3, 4 }).stream()
                .map(l -> new Object[] { l })
                .toArray(Object[][]::new);
    }

    @DataProvider
    public static Object[][] strings() {
        return perms(0, new String[] { "a", "b", "c" }).stream()
                .map(l -> new Object[] { String.join("", l) })
                .toArray(Object[][]::new);
    }

    @DataProvider
    public static Object[][] stringPairs() {
        Object[][] strings = strings();
        Object[][] stringPairs = new Object[strings.length * strings.length][];
        int pos = 0;
        for (Object[] s1 : strings) {
            for (Object[] s2 : strings) {
                stringPairs[pos++] = new Object[] { s1[0], s2[0] };
            }
        }
        return stringPairs;
    }

    @DataProvider
    public static Object[][] instants() {
        Instant start = ZonedDateTime.of(LocalDateTime.parse("2017-01-01T00:00:00"), ZoneOffset.UTC).toInstant();
        Instant end = ZonedDateTime.of(LocalDateTime.parse("2017-12-31T00:00:00"), ZoneOffset.UTC).toInstant();
        Object[][] instants = new Object[100][];
        for (int i = 0 ; i < instants.length ; i++) {
            Instant instant = start.plusSeconds((long)(Math.random() * (end.getEpochSecond() - start.getEpochSecond())));
            instants[i] = new Object[] { instant };
        }
        return instants;
    }

    static <Z> Set<List<Z>> perms(int count, Z[] arr) {
        if (count == arr.length) {
            return Set.of(List.of());
        } else {
            return Arrays.stream(arr)
                    .flatMap(num -> {
                        Set<List<Z>> perms = perms(count + 1, arr);
                        return Stream.concat(
                                //take n
                                perms.stream().map(l -> {
                                    List<Z> li = new ArrayList<>(l);
                                    li.add(num);
                                    return li;
                                }),
                                //drop n
                                perms.stream());
                    }).collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
