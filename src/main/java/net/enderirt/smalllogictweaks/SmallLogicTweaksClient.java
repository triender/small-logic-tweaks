package net.enderirt.smalllogictweaks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.enderirt.smalllogictweaks.core.error.SltError;
import net.enderirt.smalllogictweaks.network.ConfigSyncPayload;

public class SmallLogicTweaksClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Lắng nghe và ghi đè cấu hình vào ACTIVE_INSTANCE
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    SmallLogicTweaksConfig rawServerConfig = SmallLogicTweaksConfig.GSON.fromJson(payload.jsonConfig(), SmallLogicTweaksConfig.class);
                    if (rawServerConfig != null) {
                        // [VI] Tạo bản sao độc lập để thực hiện kiểm tra chuẩn hóa dữ liệu phòng vệ
                        // [EN] Create an independent copy to perform defensive validation and bounds checking
                        SmallLogicTweaksConfig safeConfig = SmallLogicTweaksConfig.GSON.fromJson(payload.jsonConfig(), SmallLogicTweaksConfig.class);

                        // [VI] Kích hoạt cơ chế phòng vệ biên để vô hiệu hóa tính năng nếu cấu hình vượt biên an toàn
                        // [EN] Trigger defensive bounds check to disable features locally if configuration exceeds safe limits
                        boolean hasOutOfBounds = safeConfig.fallbackFailsafe(rawServerConfig);

                        // [VI] Nạp cấu hình an toàn đã được chuẩn hóa vào phiên làm việc hiện hành
                        // [EN] Load sanitized and validated configuration into active runtime instance
                        SmallLogicTweaksConfig.ACTIVE_INSTANCE = safeConfig;

                        // [VI] Thông báo cho người chơi nếu cấu hình máy chủ vượt biên an toàn cục bộ
                        // [EN] Notify player if server configuration exceeds local safety boundaries
                        if (hasOutOfBounds) {
                            if (context.client().player != null) {
                                context.client().player.sendSystemMessage(
                                        net.minecraft.network.chat.Component.literal("§e[Small Logic Tweaks] Server configuration is out of safe bounds. Some tweaks are disabled locally.")
                                );
                            }
                        } else {
                            SmallLogicTweaks.LOGGER.info("Config synced safely from Server.");
                        }
                    }
                } catch (Exception e) {
                    SltError.NET_PARSE_FAILED.logError(SmallLogicTweaks.LOGGER, e.getMessage());
                }
            });
        });

        // Khôi phục cấu hình cục bộ khi ngắt kết nối
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            SmallLogicTweaksConfig.ACTIVE_INSTANCE = SmallLogicTweaksConfig.LOCAL_INSTANCE;

        });

        SmallLogicTweaks.LOGGER.info("Success load environment for client...");
    }
}