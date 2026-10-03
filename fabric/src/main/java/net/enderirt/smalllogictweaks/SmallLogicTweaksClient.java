package net.enderirt.smalllogictweaks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.enderirt.smalllogictweaks.network.ClientConfigSync;
import net.enderirt.smalllogictweaks.network.ConfigSyncPayload;

public class SmallLogicTweaksClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Lắng nghe và ghi đè cấu hình vào ACTIVE_INSTANCE (logic kiểm tra biên nằm trong common)
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientConfigSync.apply(payload.jsonConfig(), message -> {
                    if (context.client().player != null) {
                        context.client().player.sendSystemMessage(message);
                    }
                })));

        // Khôi phục cấu hình cục bộ khi ngắt kết nối
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientConfigSync.restoreLocal());

        SmallLogicTweaks.LOGGER.info("Success load environment for client...");
    }
}
