package net.enderirt.smalllogictweaks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ResourceConditionTest {

    @Test
    public void testRecipeLoadingConditions() {
        // 1. Kiểm thử điều kiện cho công thức nhuộm từ than đá (Coal Dye)
        TweaksConfigCondition coalCondition = new TweaksConfigCondition("coal_dye");

        // Trường hợp 1: Tắt config -> Điều kiện nạp phải trả về false
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_COAL_TO_BLACK_DYE = false;
        Assertions.assertFalse(coalCondition.test(null),
                "Resource condition failure: 'coal_dye' condition should return false when configuration is disabled.");

        // Trường hợp 2: Bật config -> Điều kiện nạp phải trả về true
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_COAL_TO_BLACK_DYE = true;
        Assertions.assertTrue(coalCondition.test(null),
                "Resource condition failure: 'coal_dye' condition should return true when configuration is enabled.");


        // 2. Kiểm thử điều kiện cho công thức nhuộm từ than củi (Charcoal Dye)
        TweaksConfigCondition charcoalCondition = new TweaksConfigCondition("charcoal_dye");

        // Trường hợp 1: Tắt config -> Điều kiện nạp phải trả về false
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_CHARCOAL_TO_BLACK_DYE = false;
        Assertions.assertFalse(charcoalCondition.test(null),
                "Resource condition failure: 'charcoal_dye' condition should return false when configuration is disabled.");

        // Trường hợp 2: Bật config -> Điều kiện nạp phải trả về true
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_CHARCOAL_TO_BLACK_DYE = true;
        Assertions.assertTrue(charcoalCondition.test(null),
                "Resource condition failure: 'charcoal_dye' condition should return true when configuration is enabled.");
    }

    @Test
    public void testLootTableLoadingConditions() {
        TweaksConfigCondition jungleLeavesCondition = new TweaksConfigCondition("jungle_leaves");

        // Trường hợp 1: Tắt config -> Điều kiện nạp phải trả về false
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_JUNGLE_SUSTAINABILITY = false;
        Assertions.assertFalse(jungleLeavesCondition.test(null),
                "Resource condition failure: 'jungle_leaves' condition should return false when configuration is disabled.");

        // Trường hợp 2: Bật config -> Điều kiện nạp phải trả về true
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_JUNGLE_SUSTAINABILITY = true;
        Assertions.assertTrue(jungleLeavesCondition.test(null),
                "Resource condition failure: 'jungle_leaves' condition should return true when configuration is enabled.");
    }
}