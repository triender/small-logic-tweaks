package net.enderirt.smalllogictweaks.mixin;

import net.enderirt.smalllogictweaks.ModTags;
import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.TurtleEggBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [VI] Ngăn các thực thể bay/nhẹ cân dẫm vỡ trứng rùa (MC-248332).
 * [EN] Prevents delicate and floating entities from breaking turtle eggs (MC-248332).
 */
@Mixin(TurtleEggBlock.class)
public abstract class TurtleEggBlockMixin {

    @Inject(method = "canDestroyEgg", at = @At("HEAD"), cancellable = true)
    private void slt$preventDelicateMobs(ServerLevel level, BlockPos pos, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_WEIGHTLESS_MOBS) {
            if (entity != null && entity.getType().builtInRegistryHolder().is(ModTags.CANNOT_BREAK_TURTLE_EGGS)) {
                cir.setReturnValue(false);
            }
        }
    }
}
