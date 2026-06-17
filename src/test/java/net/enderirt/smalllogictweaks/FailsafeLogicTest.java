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
        safeConfig.fallbackFailsafe(rawReceived);

        // 4. Khẳng định (Assertions)
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
        safeConfig.fallbackFailsafe(rawReceived);

        // 4. Khẳng định (Assertions)
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
        safeConfig.fallbackFailsafe(rawReceived);

        // 4. Khẳng định (Assertions)
        // Vì thông số an toàn không thay đổi sau khi chạy validate(), đối chiếu khớp 100%, các tính năng phải giữ nguyên trạng thái bật.
        Assertions.assertTrue(safeConfig.ENABLE_TIMBER_TWEAK,
                "Lỗi False Positive: Tính năng Timber vô tình bị vô hiệu hóa dù dữ liệu hoàn toàn hợp lệ.");

        Assertions.assertTrue(safeConfig.ENABLE_END_PHANTOM,
                "Lỗi False Positive: Tính năng Phantom vô tình bị vô hiệu hóa dù dữ liệu hoàn toàn hợp lệ.");
    }
}