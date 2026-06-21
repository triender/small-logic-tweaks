package net.enderirt.smalllogictweaks.mixin;

import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.enderirt.smalllogictweaks.network.KitchenRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Shadow public abstract ItemStack getItem();
    @Shadow public abstract void setItem(ItemStack stack);

    private int slt$magmaCookTimer = 0;

    private static final TagKey<Item> MAGMA_COOKABLE = TagKey.create(
        Registries.ITEM,
        Identifier.fromNamespaceAndPath("small_logic_tweaks", "magma_cookable")
    );

    @org.spongepowered.asm.mixin.Unique
    private static final java.util.Map<Item, Boolean> slt$COOKABLE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    @org.spongepowered.asm.mixin.Unique
    private boolean slt$isCookable(Level level, ItemStack stack) {
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

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ItemEntity entity = (ItemEntity) (Object) this;
        Level level = entity.level();
        if (level.isClientSide()) {
            return;
        }

        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_AESTHETIC_KITCHEN) {
            if (slt$magmaCookTimer > 0) {
                slt$magmaCookTimer--;
            }
            return;
        }

        ItemStack stack = this.getItem();
        if (stack.isEmpty() || !slt$isCookable(level, stack)) {
            if (slt$magmaCookTimer > 0) {
                slt$magmaCookTimer--;
            }
            return;
        }

        BlockPos entityPos = entity.blockPosition();

        // 1. Check for Cauldron Boiling (wet stove)
        BlockPos cauldronPos = null;
        BlockPos cauldronHeatSourcePos = null;
        float cauldronHeatMultiplier = 0.0f;

        for (int d = 0; d >= -1; d--) {
            BlockPos checkPos = entityPos.offset(0, d, 0);
            BlockState state = level.getBlockState(checkPos);
            if (state.is(Blocks.WATER_CAULDRON)) {
                BlockPos underPos = checkPos.below();
                BlockState underState = level.getBlockState(underPos);
                float heat = slt$getHeatMultiplier(underState);
                if (heat > 0.0f) {
                    cauldronPos = checkPos;
                    cauldronHeatSourcePos = underPos;
                    cauldronHeatMultiplier = heat;
                    break;
                }
            }
        }

        boolean isCauldron = cauldronPos != null && SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_CAULDRON_BOILING;
        BlockPos heatSourcePos = null;
        float heatMultiplier = 0.0f;
        BlockPos coverPos = null;

        if (isCauldron) {
            heatSourcePos = cauldronHeatSourcePos;
            heatMultiplier = cauldronHeatMultiplier;
        } else if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_DRY_ROASTING) {
            // 2. Check for Dry Roasting
            BlockState stateAtEntity = level.getBlockState(entityPos);
            BlockState stateBelowEntity = level.getBlockState(entityPos.below());
            BlockState stateTwoBelowEntity = level.getBlockState(entityPos.below(2));
            
            if (slt$isCoverBlock(stateAtEntity)) {
                float heat = slt$getHeatMultiplier(stateBelowEntity);
                if (heat > 0.0f) {
                    coverPos = entityPos;
                    heatSourcePos = entityPos.below();
                    heatMultiplier = heat;
                }
            }
            if (heatSourcePos == null && slt$isCoverBlock(stateBelowEntity)) {
                float heat = slt$getHeatMultiplier(stateTwoBelowEntity);
                if (heat > 0.0f) {
                    coverPos = entityPos.below();
                    heatSourcePos = entityPos.below(2);
                    heatMultiplier = heat;
                }
            }
            if (heatSourcePos == null) {
                float heat = slt$getHeatMultiplier(stateAtEntity);
                if (heat > 0.0f) {
                    heatSourcePos = entityPos;
                    heatMultiplier = heat;
                }
            }
            if (heatSourcePos == null) {
                float heat = slt$getHeatMultiplier(stateBelowEntity);
                if (heat > 0.0f) {
                    heatSourcePos = entityPos.below();
                    heatMultiplier = heat;
                }
            }
        }

        // If no heat source found, cool down
        if (heatSourcePos == null) {
            if (slt$magmaCookTimer > 0) {
                slt$magmaCookTimer--;
            }
            return;
        }

        // Apply Carpet insulation if applicable
        if (coverPos != null) {
            BlockState coverState = level.getBlockState(coverPos);
            if (coverState.is(net.minecraft.tags.BlockTags.WOOL_CARPETS) || coverState.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock) {
                heatMultiplier /= 1.5f;
            }
        }

        // Dry roasting requires stack size == 1
        if (!isCauldron && stack.getCount() != 1) {
            if (slt$magmaCookTimer > 0) {
                slt$magmaCookTimer--;
            }
            return;
        }

        // Try reserving the heat source
        if (!KitchenRegistry.tryReserve(heatSourcePos, entity.getUUID(), level.getGameTime())) {
            if (slt$magmaCookTimer > 0) {
                slt$magmaCookTimer--;
            }
            return;
        }

        // Heat and progress
        slt$magmaCookTimer++;

        // Play sounds periodically while cooking (every 2-3 seconds on average)
        if (level.getRandom().nextInt(40) == 0) {
            if (isCauldron) {
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS,
                    0.5f, 1.0f + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2f);
            } else {
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.5f, 1.0f + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2f);
            }
        }

        // Particles
        if (level instanceof ServerLevel serverLevel) {
            if (isCauldron) {
                serverLevel.sendParticles(
                    ParticleTypes.BUBBLE,
                    entity.getX(), entity.getY() + 0.1, entity.getZ(),
                    1, 0.1, 0.1, 0.1, 0.0
                );
                // Also add a little bit of steam
                if (level.getRandom().nextInt(4) == 0) {
                    serverLevel.sendParticles(
                        ParticleTypes.SMOKE,
                        entity.getX(), entity.getY() + 0.35, entity.getZ(),
                        1, 0.05, 0.05, 0.05, 0.0
                    );
                }
            } else {
                serverLevel.sendParticles(
                    ParticleTypes.SMOKE,
                    entity.getX(), entity.getY() + 0.1, entity.getZ(),
                    1, 0.1, 0.1, 0.1, 0.0
                );
            }
        }

        // Check if cooked
        int cookTime = SmallLogicTweaksConfig.ACTIVE_INSTANCE.getCookTime(stack.getItem());
        int targetTime = (int) (cookTime / heatMultiplier);
        if (slt$magmaCookTimer >= targetTime) {
            ItemStack cookedResult = SmallLogicTweaksConfig.getCookedResult(level, stack);
            if (!cookedResult.isEmpty()) {
                if (isCauldron) {
                    // Boil sequentially
                    ItemStack singleCooked = cookedResult.copy();
                    singleCooked.setCount(1);
                    stack.shrink(1);
                    this.setItem(stack); // Update parent stack

                    ItemEntity cookedEntity = new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), singleCooked);
                    cookedEntity.setDeltaMovement(
                        (level.getRandom().nextFloat() - 0.5) * 0.1,
                        0.2 + level.getRandom().nextFloat() * 0.1,
                        (level.getRandom().nextFloat() - 0.5) * 0.1
                    );
                    level.addFreshEntity(cookedEntity);

                    // Boil complete: splash sound and bubble burst particles
                    level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS,
                        0.7f, 1.2f);
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.BUBBLE, entity.getX(), entity.getY() + 0.4, entity.getZ(), 6, 0.15, 0.15, 0.15, 0.05);
                        serverLevel.sendParticles(ParticleTypes.SPLASH, entity.getX(), entity.getY() + 0.4, entity.getZ(), 4, 0.15, 0.15, 0.15, 0.05);
                    }
                } else {
                    // Dry roasting replaces in place
                    this.setItem(cookedResult.copy());

                    // Roasting complete: sizzle/extinguish sound and smoke burst particles
                    level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                        0.4f, 1.5f + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2f);
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, entity.getX(), entity.getY() + 0.2, entity.getZ(), 5, 0.15, 0.1, 0.15, 0.02);
                    }
                }
            }
            slt$magmaCookTimer = 0;
        }
    }

    private float slt$getHeatMultiplier(BlockState state) {
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

    private boolean slt$isCoverBlock(BlockState state) {
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
