package net.enderirt.smalllogictweaks.network;

import net.enderirt.smalllogictweaks.SmallLogicTweaksCommon;
import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.enderirt.smalllogictweaks.core.error.SltError;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * VI: Logic phía client khi nhận cấu hình từ server, dùng chung cho mọi loader.
 *     Adapter của loader chỉ cần chuyển payload vào luồng client và gọi {@link #apply}.
 * EN: Client-side handling of the server's config, shared by every loader.
 *     A loader adapter only has to hop onto the client thread and call {@link #apply}.
 */
public final class ClientConfigSync {
    private ClientConfigSync() {}

    /**
     * VI: Áp dụng cấu hình server sau khi kiểm tra biên an toàn. Phải gọi trên luồng client.
     * EN: Applies the server config after defensive bounds checks. Must run on the client thread.
     *
     * @param jsonConfig   VI: Cấu hình server dạng JSON / EN: Server config as JSON
     * @param notifyPlayer VI: Gửi thông báo tới người chơi (bỏ qua nếu chưa có player)
     *                     EN: Sends a message to the player (ignore when there is no player yet)
     */
    public static void apply(String jsonConfig, Consumer<Component> notifyPlayer) {
        try {
            SmallLogicTweaksConfig rawServerConfig = SmallLogicTweaksConfig.GSON.fromJson(jsonConfig, SmallLogicTweaksConfig.class);
            if (rawServerConfig != null) {
                // [VI] Tạo bản sao độc lập để thực hiện kiểm tra chuẩn hóa dữ liệu phòng vệ
                // [EN] Create an independent copy to perform defensive validation and bounds checking
                SmallLogicTweaksConfig safeConfig = SmallLogicTweaksConfig.GSON.fromJson(jsonConfig, SmallLogicTweaksConfig.class);

                // [VI] Kích hoạt cơ chế phòng vệ biên để vô hiệu hóa tính năng nếu cấu hình vượt biên an toàn
                // [EN] Trigger defensive bounds check to disable features locally if configuration exceeds safe limits
                boolean hasOutOfBounds = safeConfig.fallbackFailsafe(rawServerConfig);

                // [VI] Nạp cấu hình an toàn đã được chuẩn hóa vào phiên làm việc hiện hành
                // [EN] Load sanitized and validated configuration into active runtime instance
                SmallLogicTweaksConfig.ACTIVE_INSTANCE = safeConfig;

                // [VI] Thông báo cho người chơi nếu cấu hình máy chủ vượt biên an toàn cục bộ
                // [EN] Notify player if server configuration exceeds local safety boundaries
                if (hasOutOfBounds) {
                    notifyPlayer.accept(Component.literal("§e[Small Logic Tweaks] Server configuration is out of safe bounds. Some tweaks are disabled locally."));
                } else {
                    SmallLogicTweaksCommon.LOGGER.info("Config synced safely from Server.");
                }
            }
        } catch (Exception e) {
            SltError.NET_PARSE_FAILED.logError(SmallLogicTweaksCommon.LOGGER, e.getMessage());
        }
    }

    /**
     * VI: Khôi phục cấu hình cục bộ khi ngắt kết nối khỏi server.
     * EN: Restores the local config when disconnecting from a server.
     */
    public static void restoreLocal() {
        SmallLogicTweaksConfig.ACTIVE_INSTANCE = SmallLogicTweaksConfig.LOCAL_INSTANCE;
    }
}
