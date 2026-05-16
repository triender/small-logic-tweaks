package net.enderirt.smalllogictweaks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record TweaksConfigCondition(String key) implements ResourceCondition {
    private static final Logger LOGGER = LoggerFactory.getLogger("SmallLogicTweaks/Condition");
    public static final MapCodec<TweaksConfigCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("key").forGetter(TweaksConfigCondition::key)
            ).apply(instance, TweaksConfigCondition::new)
    );

    public static final ResourceConditionType<TweaksConfigCondition> TYPE =
            ResourceConditionType.create(Identifier.fromNamespaceAndPath("small_logic_tweaks", "config_flag"), CODEC);

    public static void initialize() {
        if (SmallLogicTweaksConfig.INSTANCE.ENABLE_DEBUG_LOGS) {
            LOGGER.info("Registering Resource Conditions for JSON files...");
        }
        ResourceConditions.register(TYPE);
    }

    @Override
    public ResourceConditionType<?> getType() {
        return TYPE;
    }

    // Tùy thuộc vào phiên bản Fabric, tham số ở đây có thể là RegistryInfoLookup hoặc HolderLookup.Provider
    @Override
    public boolean test(RegistryOps.RegistryInfoLookup registryLookup) {
        if ("charcoal_dye".equals(this.key)) return SmallLogicTweaksConfig.INSTANCE.ENABLE_CHARCOAL_TO_BLACK_DYE;
        if ("jungle_leaves".equals(this.key)) return SmallLogicTweaksConfig.INSTANCE.ENABLE_JUNGLE_SUSTAINABILITY;
        return false;
    }
}