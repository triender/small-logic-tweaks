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

    @Test
    public void testPickaxeDirtReversionDefaultValue() {
        // Xác minh giá trị mặc định là true (bật theo mặc định)
        SmallLogicTweaksConfig config = new SmallLogicTweaksConfig();
        Assertions.assertTrue(config.ENABLE_PICKAXE_DIRT_REVERSION,
                "ENABLE_PICKAXE_DIRT_REVERSION phải bật theo mặc định (true)");
    }

    @Test
    public void testPickaxeDirtReversionCommentRestoredByValidate() {
        SmallLogicTweaksConfig config = new SmallLogicTweaksConfig();
        // Cố ý xóa comment (mô phỏng file JSON bị hỏng hoặc thiếu trường comment)
        config._comment_ENABLE_PICKAXE_DIRT_REVERSION = null;

        // validate() phải khôi phục lại comment đúng chuẩn
        config.validate();

        Assertions.assertNotNull(config._comment_ENABLE_PICKAXE_DIRT_REVERSION,
                "validate() phải tự phục hồi comment của ENABLE_PICKAXE_DIRT_REVERSION");
        Assertions.assertTrue(config._comment_ENABLE_PICKAXE_DIRT_REVERSION.contains("Pickaxe"),
                "Comment phải đề cập đến Pickaxe để mô tả đúng tính năng");
    }

    @Test
    public void testAnvilAndWeightlessMobsDefaultValue() {
        SmallLogicTweaksConfig config = new SmallLogicTweaksConfig();
        Assertions.assertTrue(config.ENABLE_ANVIL_PRESERVED,
                "ENABLE_ANVIL_PRESERVED phải bật theo mặc định (true)");
        Assertions.assertTrue(config.ENABLE_WEIGHTLESS_MOBS,
                "ENABLE_WEIGHTLESS_MOBS phải bật theo mặc định (true)");
    }

    @Test
    public void testAnvilAndWeightlessMobsCommentsRestoredByValidate() {
        SmallLogicTweaksConfig config = new SmallLogicTweaksConfig();
        config._comment_ENABLE_ANVIL_PRESERVED = null;
        config._comment_ENABLE_WEIGHTLESS_MOBS = null;

        config.validate();

        Assertions.assertNotNull(config._comment_ENABLE_ANVIL_PRESERVED,
                "validate() phải tự phục hồi comment của ENABLE_ANVIL_PRESERVED");
        Assertions.assertTrue(config._comment_ENABLE_ANVIL_PRESERVED.contains("anvil") || config._comment_ENABLE_ANVIL_PRESERVED.contains("Anvil"),
                "Comment phải mô tả đúng tính năng Anvil");

        Assertions.assertNotNull(config._comment_ENABLE_WEIGHTLESS_MOBS,
                "validate() phải tự phục hồi comment của ENABLE_WEIGHTLESS_MOBS");
        Assertions.assertTrue(config._comment_ENABLE_WEIGHTLESS_MOBS.contains("turtle eggs"),
                "Comment phải mô tả đúng tính năng Turtle Eggs");
    }
}