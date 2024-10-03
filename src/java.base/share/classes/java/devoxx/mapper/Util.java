package java.devoxx.mapper;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;

final class Util {

    private Util() {}

    static String intsToString(MemorySegment segment) {
        return Arrays.toString(segment.toArray(ValueLayout.JAVA_INT));
    }

}
