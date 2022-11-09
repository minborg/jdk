package jdk.internal.foreign;

import java.lang.foreign.MemorySession;
import java.lang.ref.Cleaner;

public final class SessionFactory {

    private SessionFactory() {
    }

    private static final MemorySession GLOBAL = new GlobalSession(null);

    public static MemorySession global() {
        return GLOBAL;
    }

    public static MemorySessionImpl createConfined(Thread thread) {
        return new ConfinedSession(thread);
    }

    public static MemorySessionImpl createShared() {
        return new SharedSession();
    }

    public static MemorySessionImpl createImplicit(Cleaner cleaner) {
        return new ImplicitSession(cleaner);
    }
    public static MemorySessionImpl createHeap(Object ref) {
        return new GlobalSession(ref);
    }

}
