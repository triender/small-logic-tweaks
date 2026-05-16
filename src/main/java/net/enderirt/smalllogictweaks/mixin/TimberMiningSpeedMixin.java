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
        float originalProgress = cir.getReturnValue();
        if (originalProgress <= 0.0F) return;

        BlockState state = (BlockState) (Object) this;

        // Bỏ qua can thiệp thuật toán nếu không phải khối gỗ hoặc người chơi đang dùng chế độ đào chính xác (Shift)
        if (!state.is(net.minecraft.tags.BlockTags.LOGS) || player.isShiftKeyDown()) {
            return;
        }

        float structuralResistance = SmallLogicTweaksEvents.getTimberSpeedFactor(player, pos);

        if (structuralResistance > 1.0F) {
            // Cập nhật tiến trình theo thời gian thực (Delta) dựa trên sức cản cấu trúc
            // Việc tính toán động tại mỗi tick giúp duy trì tính đồng bộ mạng tối đa
            cir.setReturnValue(originalProgress / structuralResistance);
        }
    }
}