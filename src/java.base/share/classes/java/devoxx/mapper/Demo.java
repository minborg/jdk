package java.devoxx.mapper;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;

/** Demo */
public class Demo {

    /**...*/
    public Demo(){};

    /** Point */
    record Point(int x, int y){}

    /**
     * Demo app.
     * @param args ignored
     */
    public static void main(String[] args) {
        var segment = MemorySegment.ofArray(new int[]{3, 4});

        // get
        var point = new Point(segment.get(ValueLayout.JAVA_INT, 0), segment.get(ValueLayout.JAVA_INT, 4));
        System.out.println(point); // Point[x=3, y=4]

        // set
        var point2 = new Point(6, 8);
        segment.set(ValueLayout.JAVA_INT, 0, point2.x());
        segment.set(ValueLayout.JAVA_INT, 4, point2.y());
        System.out.println(Util.intsToString(segment)); // [6, 8]
    }

    record Line(Point begin, Point end){}

    record Group(List<Line> lines){}

    record Drawing(List<Group> groups){}

}
