package net.enderirt.smalllogictweaks.core.platform;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;

/**
 * VI: Trien khai IPlatformHelper danh rieng cho he sinh thai Fabric Loader.
 * EN: IPlatformHelper implementation dedicated to Fabric Loader ecosystem.
 */
public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
