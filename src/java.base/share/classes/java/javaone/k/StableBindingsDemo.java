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
public class StableBindings {
    /**...*/
    public StableBindings() {}


    /*   SYNOPSIS

         #include <string.h>

         size_t strlen(const char *s);
     */

    private static final MemoryLayout SIZE_T =
            Linker.nativeLinker().canonicalLayouts().get("size_t");

    record Binding(String name, FunctionDescriptor descriptor){}

    static final Bindings BINDINGS = Bindings.of(
            new Binding("strlen", FunctionDescriptor.of(SIZE_T, ADDRESS))
    );

    /**...*/
    void main() {




/*        Linker linker = Linker.nativeLinker();
        MemorySegment symbol = linker.defaultLookup().findOrThrow("strlen");
        @SuppressWarnings("restricted") // Dangerous stuff...
        MethodHandle strlen = linker.downcallHandle(symbol, FunctionDescriptor.of(SIZE_T, ADDRESS));
        System.out.println("strlen = " + strlen);*/

        try (var arena = Arena.ofConfined()) {
            MemorySegment text = arena.allocateFrom("JavaOne!");
            long len = (long) BINDINGS.get("strlen").invokeExact(text);
            System.out.println("len = " + len);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }


    }

    @FunctionalInterface
    interface Bindings {

        MethodHandle get(String name);

        record BindingsImpl(
                Map<String, StableValue<MethodHandle>> delegate,
                Map<String, FunctionDescriptor> descriptors
        ) implements Bindings {

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

        static Bindings of(Binding... bindings) {
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
            return new BindingsImpl(delegate, descriptors);
        }

    }

}
