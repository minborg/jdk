package java.devoxx.mapper;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.List;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/** Demo2 */
public class Demo2 {

    /**...*/
    public Demo2(){};

    /** Point */
    record Point(int x, int y){}

    private static final StructLayout POINT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"), // @ 0
            JAVA_INT.withName("y")            // @ 4
    );

    record Line(Point begin, Point end){}

    record Group(List<Demo.Line> lines){}

    /**
     * Demo app.
     * @param args ignored
     */
    public static void main(String[] args) {
        var segment = MemorySegment.ofArray(new int[]{3, 4});

        var mapper = RecordMapper.of(Point.class, POINT);

        // get
        var point = mapper.get(segment, 0);
        System.out.println(point); // Point[x=3, y=4]

        // set
        mapper.set(segment, 0, new Point(6, 8));
        System.out.println(Util.intsToString(segment)); // [6, 8]

        // Lines

        var lineLayout = MemoryLayout.structLayout(
                POINT.withName("begin"),
                POINT.withName("end")
        );
        var lineMapper = RecordMapper.of(Line.class, lineLayout);

        var lineSegment = MemorySegment.ofArray(new int[]{0, 0, 3, 4});
        // get
        var line = lineMapper.get(lineSegment, 0);
        System.out.println(line); // Line[begin=Point[x=0, y=0], end=Point[x=3, y=4]]

        // set
        lineMapper.set(lineSegment, 0,
                new Line(
                        new Point(10, 10),
                        new Point(13, 14)));
        System.out.println(Util.intsToString(lineSegment)); // [10, 10, 13, 14]

        // Groups

        var groupLayout = MemoryLayout.structLayout(
                MemoryLayout.sequenceLayout(10, lineLayout)
                        .withName("lines")
        );

        // var groupMapper = RecordMapper.of(Group.class, groupLayout);
        // var group = groupMapper.get(...)

    }

}
