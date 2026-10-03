package net.enderirt.smalllogictweaks;

import net.enderirt.smalllogictweaks.handler.BoneMealHandler;
import net.enderirt.smalllogictweaks.handler.ConcreteHardeningHandler;
import net.enderirt.smalllogictweaks.handler.DirtReversionHandler;
import net.enderirt.smalllogictweaks.handler.EndPhantomHandler;
import net.enderirt.smalllogictweaks.handler.TimberHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * VI: Nối event NeoForge vào các Handler logic dùng chung (tương đương SmallLogicTweaksEvents bên Fabric).
 * EN: Wires NeoForge events into the shared logic handlers (NeoForge counterpart of Fabric's SmallLogicTweaksEvents).
 */
public final class NeoForgeEvents {
    private NeoForgeEvents() {}

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(NeoForgeEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(NeoForgeEvents::onBreakBlock);
        NeoForge.EVENT_BUS.addListener(NeoForgeEvents::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(NeoForgeEvents::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(NeoForgeEvents::onLevelTick);
        modBus.addListener(NeoForgeEvents::onModifyDefaultComponents);
        SmallLogicTweaksCommon.LOGGER.info("Small logic Tweaks Mod register success!!");
    }

    // Same order as Fabric's UseBlockCallback listeners: the first non-PASS result wins
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        InteractionResult result = BoneMealHandler.handleBoneMeal(level, event.getEntity(), event.getHand(), event.getHitVec());
        if (result == InteractionResult.PASS) {
            result = ConcreteHardeningHandler.handleWaterBottle(level, event.getEntity(), event.getHand(), event.getHitVec());
        }
        if (result == InteractionResult.PASS) {
            result = DirtReversionHandler.handlePickaxeReversion(level, event.getEntity(), event.getHand(), event.getHitVec());
        }
        if (result != InteractionResult.PASS) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    private static void onBreakBlock(BreakBlockEvent event) {
        if (!TimberHandler.handleTimberChop((Level) event.getLevel(), event.getPlayer(), event.getPos(), event.getState())) {
            event.setCanceled(true);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getFrom() == Level.END && event.getEntity() instanceof ServerPlayer player) {
            EndPhantomHandler.resetInsomnia(player);
        }
    }

    // Fabric uses AFTER_RESPAWN(oldPlayer, newPlayer); Clone carries both players during respawn
    private static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal().level().dimension() == Level.END && event.getEntity() instanceof ServerPlayer newPlayer) {
            EndPhantomHandler.resetInsomnia(newPlayer);
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            EndPhantomHandler.handleEndTick(serverLevel);
        }
    }

    private static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_POISONOUS_POTATO_COMPOST) {
            event.modify(Items.POISONOUS_POTATO, (builder, registries, item) ->
                    builder.set(DataComponents.COMPOSTABLE, new Compostable(new ResolvableInt.Constant(65))));
            if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_DEBUG_LOGS) {
                SmallLogicTweaksCommon.LOGGER.info("[Potato Tweak] Registered Poisonous Potato to Composter via ModifyDefaultComponentsEvent.");
            }
        }
    }
}
