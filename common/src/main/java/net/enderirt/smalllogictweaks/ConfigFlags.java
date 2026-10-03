package net.enderirt.smalllogictweaks;

import net.minecraft.resources.Identifier;

/**
 * VI: Ánh xạ khóa của điều kiện nạp JSON {@code small_logic_tweaks:config_flag} sang cờ cấu hình.
 *     Adapter điều kiện của từng loader (Fabric ResourceCondition, NeoForge ICondition) chỉ gọi lại lớp này.
 * EN: Maps keys of the {@code small_logic_tweaks:config_flag} JSON load condition to config flags.
 *     Each loader's condition adapter (Fabric ResourceCondition, NeoForge ICondition) only delegates here.
 */
public final class ConfigFlags {
    public static final Identifier CONDITION_ID = Identifier.fromNamespaceAndPath(SmallLogicTweaksCommon.MOD_ID, "config_flag");

    private ConfigFlags() {}

    public static boolean isEnabled(String key) {
        return switch (key) {
            case "charcoal_dye" -> SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_CHARCOAL_TO_BLACK_DYE;
            case "coal_dye" -> SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_COAL_TO_BLACK_DYE;
            case "jungle_leaves" -> SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_JUNGLE_SUSTAINABILITY;
            default -> false;
        };
    }
}
