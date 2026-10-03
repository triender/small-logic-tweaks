package net.enderirt.smalllogictweaks.core.platform;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.Path;

/**
 * VI: Triển khai IPlatformHelper dành riêng cho NeoForge (FML).
 * EN: IPlatformHelper implementation dedicated to NeoForge (FML).
 */
public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
