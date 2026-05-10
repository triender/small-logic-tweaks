package net.enderirt.smalllogictweaks.mixin;

import net.enderirt.smalllogictweaks.SmallLogicTweaksEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class TimberMiningSpeedMixin {

    @Inject(method = "getDestroyProgress", at = @At("RETURN"), cancellable = true)
    private void modifyTimberMiningProgress(Player player, BlockGetter world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        BlockState state = (BlockState) (Object) this;

        if (!state.is(net.minecraft.tags.BlockTags.LOGS) || player.isShiftKeyDown()) return;

        float originalProgress = cir.getReturnValue();
        if (originalProgress <= 0) return;

        // Lấy factor từ Cache (Hàm này giờ đã rất nhanh)
        float nerfFactor = SmallLogicTweaksEvents.getTimberSpeedFactor(player, pos);

        if (nerfFactor > 1.0f) {
            float newProgress = originalProgress / nerfFactor;

            float vanillaSeconds = (float) Math.ceil(1.0f / originalProgress) / 20.0f;
            float timberSeconds = (float) Math.ceil(1.0f / newProgress) / 20.0f;

            // In ra Console để dễ so sánh (Có thể xóa đi khi phát hành Mod)
//            System.out.printf("[Timber-Time] Blocks: %.1f | Vanilla: %.2fs | Timber: %.2fs%n",
//                    nerfFactor, vanillaSeconds, timberSeconds);

            // Ghi đè giá trị: Vết nứt sẽ chạy chậm lại ngay lập tức trên màn hình
            cir.setReturnValue(originalProgress / nerfFactor);
        }
    }
}