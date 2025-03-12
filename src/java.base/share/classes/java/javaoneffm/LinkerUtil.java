package java.javaoneffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;

final class LinkerUtil {

    private LinkerUtil() {}

    static <T> T link(Class<T> type,
                      String name,
                      FunctionDescriptor descriptor) {

        Linker linker = Linker.nativeLinker();
        MemorySegment symbol = linker.defaultLookup().findOrThrow(name);
        @SuppressWarnings("restricted") // Outside Java's safetynet
        MethodHandle handle = linker.downcallHandle(symbol, descriptor);
        // The glue is here
        return MethodHandleProxies.asInterfaceInstance(type, handle);
    }

    static <T> T link(Class<T> type,
                      String name) {
        // Find the single abstract method in `type`
        // Retrieve the return type via reflection
        // Retrieve the calling parameter types via reflection
        // Convert the return type and parameter types to a FunctionalDescriptor fd = ...
        // return link(type, name, fd)
        throw new UnsupportedOperationException("Maybe come back another day?");
    }

}
