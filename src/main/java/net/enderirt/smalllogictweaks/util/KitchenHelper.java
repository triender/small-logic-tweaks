package net.enderirt.smalllogictweaks.util;

import net.enderirt.smalllogictweaks.SmallLogicTweaks;
import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class KitchenHelper {
    private KitchenHelper() {}

    public static final TagKey<Item> MAGMA_COOKABLE = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(SmallLogicTweaks.MOD_ID, "magma_cookable")
    );

    private static final Map<Item, Boolean> COOKABLE_CACHE = new ConcurrentHashMap<>();

    public static void clearCookableCache() {
        COOKABLE_CACHE.clear();
    }

    public static boolean isCookable(Level level, ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        Boolean cached = COOKABLE_CACHE.get(item);
        if (cached != null) {
            return cached;
        }

        boolean isMarked = stack.is(MAGMA_COOKABLE);
        String key = BuiltInRegistries.ITEM.getKey(item).toString();
        if (!isMarked) {
            isMarked = SmallLogicTweaksConfig.ACTIVE_INSTANCE.magmaCookTimes != null
                    && SmallLogicTweaksConfig.ACTIVE_INSTANCE.magmaCookTimes.containsKey(key);
        }
        if (!isMarked) {
            COOKABLE_CACHE.put(item, false);
            return false;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        String namespace = itemId.getNamespace();
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.aestheticCookResults != null
                && SmallLogicTweaksConfig.ACTIVE_INSTANCE.aestheticCookResults.containsKey(key)) {
            COOKABLE_CACHE.put(item, true);
            return true;
        }

        if (!namespace.equals("minecraft")) {
            COOKABLE_CACHE.put(item, false);
            return false;
        }

        boolean hasSmelting = false;
        var recipeAccess = level.recipeAccess();
        if (recipeAccess instanceof RecipeManager recipeManager) {
            try {
                var input = new SingleRecipeInput(stack);
                hasSmelting = recipeManager.getRecipeFor(RecipeType.SMELTING, input, level).isPresent();
            } catch (Exception e) {
                if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_DEBUG_LOGS) {
                    SmallLogicTweaks.LOGGER.debug("Failed to query smelting recipe for {}", key, e);
                }
            }
        }
        COOKABLE_CACHE.put(item, hasSmelting);
        return hasSmelting;
    }

    public static ItemStack getCookedResult(Level level, ItemStack rawStack) {
        if (level == null || rawStack == null || rawStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        String rawKey = BuiltInRegistries.ITEM.getKey(rawStack.getItem()).toString();
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.aestheticCookResults != null
                && SmallLogicTweaksConfig.ACTIVE_INSTANCE.aestheticCookResults.containsKey(rawKey)) {
            String cookedKey = SmallLogicTweaksConfig.ACTIVE_INSTANCE.aestheticCookResults.get(rawKey);
            Identifier cookedId = Identifier.tryParse(cookedKey);
            if (cookedId != null) {
                var holderOpt = BuiltInRegistries.ITEM.get(cookedId);
                if (holderOpt.isPresent()) {
                    Item cookedItem = holderOpt.get().value();
                    if (cookedItem != null && cookedItem != Items.AIR) {
                        return new ItemStack(cookedItem, rawStack.getCount());
                    }
                }
            }
        }
        var recipeAccess = level.recipeAccess();
        if (recipeAccess instanceof RecipeManager recipeManager) {
            try {
                var input = new SingleRecipeInput(rawStack);
                var recipeOpt = recipeManager.getRecipeFor(RecipeType.SMELTING, input, level);
                if (recipeOpt.isPresent()) {
                    return recipeOpt.get().value().assemble(input);
                }
            } catch (Exception e) {
                if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_DEBUG_LOGS) {
                    SmallLogicTweaks.LOGGER.debug("Failed to assemble smelting recipe for {}", rawKey, e);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static float getHeatMultiplier(BlockState state) {
        if (state.is(Blocks.MAGMA_BLOCK)) {
            return 1.0f;
        }
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            return 1.2f;
        }
        if (state.is(Blocks.LAVA)) {
            return 1.5f;
        }
        return 0.0f;
    }

    public static boolean isHeatSource(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.LAVA);
    }

    public static boolean isCoverBlock(BlockState state) {
        if (state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.PRESSURE_PLATES)
                || state.is(BlockTags.WOOL_CARPETS)
                || state.is(BlockTags.SLABS)) {
            return true;
        }
        var block = state.getBlock();
        return block instanceof TrapDoorBlock
                || block instanceof PressurePlateBlock
                || block instanceof CarpetBlock
                || block instanceof SlabBlock;
    }

    public static boolean isOnActiveStove(ItemEntity entity) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_AESTHETIC_KITCHEN
                || !SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_DRY_ROASTING) {
            return false;
        }
        Level level = entity.level();
        if (level.isClientSide()) return false;
        ItemStack stack = entity.getItem();
        if (stack.getCount() != 1 || !isCookable(level, stack)) return false;

        BlockPos entityPos = entity.blockPosition();
        BlockState stateAtEntity = level.getBlockState(entityPos);
        BlockState stateBelowEntity = level.getBlockState(entityPos.below());
        BlockState stateTwoBelowEntity = level.getBlockState(entityPos.below(2));

        if (isCoverBlock(stateAtEntity)) {
            if (isHeatSource(stateBelowEntity)) return true;
        }
        if (isCoverBlock(stateBelowEntity)) {
            if (isHeatSource(stateTwoBelowEntity)) return true;
        }
        return false;
    }
}
