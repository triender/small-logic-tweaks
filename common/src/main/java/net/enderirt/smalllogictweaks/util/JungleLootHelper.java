package net.enderirt.smalllogictweaks.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.enderirt.smalllogictweaks.SmallLogicTweaksCommon;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * VI: Tiện ích nạp bảng loot Jungle Leaves nâng cao từ namespace riêng của mod khi tính năng được bật,
 *     tránh ghi đè trực tiếp đường dẫn vanilla gây mất loot khi cờ cấu hình bị tắt.
 * EN: Helper that loads the boosted Jungle Leaves loot table from the mod's own namespace when enabled,
 *     avoiding overwriting the vanilla path which would drop nothing when the config flag is disabled.
 */
public final class JungleLootHelper {
    public static final Identifier JUNGLE_LEAVES_LOOT_ID = Identifier.fromNamespaceAndPath("minecraft", "blocks/jungle_leaves");
    public static final ResourceKey<LootTable> JUNGLE_LEAVES_LOOT_KEY = ResourceKey.create(Registries.LOOT_TABLE, JUNGLE_LEAVES_LOOT_ID);
    private static final String RESOURCE_PATH = "/data/small_logic_tweaks/loot_table/blocks/jungle_leaves.json";

    private JungleLootHelper() {}

    public static LootTable loadJungleLeavesOverride(HolderLookup.Provider registries) {
        try (InputStream stream = JungleLootHelper.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                SmallLogicTweaksCommon.LOGGER.error("[Jungle Sustainability] Missing loot table resource: {}", RESOURCE_PATH);
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement json = JsonParser.parseReader(reader);
                return LootTable.DIRECT_CODEC
                        .parse(registries.createSerializationContext(JsonOps.INSTANCE), json)
                        .resultOrPartial(err -> SmallLogicTweaksCommon.LOGGER.error("[Jungle Sustainability] Failed to decode loot table: {}", err))
                        .orElse(null);
            }
        } catch (Exception e) {
            SmallLogicTweaksCommon.LOGGER.error("[Jungle Sustainability] Error loading jungle leaves loot table override", e);
            return null;
        }
    }
}
