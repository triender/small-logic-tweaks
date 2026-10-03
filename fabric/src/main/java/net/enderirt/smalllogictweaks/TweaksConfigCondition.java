package net.enderirt.smalllogictweaks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.resources.RegistryOps;
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
            ResourceConditionType.create(ConfigFlags.CONDITION_ID, CODEC);

    public static void initialize() {
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_DEBUG_LOGS) {
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
        return ConfigFlags.isEnabled(this.key);
    }
}