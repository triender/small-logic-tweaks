package net.enderirt.smalllogictweaks.util;

import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * [VI] Cung cấp component ủ phân chuẩn vanilla cho Khoai tây độc (xác suất 65% tăng 1 tầng,
 *      khớp với Khoai tây thường trong Minecraft 26.3 thay vì cộng 65 tầng ngay lập tức).
 * [EN] Provides the vanilla-equivalent compostable component for Poisonous Potato (65% chance to
 *      add 1 layer, matching regular Potatoes in Minecraft 26.3 instead of adding 65 layers at once).
 */
public final class CompostHelper {
    public static final Compostable POISONOUS_POTATO_COMPOSTABLE =
            new Compostable(ContextIntProviders.COMPOSTABLE_MEDIUM);

    private CompostHelper() {}
}
