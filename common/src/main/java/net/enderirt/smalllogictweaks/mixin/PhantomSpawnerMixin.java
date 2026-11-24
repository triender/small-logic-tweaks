package net.enderirt.smalllogictweaks.mixin;

import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * VI: Ngăn cơ chế sinh Phantom mặc định của vanilla hoạt động ngoài The End khi tính năng End Phantom được bật.
 * EN: Prevents vanilla PhantomSpawner from spawning Phantoms outside The End when End Phantom is enabled.
 */
@Mixin(PhantomSpawner.class)
public class PhantomSpawnerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(ServerLevel level, boolean spawnEnemies, CallbackInfo ci) {
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_END_PHANTOM && level.dimension() != Level.END) {
            ci.cancel();
        }
    }
}
