package net.enderirt.smalllogictweaks;

import net.enderirt.smalllogictweaks.core.platform.Services;
import net.enderirt.smalllogictweaks.network.ConfigSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * VI: Entrypoint NeoForge (tương đương SmallLogicTweaks bên Fabric).
 * EN: NeoForge entrypoint (counterpart of Fabric's SmallLogicTweaks).
 */
@Mod(SmallLogicTweaksCommon.MOD_ID)
public class SmallLogicTweaksNeoForge {

    public SmallLogicTweaksNeoForge(IEventBus modBus) {
        // 1. Luôn tải cấu hình đầu tiên
        SmallLogicTweaksCommon.init();

        // 2. Khởi tạo các module dựa trên cấu hình đã tải
        NeoForgeConfigCondition.register(modBus);
        modBus.addListener(SmallLogicTweaksNeoForge::onRegisterPayloads);

        // Gửi cấu hình của server cho người chơi vừa vào
        NeoForge.EVENT_BUS.addListener(SmallLogicTweaksNeoForge::onPlayerLoggedIn);

        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_DEBUG_LOGS) {
            SmallLogicTweaksCommon.LOGGER.info("Small Logic Tweaks Mod initialization completed.");
        }
        NeoForgeEvents.register(modBus);

        // Kiểm tra mixin cho boot test (chỉ chạy khi có -Dslt.mixinAudit=true)
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> SmallLogicTweaksCommon.runMixinAuditIfRequested());
    }

    // Optional: clients without the mod (or vanilla clients) can still join, like on Fabric.
    // The client-side handler is registered in SmallLogicTweaksNeoForgeClient.
    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(SmallLogicTweaksCommon.MOD_ID)
                .optional()
                .playToClient(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Services.NETWORK.sendToPlayer(player, ConfigSyncPayload.fromLocalConfig());
        }
    }
}
