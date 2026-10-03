package net.enderirt.smalllogictweaks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * VI: Điều kiện nạp JSON {@code small_logic_tweaks:config_flag} cho NeoForge (khóa {@code neoforge:conditions}).
 *     Logic nằm trong {@link ConfigFlags}, dùng chung với bản Fabric.
 * EN: NeoForge version of the {@code small_logic_tweaks:config_flag} JSON load condition ({@code neoforge:conditions} key).
 *     The logic lives in {@link ConfigFlags}, shared with the Fabric build.
 */
public record NeoForgeConfigCondition(String key) implements ICondition {
    public static final MapCodec<NeoForgeConfigCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("key").forGetter(NeoForgeConfigCondition::key)
            ).apply(instance, NeoForgeConfigCondition::new)
    );

    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, SmallLogicTweaksCommon.MOD_ID);

    static {
        CONDITION_CODECS.register(ConfigFlags.CONDITION_ID.getPath(), () -> CODEC);
    }

    public static void register(IEventBus modBus) {
        CONDITION_CODECS.register(modBus);
    }

    @Override
    public boolean test(IContext context) {
        return ConfigFlags.isEnabled(this.key);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
