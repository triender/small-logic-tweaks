package net.enderirt.smalllogictweaks.mixin;

import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.enderirt.smalllogictweaks.SmallLogicTweaksEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractThrownPotion.class)
public abstract class ThrownPotionMixin {

    @Inject(method = "onHit", at = @At("RETURN"))
    private void applySplashHardeningLogic(HitResult result, CallbackInfo ci) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_SPLASH_HARDENING) return;

        AbstractThrownPotion potion = (AbstractThrownPotion) (Object) this;
        Level world = potion.level();

        if (world.isClientSide()) return;

        var potionContents = potion.getItem().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);

        if (potionContents != null && potionContents.is(Potions.WATER)) {
            // 1. Lấy tâm không khí (Splash Center)
            BlockPos splashCenter = BlockPos.containing(result.getLocation());
            BlockPos directHitPos = null;

            // Xác định hộp giới hạn quét ban đầu là 3x3x3 xung quanh tâm không khí
            int minX = splashCenter.getX() - 1;
            int minY = splashCenter.getY() - 1;
            int minZ = splashCenter.getZ() - 1;
            int maxX = splashCenter.getX() + 1;
            int maxY = splashCenter.getY() + 1;
            int maxZ = splashCenter.getZ() + 1;

            // 2. Nếu va chạm với khối, thực hiện thu hẹp hộp giới hạn bằng phép GIAO (Intersection)
            if (result.getType() == HitResult.Type.BLOCK) {
                directHitPos = ((BlockHitResult) result).getBlockPos();

                // Toán học Giao tập hợp (Intersection of AABBs)
                minX = Math.max(minX, directHitPos.getX() - 1);
                minY = Math.max(minY, directHitPos.getY() - 1);
                minZ = Math.max(minZ, directHitPos.getZ() - 1);
                maxX = Math.min(maxX, directHitPos.getX() + 1);
                maxY = Math.min(maxY, directHitPos.getY() + 1);
                maxZ = Math.min(maxZ, directHitPos.getZ() + 1);

                // Ưu tiên hóa cứng 100% khối bị ném trúng trực tiếp
                SmallLogicTweaksEvents.tryHardenConcrete(world, directHitPos);
            }

            float spreadChance = 0.4f;
            var random = world.getRandom();

            // 3. Quét chính xác vùng không gian đã bị thu hẹp (Chỉ 18 khối nếu có va chạm tường)
            for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {

                // Bỏ qua khối tâm điểm đã xử lý, tiến hành roll xác suất cho các khối còn lại
                if (directHitPos == null || !pos.equals(directHitPos)) {
                    if (random.nextFloat() < spreadChance) {
                        SmallLogicTweaksEvents.tryHardenConcrete(world, pos);
                    }
                }
            }
        }
    }
}
