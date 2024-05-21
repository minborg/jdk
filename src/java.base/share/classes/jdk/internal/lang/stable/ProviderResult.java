package jdk.internal.lang.stable;

sealed interface ProviderResult {

    record Error<X extends Throwable>(Class<X> throwableClass) implements ProviderResult {}

    enum NonNull implements ProviderResult { INSTANCE;
        @Override public String toString() { return "NonNull"; }
    }

    enum Null implements ProviderResult { INSTANCE;
        @Override public String toString() { return "Null"; }
    }

}
