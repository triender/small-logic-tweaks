package net.enderirt.smalllogictweaks;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * [VI] Quản lý các khóa thẻ (TagKey) tùy biến của Small Logic Tweaks.
 * [EN] Manages custom TagKeys for Small Logic Tweaks.
 */
public final class ModTags {
    private ModTags() {}

    /**
     * [VI] Thẻ định danh các sinh vật không thể dẫm vỡ trứng rùa (MC-248332).
     * [EN] Tag identifying entities that cannot break turtle eggs (MC-248332).
     */
    public static final TagKey<EntityType<?>> CANNOT_BREAK_TURTLE_EGGS =
            TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(SmallLogicTweaks.MOD_ID, "cannot_break_turtle_eggs"));
}
