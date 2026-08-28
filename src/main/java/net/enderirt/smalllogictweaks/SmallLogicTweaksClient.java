package net.enderirt.smalllogictweaks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
                        // Tạo một bản sao độc lập (Deep Copy hoặc tạo mới bằng GSON) để làm mốc đối chiếu
                        SmallLogicTweaksConfig safeConfig = SmallLogicTweaksConfig.GSON.fromJson(payload.jsonConfig(), SmallLogicTweaksConfig.class);

                        // Gọi hàm dự phòng để tự động vô hiệu hóa các tính năng có nguy cơ tấn công
                        boolean isTampered = safeConfig.fallbackFailsafe(rawServerConfig);

                        // Nạp vào RAM cấu hình đã an toàn
                        SmallLogicTweaksConfig.ACTIVE_INSTANCE = safeConfig;

                        // Nếu phát hiện cấu hình bị can thiệp/vượt biên, thông báo cho người chơi
                        if (isTampered) {
                            if (context.client().player != null) {
                                context.client().player.sendSystemMessage(
                                        net.minecraft.network.chat.Component.literal("§e[Small Logic Tweaks] Server configuration is invalid. Some tweaks are disabled for your safety.")
                                );
                            }
                        } else {
                            SmallLogicTweaks.LOGGER.info("Config synced safely from Server.");
                        }
                    }
                } catch (Exception e) {
                    SmallLogicTweaks.LOGGER.error("Failed to parse Server config", e);
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