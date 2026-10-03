package net.enderirt.smalllogictweaks;

import net.enderirt.smalllogictweaks.network.ClientConfigSync;
import net.enderirt.smalllogictweaks.network.ConfigSyncPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * VI: Entrypoint phía client của NeoForge (tương đương SmallLogicTweaksClient bên Fabric).
 * EN: NeoForge client-side entrypoint (counterpart of Fabric's SmallLogicTweaksClient).
 */
@Mod(value = SmallLogicTweaksCommon.MOD_ID, dist = Dist.CLIENT)
public class SmallLogicTweaksNeoForgeClient {

    public SmallLogicTweaksNeoForgeClient(IEventBus modBus) {
        // Lắng nghe và ghi đè cấu hình vào ACTIVE_INSTANCE (logic kiểm tra biên nằm trong common)
        modBus.addListener(SmallLogicTweaksNeoForgeClient::onRegisterClientPayloadHandlers);

        // Khôi phục cấu hình cục bộ khi ngắt kết nối
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientConfigSync.restoreLocal());

        SmallLogicTweaksCommon.LOGGER.info("Success load environment for client...");
    }

    private static void onRegisterClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ConfigSyncPayload.ID, (payload, context) ->
                context.enqueueWork(() -> ClientConfigSync.apply(payload.jsonConfig(), message -> {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(message);
                    }
                })));
    }
}
