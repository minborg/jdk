package java.javaone.k;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static java.lang.foreign.ValueLayout.ADDRESS;

/**...*/
public class StableBindingsDemo {
    /**...*/
    public StableBindingsDemo() {}


    /*   SYNOPSIS

         #include <string.h>

         size_t strlen(const char *s);
         char *strcat(char *dest, const char *src);
     */

    private static final MemoryLayout SIZE_T =
            Linker.nativeLinker().canonicalLayouts().get("size_t");

    record Binding(String name, FunctionDescriptor descriptor){}

    static final StableBindings STABLE_BINDINGS = StableBindings.of(
            new Binding("strlen", FunctionDescriptor.of(SIZE_T, ADDRESS)),
            new Binding("strcat", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS))
    );

    /**...*/
    void main() throws Throwable {

        try (var arena = Arena.ofConfined()) {

            String javaOne = "JavaOne!";
            MemorySegment cJavaOne = arena.allocateFrom(javaOne);
            long len = (long) STABLE_BINDINGS.get("strlen").invokeExact(cJavaOne);
            System.out.printf("The string '%s' consists of %d characters.%n", javaOne, len);

            String hello = "Hello ";
            MemorySegment dest = arena.allocate(javaOne.length() + hello.length() + 1);
            dest.setString(0, hello);
            MemorySegment r = (MemorySegment) STABLE_BINDINGS.get("strcat").invokeExact(dest, cJavaOne);
            System.out.println(dest.getString(0));

        }

    }

    @FunctionalInterface
    interface StableBindings {

        MethodHandle get(String name);

        record StableBindingsImpl(
                Map<String, StableValue<MethodHandle>> delegate,
                Map<String, FunctionDescriptor> descriptors
        ) implements StableBindings {

            @Override
            public MethodHandle get(String name) {
                final StableValue<MethodHandle> stable = delegate.get(name);
                if (stable == null) {
                    throw new NoSuchElementException("Unknown binding: " + name);
                }
                return stable.orElseSet(() -> makeHandle(name, descriptors.get(name)));
            }

            private static MethodHandle makeHandle(String name,
                                                   FunctionDescriptor descriptor) {
                Linker linker = Linker.nativeLinker();
                MemorySegment symbol = linker.defaultLookup().findOrThrow(name);
                @SuppressWarnings("restricted") // Outside Java's safetynet
                MethodHandle strlen = linker.downcallHandle(symbol, descriptor);
                return strlen;
            }

        }

        static StableBindings of(Binding... bindings) {
            var delegate = Arrays.stream(bindings)
                    .collect(Collectors.toUnmodifiableMap(
                            Binding::name,
                            _ -> StableValue.<MethodHandle>of()
                    ));
            var descriptors = Arrays.stream(bindings)
                    .collect(Collectors.toUnmodifiableMap(
                            Binding::name,
                            Binding::descriptor
                    ));
            return new StableBindingsImpl(delegate, descriptors);
        }

    }

}
