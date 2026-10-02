package net.enderirt.smalllogictweaks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FailsafeLogicTest {

    /**
     * Kiểm tra trường hợp cấu hình Timber bị thao túng bằng giá trị vượt biên (Out of bounds).
     */
    @Test
    public void testTimberFailsafeTriggered() {
        // 1. Giả lập cấu hình thô nhận từ mạng chứa thông số độc hại
        SmallLogicTweaksConfig rawReceived = new SmallLogicTweaksConfig();
        rawReceived.ENABLE_TIMBER_TWEAK = true;
        rawReceived.MAX_LOG_HORIZONTAL_RADIUS = 9999; // Vượt quá giới hạn cho phép (1-8)

        // 2. Tạo bản sao an toàn ban đầu mang cùng dữ liệu thô
        SmallLogicTweaksConfig safeConfig = new SmallLogicTweaksConfig();
        safeConfig.ENABLE_TIMBER_TWEAK = true;
        safeConfig.MAX_LOG_HORIZONTAL_RADIUS = 9999;

        // 3. Thực thi Failsafe
        boolean isTampered = safeConfig.fallbackFailsafe(rawReceived);

        // 4. Khẳng định (Assertions)
        Assertions.assertTrue(isTampered, "Failsafe phải phát hiện và trả về true (isTampered).");
        // Lỗi biên phải được validate() sửa về mặc định là 5. Do 5 khác 9999, failsafe phải tắt tính năng.
        Assertions.assertFalse(safeConfig.ENABLE_TIMBER_TWEAK,
                "Lỗi Failsafe: Tính năng Timber phải bị tắt (false) khi phát hiện thông số bán kính bị thao túng.");

        Assertions.assertEquals(5, safeConfig.MAX_LOG_HORIZONTAL_RADIUS,
                "Lỗi tự phục hồi: Thông số bán kính không được trả về mức an toàn mặc định (5).");
    }

    /**
     * Kiểm tra trường hợp cấu hình Phantom bị thao túng bằng giá trị âm (Negative injection).
     */
    @Test
    public void testPhantomFailsafeTriggered() {
        // 1. Giả lập cấu hình thô chứa thông số âm
        SmallLogicTweaksConfig rawReceived = new SmallLogicTweaksConfig();
        rawReceived.ENABLE_END_PHANTOM = true;
        rawReceived.PHANTOM_MOB_CAP = -15; // Giới hạn quái vật không thể là số âm

        // 2. Tạo bản sao an toàn
        SmallLogicTweaksConfig safeConfig = new SmallLogicTweaksConfig();
        safeConfig.ENABLE_END_PHANTOM = true;
        safeConfig.PHANTOM_MOB_CAP = -15;

        // 3. Thực thi Failsafe
        boolean isTampered = safeConfig.fallbackFailsafe(rawReceived);

        // 4. Khẳng định (Assertions)
        Assertions.assertTrue(isTampered, "Failsafe phải phát hiện và trả về true (isTampered).");
        // Mob cap âm sẽ được sửa về mặc định là 8. Do 8 khác -15, failsafe phải tắt tính năng.
        Assertions.assertFalse(safeConfig.ENABLE_END_PHANTOM,
                "Lỗi Failsafe: Tính năng Phantom phải bị tắt (false) khi giới hạn sinh quái bị cấu hình sai.");

        Assertions.assertEquals(8, safeConfig.PHANTOM_MOB_CAP,
                "Lỗi tự phục hồi: Thông số Mob Cap không được trả về mức an toàn mặc định (8).");
    }

    /**
     * Kiểm tra trường hợp cấu hình hợp lệ, đảm bảo Failsafe không vô tình tắt nhầm tính năng.
     */
    @Test
    public void testFailsafePassesOnValidConfig() {
        // 1. Giả lập cấu hình hoàn toàn hợp lệ và an toàn
        SmallLogicTweaksConfig rawReceived = new SmallLogicTweaksConfig();
        rawReceived.ENABLE_TIMBER_TWEAK = true;
        rawReceived.MAX_LOG_HORIZONTAL_RADIUS = 4; // Hợp lệ (1-8)
        rawReceived.ENABLE_END_PHANTOM = true;
        rawReceived.PHANTOM_MOB_CAP = 10; // Hợp lệ (1-100)

        // 2. Tạo bản sao an toàn
        SmallLogicTweaksConfig safeConfig = new SmallLogicTweaksConfig();
        safeConfig.ENABLE_TIMBER_TWEAK = true;
        safeConfig.MAX_LOG_HORIZONTAL_RADIUS = 4;
        safeConfig.ENABLE_END_PHANTOM = true;
        safeConfig.PHANTOM_MOB_CAP = 10;

        // 3. Thực thi Failsafe
        boolean isTampered = safeConfig.fallbackFailsafe(rawReceived);

        // 4. Khẳng định (Assertions)
        Assertions.assertFalse(isTampered, "Failsafe không được báo isTampered trên cấu hình hợp lệ.");
        Assertions.assertTrue(safeConfig.ENABLE_TIMBER_TWEAK,
                "Lỗi False Positive: Tính năng Timber vô tình bị vô hiệu hóa dù dữ liệu hoàn toàn hợp lệ.");

        Assertions.assertTrue(safeConfig.ENABLE_END_PHANTOM,
                "Lỗi False Positive: Tính năng Phantom vô tình bị vô hiệu hóa dù dữ liệu hoàn toàn hợp lệ.");
    }

    /**
     * Kiểm tra trường hợp Server chủ động tắt tính năng (ENABLE = false), Failsafe không được coi là bị can thiệp trái phép.
     */
    @Test
    public void testFailsafeLegitimatelyDisabledFeature() {
        SmallLogicTweaksConfig rawReceived = new SmallLogicTweaksConfig();
        rawReceived.ENABLE_TIMBER_TWEAK = false;
        rawReceived.ENABLE_END_PHANTOM = false;

        SmallLogicTweaksConfig safeConfig = new SmallLogicTweaksConfig();
        safeConfig.ENABLE_TIMBER_TWEAK = false;
        safeConfig.ENABLE_END_PHANTOM = false;

        boolean isTampered = safeConfig.fallbackFailsafe(rawReceived);

        Assertions.assertFalse(isTampered, "Server chủ động tắt tính năng không được coi là tampered.");
        Assertions.assertFalse(safeConfig.ENABLE_TIMBER_TWEAK);
        Assertions.assertFalse(safeConfig.ENABLE_END_PHANTOM);
    }

    /**
     * VI: Kiểm tra cấu trúc khóa cache TimberSpeedKey đảm bảo cô lập hoàn toàn giữa 2 người chơi khác nhau
     * hoặc các công cụ có cấp độ phù phép khác nhau trên cùng một tọa độ khối.
     * EN: Tests that TimberSpeedKey ensures strict cache isolation between different players
     * or tools with different enchantment levels on the exact same block position.
     */
    @Test
    public void testTimberSpeedKeyIsolation() {
        java.util.UUID playerA = java.util.UUID.randomUUID();
        java.util.UUID playerB = java.util.UUID.randomUUID();
        net.minecraft.core.BlockPos targetPos = new net.minecraft.core.BlockPos(100, 64, 200);

        SmallLogicTweaksEvents.TimberSpeedKey keyPlayerA_Timber3 = new SmallLogicTweaksEvents.TimberSpeedKey(
                "minecraft:overworld",
                targetPos,
                playerA,
                3,
                false
        );

        SmallLogicTweaksEvents.TimberSpeedKey keyPlayerB_NoTimber = new SmallLogicTweaksEvents.TimberSpeedKey(
                "minecraft:overworld",
                targetPos,
                playerB,
                0,
                false
        );

        SmallLogicTweaksEvents.TimberSpeedKey keyPlayerA_Sneaking = new SmallLogicTweaksEvents.TimberSpeedKey(
                "minecraft:overworld",
                targetPos,
                playerA,
                3,
                true
        );

        SmallLogicTweaksEvents.TimberSpeedKey keyPlayerA_Nether = new SmallLogicTweaksEvents.TimberSpeedKey(
                "minecraft:the_nether",
                targetPos,
                playerA,
                3,
                false
        );

        // [VI] Hai người chơi khác nhau phải có 2 key hoàn toàn khác nhau (không được va chạm cache)
        // [EN] Different players must produce completely distinct keys (no cache collision)
        Assertions.assertNotEquals(keyPlayerA_Timber3, keyPlayerB_NoTimber,
                "Cache isolation failure: Player A and Player B produced identical cache keys on the same block.");

        // [VI] Cùng người chơi nhưng đang cúi người (Shift) phải có key khác
        // [EN] Same player while sneaking must produce a distinct key
        Assertions.assertNotEquals(keyPlayerA_Timber3, keyPlayerA_Sneaking,
                "Cache isolation failure: Sneaking state did not alter cache key.");

        // [VI] Cùng người chơi, cùng tọa độ nhưng khác chiều không gian (Dimension) phải có key khác
        // [EN] Same player and position across different dimensions must produce distinct keys
        Assertions.assertNotEquals(keyPlayerA_Timber3, keyPlayerA_Nether,
                "Cache isolation failure: Different dimensions produced identical cache keys.");
    }
}