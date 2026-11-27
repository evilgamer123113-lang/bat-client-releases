package com.batclient.platform;

import java.util.Iterator;
import java.util.ServiceLoader;

/** Exactly one provider is packaged for a loader. No guessed working-directory fallback. */
public final class Platforms {
    private Platforms() { }
    private static final class Holder {
        private static final PlatformEnvironment INSTANCE = discover(ServiceLoader.load(
                PlatformEnvironment.class, Platforms.class.getClassLoader()));
    }
    public static PlatformEnvironment get() { return Holder.INSTANCE; }
    public static PlatformEnvironment discover(Iterable<PlatformEnvironment> providers) {
        Iterator<PlatformEnvironment> iterator = providers.iterator();
        if (!iterator.hasNext()) throw new IllegalStateException("Bat Client platform provider is missing");
        PlatformEnvironment environment = iterator.next();
        if (iterator.hasNext()) throw new IllegalStateException("Multiple Bat Client loader providers are installed");
        if (environment == null || environment.getGameDir() == null || environment.getConfigDir() == null)
            throw new IllegalStateException("Bat Client platform returned invalid directories");
        return environment;
    }
}
