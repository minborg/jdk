package jdk.internal.foreign;

import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.MemorySegment;
import java.util.Iterator;
import java.util.function.Consumer;

// Todo: Explore a stack with chunks of segments to avoid pointer chasing
final class UnboundSegmentStack implements Iterable<MemorySegment> {

    record Node(Node next, MemorySegment segment) {}

    private Node first;

    @ForceInline
    void push(MemorySegment segment) {
        first = new Node(first, segment);
    }

    @ForceInline
    MemorySegment pop() {
        final Node f = first;
        if (f == null) {
            return null;
        } else {
            first = f.next();
            return f.segment;
        }
    }

    @Override
    public Iterator<MemorySegment> iterator() {
        return new Iterator<MemorySegment>() {
            Node current = first;

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public MemorySegment next() {
                final MemorySegment segment = current.segment;
                current = current.next;
                return segment;
            }
        };
    }

    @ForceInline
    @Override
    public void forEach(Consumer<? super MemorySegment> action) {
        for (Node node = first; node != null; node=node.next) {
            action.accept(node.segment);
        }
    }
}
