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

package jdk.internal.util.zip;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import jdk.internal.util.IntFieldLayout;

/** The 32-bit DOS timestamp used by ZIP entries. */
public final class DosTime {
    private DosTime() {}

    private static final IntBits.Field YEAR;
    private static final IntBits.Field MONTH;
    private static final IntBits.Field DAY;
    private static final IntBits.Field HOUR;
    private static final IntBits.Field MINUTE;
    private static final IntBits.Field TWO_SECONDS;

    static {
        var layout = new IntFieldLayout();
        YEAR = layout.next(7);
        MONTH = layout.next(4);
        DAY = layout.next(5);
        HOUR = layout.next(5);
        MINUTE = layout.next(6);
        TWO_SECONDS = layout.next(5);
    }

    public static long pack(LocalDateTime time) {
        int word = YEAR.replace(0, time.getYear() - 1980);
        word = MONTH.replace(word, time.getMonthValue());
        word = DAY.replace(word, time.getDayOfMonth());
        word = HOUR.replace(word, time.getHour());
        word = MINUTE.replace(word, time.getMinute());
        word = TWO_SECONDS.replace(word, time.getSecond() / 2);
        return Integer.toUnsignedLong(word);
    }

    public static LocalDateTime toLocalDateTime(long extended) {
        int word = (int) extended;
        int millis = (int) (extended >> 32);
        return LocalDateTime.of(YEAR.extract(word) + 1980, MONTH.extract(word), DAY.extract(word),
                HOUR.extract(word), MINUTE.extract(word), TWO_SECONDS.extract(word) * 2 + millis / 1000,
                (millis % 1000) * 1_000_000);
    }

    public static long toEpochMillis(long dosTime) {
        int word = (int) dosTime;
        int year = YEAR.extract(word) + 1980;
        int month = MONTH.extract(word);
        int day = DAY.extract(word);
        int hour = HOUR.extract(word);
        int minute = MINUTE.extract(word);
        int second = TWO_SECONDS.extract(word) * 2;
        if (month > 0 && month < 13 && day > 0 && hour < 24 && minute < 60 && second < 60) {
            try {
                LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute, second);
                return TimeUnit.SECONDS.toMillis(ldt.toEpochSecond(
                        ZoneId.systemDefault().getRules().getOffset(ldt)));
            } catch (DateTimeException dte) {
                // ignore
            }
        }
        return overflowDosToJavaTime(year, month, day, hour, minute, second);
    }

    /*
     * Deal with corner cases where an arguably mal-formed DOS time is used
     */
    @SuppressWarnings("deprecation") // Use of Date constructor
    private static long overflowDosToJavaTime(int year, int month, int day,
                                              int hour, int minute, int second) {
        return new Date(year - 1900, month - 1, day, hour, minute, second).getTime();
    }
}
