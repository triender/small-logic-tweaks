package net.enderirt.smalllogictweaks.mixin;

import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.enderirt.smalllogictweaks.network.KitchenRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMergeMixin {
    @Shadow public abstract ItemStack getItem();

    private static final TagKey<Item> MAGMA_COOKABLE = TagKey.create(
        Registries.ITEM,
        Identifier.fromNamespaceAndPath("small_logic_tweaks", "magma_cookable")
    );

    @org.spongepowered.asm.mixin.Unique
    private static final java.util.Map<Item, Boolean> slt$COOKABLE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    @org.spongepowered.asm.mixin.Unique
    private boolean slt$isCookableMerge(Level level, ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        Boolean cached = slt$COOKABLE_CACHE.get(item);
        if (cached != null) {
            return cached;
        }
        boolean isMarked = stack.is(MAGMA_COOKABLE);
        String key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
        if (!isMarked) {
            isMarked = SmallLogicTweaksConfig.ACTIVE_INSTANCE.magmaCookTimes != null
                && SmallLogicTweaksConfig.ACTIVE_INSTANCE.magmaCookTimes.containsKey(key);
        }
        if (!isMarked) {
            slt$COOKABLE_CACHE.put(item, false);
            return false;
        }
        net.minecraft.resources.Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        String namespace = itemId.getNamespace();
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.aestheticCookResults != null 
            && SmallLogicTweaksConfig.ACTIVE_INSTANCE.aestheticCookResults.containsKey(key)) {
            slt$COOKABLE_CACHE.put(item, true);
            return true;
        }
        if (!namespace.equals("minecraft")) {
            slt$COOKABLE_CACHE.put(item, false);
            return false;
        }
        boolean hasSmelting = false;
        var recipeAccess = level.recipeAccess();
        if (recipeAccess instanceof net.minecraft.world.item.crafting.RecipeManager recipeManager) {
            try {
                var input = new net.minecraft.world.item.crafting.SingleRecipeInput(stack);
                hasSmelting = recipeManager.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, input, level).isPresent();
            } catch (Throwable t) {
            }
        }
        slt$COOKABLE_CACHE.put(item, hasSmelting);
        return hasSmelting;
    }

    private boolean slt$isOnActiveStove(ItemEntity entity) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_AESTHETIC_KITCHEN || !SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_DRY_ROASTING) return false;
        Level level = entity.level();
        if (level.isClientSide()) return false;
        ItemStack stack = entity.getItem();
        if (stack.getCount() != 1 || !slt$isCookableMerge(level, stack)) return false;

        BlockPos entityPos = entity.blockPosition();
        BlockState stateAtEntity = level.getBlockState(entityPos);
        BlockState stateBelowEntity = level.getBlockState(entityPos.below());
        BlockState stateTwoBelowEntity = level.getBlockState(entityPos.below(2));

        if (slt$isCoverBlockMerge(stateAtEntity)) {
            if (slt$isHeatSource(stateBelowEntity)) return true;
        }
        if (slt$isCoverBlockMerge(stateBelowEntity)) {
            if (slt$isHeatSource(stateTwoBelowEntity)) return true;
        }
        return false;
    }

    @Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true)
    private void onTryToMerge(ItemEntity other, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (slt$isOnActiveStove(self) || slt$isOnActiveStove(other)) {
            ci.cancel();
        }
    }

    private boolean slt$isHeatSource(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.LAVA);
    }

    private boolean slt$isCoverBlockMerge(BlockState state) {
        if (state.is(net.minecraft.tags.BlockTags.TRAPDOORS)
            || state.is(net.minecraft.tags.BlockTags.PRESSURE_PLATES)
            || state.is(net.minecraft.tags.BlockTags.WOOL_CARPETS)
            || state.is(net.minecraft.tags.BlockTags.SLABS)) {
            return true;
        }
        String name = state.getBlock().getClass().getSimpleName().toLowerCase();
        return name.contains("trapdoor") || name.contains("pressureplate") || name.contains("carpet") || name.contains("slab");
    }
}
