package net.enderirt.smalllogictweaks;

import net.enderirt.smalllogictweaks.handler.BoneMealHandler;
import net.enderirt.smalllogictweaks.handler.ConcreteHardeningHandler;
import net.enderirt.smalllogictweaks.handler.DirtReversionHandler;
import net.enderirt.smalllogictweaks.handler.EndPhantomHandler;
import net.enderirt.smalllogictweaks.handler.TimberHandler;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * VI: Bo dieu hop su kien Fabric (Fabric Event Adapter) ket noi Fabric Lifecycle vao cac Handler doc lap.
 * EN: Fabric event adapter connecting Fabric lifecycle callbacks to standalone platform-neutral handlers.
 */
public final class SmallLogicTweaksEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger("small_logic_tweaks");

    // Tuong thich nguoc cho cac module va test hien tai
    public static final Map<Block, Block> POWDER_TO_CONCRETE = ConcreteHardeningHandler.POWDER_TO_CONCRETE;
    public static final TagKey<Block> PICKAXE_REVERSION_BLOCKS = DirtReversionHandler.PICKAXE_REVERSION_BLOCKS;

    public record TimberResult(boolean shouldChop, List<BlockPos> logs, List<BlockPos> leaves, int level) {}

    public record TimberSpeedKey(
            Object dimension,
            BlockPos pos,
            UUID playerId,
            int timberLevel,
            boolean isShiftKeyDown
    ) {}

    private SmallLogicTweaksEvents() {}

    /**
     * VI: Dang ky toan bo su kien Fabric vao cac Handler logic tuong ung.
     * EN: Registers all Fabric event callbacks and delegates them to core handlers.
     */
    public static void register() {
        registerBoneMealTweak();
        registerTimberTweak();
        registerPotatoTweaks();
        registerHydroHardeningTweak();
        registerPickaxeDirtReversionTweak();
        registerEndPhantomTweak();
        LOGGER.info("Small logic Tweaks Mod register success!!");
    }

    private static void registerBoneMealTweak() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
                BoneMealHandler.handleBoneMeal(world, player, hand, hitResult));
    }

    private static void registerTimberTweak() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) ->
                TimberHandler.handleTimberChop(world, player, pos, state));
    }

    private static void registerPotatoTweaks() {
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_POISONOUS_POTATO_COMPOST) {
            DefaultItemComponentEvents.MODIFY.register(context -> {
                context.modify(Items.POISONOUS_POTATO, builder -> {
                    builder.set(DataComponents.COMPOSTABLE, new Compostable(new ResolvableInt.Constant(65)));
                });
            });
            if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_DEBUG_LOGS) {
                LOGGER.info("[Potato Tweak] Registered Poisonous Potato to Composter via DefaultItemComponentEvents.");
            }
        }
    }

    private static void registerHydroHardeningTweak() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
                ConcreteHardeningHandler.handleWaterBottle(world, player, hand, hitResult));
    }

    private static void registerPickaxeDirtReversionTweak() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
                DirtReversionHandler.handlePickaxeReversion(world, player, hand, hitResult));
    }

    private static void registerEndPhantomTweak() {
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
            if (origin.dimension() == Level.END) {
                EndPhantomHandler.resetInsomnia(player);
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (oldPlayer.level().dimension() == Level.END) {
                EndPhantomHandler.resetInsomnia(newPlayer);
            }
        });

        ServerTickEvents.END_LEVEL_TICK.register(EndPhantomHandler::handleEndTick);
    }

    // --- FACADE METHODS DELEGATE SANG CORE HANDLERS ---

    public static boolean tryHardenConcrete(Level world, BlockPos pos) {
        return ConcreteHardeningHandler.tryHardenConcrete(world, pos);
    }

    public static float getTimberSpeedFactor(Player player, BlockPos pos) {
        return TimberHandler.getTimberSpeedFactor(player, pos);
    }

    public static void executePhantomSpawnLogic(ServerLevel level, Player player) {
        EndPhantomHandler.executePhantomSpawnLogic(level, player);
    }
}