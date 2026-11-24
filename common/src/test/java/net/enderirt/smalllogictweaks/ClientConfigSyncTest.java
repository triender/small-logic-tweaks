package net.enderirt.smalllogictweaks;

import net.enderirt.smalllogictweaks.network.ClientConfigSync;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ClientConfigSyncTest {

    @Test
    public void testRemoteVanillaServerDisablesClientPredictionUntilSynced() {
        ClientConfigSync.restoreLocal();
        Assertions.assertTrue(ClientConfigSync.isClientPredictionEnabled(),
                "Client prediction should be enabled by default locally.");

        ClientConfigSync.onJoinRemoteServer();
        Assertions.assertFalse(ClientConfigSync.isClientPredictionEnabled(),
                "Client prediction must be disabled on a remote server before ConfigSyncPayload arrives.");

        String serverConfigJson = SmallLogicTweaksConfig.GSON.toJson(new SmallLogicTweaksConfig());
        ClientConfigSync.apply(serverConfigJson, msg -> {});
        Assertions.assertTrue(ClientConfigSync.isClientPredictionEnabled(),
                "Client prediction must be re-enabled once server config sync succeeds.");

        ClientConfigSync.onJoinRemoteServer();
        Assertions.assertFalse(ClientConfigSync.isClientPredictionEnabled());
        ClientConfigSync.restoreLocal();
        Assertions.assertTrue(ClientConfigSync.isClientPredictionEnabled(),
                "Disconnecting must restore client prediction for local/singleplayer sessions.");
        Assertions.assertSame(SmallLogicTweaksConfig.LOCAL_INSTANCE, SmallLogicTweaksConfig.ACTIVE_INSTANCE);
    }

    @Test
    public void testApplyHandlesOutOfBoundsNullAndMalformedPayloads() {
        ClientConfigSync.restoreLocal();
        try {
            // 1. Cấu hình vượt biên từ server -> bật thông báo chat cho người chơi và tắt tính năng cục bộ
            SmallLogicTweaksConfig tampered = new SmallLogicTweaksConfig();
            tampered.MAX_LOG_HORIZONTAL_RADIUS = 999;
            String tamperedJson = SmallLogicTweaksConfig.GSON.toJson(tampered);

            java.util.List<net.minecraft.network.chat.Component> notifications = new java.util.ArrayList<>();
            ClientConfigSync.apply(tamperedJson, notifications::add);
            Assertions.assertEquals(1, notifications.size(),
                    "Out-of-bounds server config must notify player.");
            Assertions.assertFalse(SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_TIMBER_TWEAK,
                    "Out-of-bounds Timber parameter from server must disable Timber locally.");

            // 2. JSON là "null" -> không crash, giữ nguyên ACTIVE_INSTANCE
            var beforeNull = SmallLogicTweaksConfig.ACTIVE_INSTANCE;
            ClientConfigSync.apply("null", notifications::add);
            Assertions.assertSame(beforeNull, SmallLogicTweaksConfig.ACTIVE_INSTANCE);

            // 3. JSON lỗi cú pháp -> bắt ngoại lệ an toàn và ghi log lỗi
            ClientConfigSync.apply("{malformed json", notifications::add);
            Assertions.assertSame(beforeNull, SmallLogicTweaksConfig.ACTIVE_INSTANCE);
        } finally {
            ClientConfigSync.restoreLocal();
        }
    }
}
