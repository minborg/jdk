package java.devoxx.mapper;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

/** RecordMapper
 * @param <T> record type
 * */
public interface RecordMapper<T extends Record> {

    /**
     * {@return a new record of type T}
     * @param segment to use
     * @param offset  at which retrieval shall begin
     */
    T get(MemorySegment segment, long offset);

    /**
     * Sets memory to represent the given value of type T
     * @param segment to use
     * @param offset  at which retrieval shall begin
     * @param value   to set
     */
    void set(MemorySegment segment, long offset, T value);


    /**
     * {@return a new RecordMapper}
     * @param clazz to reflect
     * @param layout to apply when reading/writing
     * @param <T> record type
     */
    @SuppressWarnings("unchecked")
    static <T extends Record> RecordMapper<T> of(Class<T> clazz,
                                                 GroupLayout layout) {

        if (clazz.equals(Demo2.Point.class)) {

            // Generated code
            return (RecordMapper<T>) new RecordMapper<Demo2.Point>() {

                private final VarHandle xHandle = layout.varHandle(PathElement.groupElement("x"));
                private final VarHandle yHandle = layout.varHandle(PathElement.groupElement("y"));

                private final long xOffset = layout.byteOffset(PathElement.groupElement("x"));
                private final long yOffset = layout.byteOffset(PathElement.groupElement("y"));

                @Override
                public Demo2.Point get(MemorySegment segment, long offset) {
                    return new Demo2.Point((int) xHandle.get(segment, offset), (int) yHandle.get(segment, offset));
                }

                @Override
                public void set(MemorySegment segment, long offset, Demo2.Point value) {
                    segment.set(ValueLayout.JAVA_INT, xOffset + offset, value.x());
                    segment.set(ValueLayout.JAVA_INT, yOffset + offset, value.y());
                }
            };

        } else if (clazz.equals(Demo2.Line.class)) {

            // Generated code
            return (RecordMapper<T>) new RecordMapper<Demo2.Line>() {

                private final RecordMapper<Demo2.Point> beginMapper =
                        RecordMapper.of(Demo2.Point.class, (GroupLayout) layout.select(PathElement.groupElement("begin")));
                private final RecordMapper<Demo2.Point> endMapper =
                        RecordMapper.of(Demo2.Point.class, (GroupLayout) layout.select(PathElement.groupElement("end")));

                private final long beginOffset = layout.byteOffset(PathElement.groupElement("begin"));
                private final long endOffset = layout.byteOffset(PathElement.groupElement("end"));

                @Override
                public Demo2.Line get(MemorySegment segment, long offset) {
                    return new Demo2.Line(
                            beginMapper.get(segment, beginOffset + offset),
                            endMapper.get(segment, endOffset + offset)
                    );
                }

                @Override
                public void set(MemorySegment segment, long offset, Demo2.Line value) {
                    beginMapper.set(segment, beginOffset + offset, value.begin());
                    endMapper.set(segment, endOffset + offset, value.end());
                }
            };

        }
        throw new UnsupportedOperationException();
    }

}
