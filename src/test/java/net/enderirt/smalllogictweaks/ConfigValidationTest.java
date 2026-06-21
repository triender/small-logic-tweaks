package net.enderirt.smalllogictweaks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigValidationTest {

    @Test
    public void testBoundaryCorrection() {
        // Tạo một đối tượng cấu hình mô phỏng
        SmallLogicTweaksConfig testConfig = new SmallLogicTweaksConfig();

        // Cố tình gán giá trị sai logic (số âm, vượt giới hạn)
        testConfig.MIN_LEAVES_FOR_TREE = -5;
        testConfig.MAX_LOG_HORIZONTAL_RADIUS = 999;

        // Chạy hàm xác thực (Bạn cần đảm bảo hàm validate() trong class gốc là public)
        testConfig.validate();

        // Khẳng định (Assert) rằng hệ thống đã tự động sửa lỗi về giá trị an toàn
        Assertions.assertEquals(4, testConfig.MIN_LEAVES_FOR_TREE,
                "Hệ thống phải sửa số lượng lá tối thiểu về 4 khi nhập số âm");

        Assertions.assertEquals(5, testConfig.MAX_LOG_HORIZONTAL_RADIUS,
                "Hệ thống phải giới hạn bán kính quét gỗ tối đa là 5");
    }

}