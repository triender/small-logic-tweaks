package net.enderirt.smalllogictweaks.core.platform;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ServiceLoader;

/**
 * VI: Bo nap va quan ly dich vu nen tang (Service Provider Locator).
 * EN: Service Provider Locator for cross-platform modloader services.
 */
public final class Services {
    private Services() {}

    /**
     * VI: The hien dich vu nen tang dang hoat dong.
     * EN: Active platform service instance.
     */
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    public static <T> T load(Class<T> clazz) {
        return ServiceLoader.load(clazz)
                .findFirst()
                .orElseGet(() -> {
                    if (clazz == IPlatformHelper.class) {
                        @SuppressWarnings("unchecked")
                        T fallback = (T) new IPlatformHelper() {
                            @Override
                            public Path getConfigDirectory() {
                                return Paths.get("config");
                            }

                            @Override
                            public boolean isModLoaded(String modId) {
                                return false;
                            }
                        };
                        return fallback;
                    }
                    throw new NullPointerException("Failed to load service for " + clazz.getName());
                });
    }
}
