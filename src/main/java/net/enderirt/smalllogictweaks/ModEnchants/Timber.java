package net.enderirt.smalllogictweaks.ModEnchants;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.enderirt.smalllogictweaks.SmallLogicTweaks.MOD_ID;

public class Timber {
    public static final ResourceKey<Enchantment> TIMBER =
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MOD_ID, "timber"));;

    public static void initialize(){
        
    }
}
