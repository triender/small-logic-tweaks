package net.enderirt.smalllogictweaks.mixin;

import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(
            method = "awardStat(Lnet/minecraft/stats/Stat;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAwardStat(Stat<?> stat, int count, CallbackInfo ci) {
        // Lọc đúng chỉ số TIME_SINCE_REST (Thời gian mất ngủ)
        if (SmallLogicTweaksConfig.INSTANCE.ENABLE_END_PHANTOM && stat.equals(Stats.CUSTOM.get(Stats.TIME_SINCE_REST))) {
            ServerPlayer player = (ServerPlayer) (Object) this;

            // Nếu người chơi KHÔNG ở thế giới The End, chặn hoàn toàn việc tăng bộ đếm
            if (player.level().dimension() != Level.END) {
                ci.cancel();
            }
        }
    }
}