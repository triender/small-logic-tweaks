package net.enderirt.smalllogictweaks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ResourceConditionTest {

    @Test
    public void testRecipeLoadingConditions() {
        // 1. Kiểm thử điều kiện cho công thức nhuộm từ than đá (Coal Dye)

        // Trường hợp 1: Tắt config -> Điều kiện nạp phải trả về false
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_COAL_TO_BLACK_DYE = false;
        Assertions.assertFalse(ConfigFlags.isEnabled("coal_dye"),
                "Resource condition failure: 'coal_dye' condition should return false when configuration is disabled.");

        // Trường hợp 2: Bật config -> Điều kiện nạp phải trả về true
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_COAL_TO_BLACK_DYE = true;
        Assertions.assertTrue(ConfigFlags.isEnabled("coal_dye"),
                "Resource condition failure: 'coal_dye' condition should return true when configuration is enabled.");


        // 2. Kiểm thử điều kiện cho công thức nhuộm từ than củi (Charcoal Dye)

        // Trường hợp 1: Tắt config -> Điều kiện nạp phải trả về false
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_CHARCOAL_TO_BLACK_DYE = false;
        Assertions.assertFalse(ConfigFlags.isEnabled("charcoal_dye"),
                "Resource condition failure: 'charcoal_dye' condition should return false when configuration is disabled.");

        // Trường hợp 2: Bật config -> Điều kiện nạp phải trả về true
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_CHARCOAL_TO_BLACK_DYE = true;
        Assertions.assertTrue(ConfigFlags.isEnabled("charcoal_dye"),
                "Resource condition failure: 'charcoal_dye' condition should return true when configuration is enabled.");
    }

    @Test
    public void testLootTableLoadingConditions() {

        // Trường hợp 1: Tắt config -> Điều kiện nạp phải trả về false
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_JUNGLE_SUSTAINABILITY = false;
        Assertions.assertFalse(ConfigFlags.isEnabled("jungle_leaves"),
                "Resource condition failure: 'jungle_leaves' condition should return false when configuration is disabled.");

        // Trường hợp 2: Bật config -> Điều kiện nạp phải trả về true
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_JUNGLE_SUSTAINABILITY = true;
        Assertions.assertTrue(ConfigFlags.isEnabled("jungle_leaves"),
                "Resource condition failure: 'jungle_leaves' condition should return true when configuration is enabled.");
    }

    @Test
    public void testUnknownKeyIsDisabled() {
        Assertions.assertFalse(ConfigFlags.isEnabled("unknown_flag"),
                "Resource condition failure: unknown keys must never enable a resource.");
    }
}
