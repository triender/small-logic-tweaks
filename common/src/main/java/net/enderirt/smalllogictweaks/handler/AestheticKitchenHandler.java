package net.enderirt.smalllogictweaks.handler;

import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.enderirt.smalllogictweaks.network.KitchenRegistry;
import net.enderirt.smalllogictweaks.util.KitchenHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * VI: Bộ xử lý logic nấu ăn vật lý (Aesthetic Kitchen): nướng khô trên bếp và luộc trong vạc nước sôi.
 * EN: Logic handler for physics-based cooking (Aesthetic Kitchen): dry roasting on stoves and cauldron boiling.
 */
public final class AestheticKitchenHandler {

    private AestheticKitchenHandler() {}

    /**
     * VI: Xử lý một nhịp tick nấu ăn cho thực thể vật phẩm. Trả về giá trị timer sau khi cập nhật.
     * EN: Handles one cooking tick for an ItemEntity. Returns the updated timer value.
     *
     * @param entity        VI: Thực thể vật phẩm đang nấu / EN: Cooking item entity
     * @param currentTimer  VI: Thời gian nấu hiện tại (ticks) / EN: Current cooking timer in ticks
     * @return              VI: Thời gian nấu mới sau tick / EN: Updated cooking timer after tick
     */
    public static int tickCooking(ItemEntity entity, int currentTimer) {
        Level level = entity.level();
        if (level.isClientSide()) {
            return currentTimer;
        }

        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_AESTHETIC_KITCHEN) {
            return Math.max(0, currentTimer - 1);
        }

        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || !KitchenHelper.isCookable(level, stack)) {
            return Math.max(0, currentTimer - 1);
        }

        BlockPos entityPos = entity.blockPosition();

        // 1. Kiểm tra luộc trong vạc nước (Cauldron Boiling)
        BlockPos cauldronPos = null;
        BlockPos cauldronHeatSourcePos = null;
        float cauldronHeatMultiplier = 0.0f;

        for (int d = 0; d >= -1; d--) {
            BlockPos checkPos = entityPos.offset(0, d, 0);
            BlockState state = level.getBlockState(checkPos);
            if (state.is(Blocks.WATER_CAULDRON)) {
                BlockPos underPos = checkPos.below();
                BlockState underState = level.getBlockState(underPos);
                float heat = KitchenHelper.getHeatMultiplier(underState);
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
            // 2. Kiểm tra nướng khô trên bếp phủ hoặc trần (Dry Roasting)
            BlockState stateAtEntity = level.getBlockState(entityPos);
            BlockState stateBelowEntity = level.getBlockState(entityPos.below());
            BlockState stateTwoBelowEntity = level.getBlockState(entityPos.below(2));

            if (KitchenHelper.isCoverBlock(stateAtEntity)) {
                float heat = KitchenHelper.getHeatMultiplier(stateBelowEntity);
                if (heat > 0.0f) {
                    coverPos = entityPos;
                    heatSourcePos = entityPos.below();
                    heatMultiplier = heat;
                }
            }
            if (heatSourcePos == null && KitchenHelper.isCoverBlock(stateBelowEntity)) {
                float heat = KitchenHelper.getHeatMultiplier(stateTwoBelowEntity);
                if (heat > 0.0f) {
                    coverPos = entityPos.below();
                    heatSourcePos = entityPos.below(2);
                    heatMultiplier = heat;
                }
            }
            if (heatSourcePos == null) {
                float heat = KitchenHelper.getHeatMultiplier(stateAtEntity);
                if (heat > 0.0f) {
                    heatSourcePos = entityPos;
                    heatMultiplier = heat;
                }
            }
            if (heatSourcePos == null) {
                float heat = KitchenHelper.getHeatMultiplier(stateBelowEntity);
                if (heat > 0.0f) {
                    heatSourcePos = entityPos.below();
                    heatMultiplier = heat;
                }
            }
        }

        // Không tìm thấy nguồn nhiệt -> Giảm dần nhiệt độ
        if (heatSourcePos == null) {
            return Math.max(0, currentTimer - 1);
        }

        // Cách nhiệt bằng thảm len (giảm nhiệt lượng)
        if (coverPos != null) {
            BlockState coverState = level.getBlockState(coverPos);
            if (coverState.is(BlockTags.WOOL_CARPETS) || coverState.getBlock() instanceof CarpetBlock) {
                heatMultiplier /= 1.5f;
            }
        }

        // Nướng khô bắt buộc số lượng đúng 1 vật phẩm
        if (!isCauldron && stack.getCount() != 1) {
            return Math.max(0, currentTimer - 1);
        }

        // Đặt chỗ bếp nhiệt để tránh gian lận nấu hàng loạt trên 1 ô nhiệt
        if (!KitchenRegistry.tryReserve(level.dimension(), heatSourcePos, entity.getUUID(), level.getGameTime())) {
            return Math.max(0, currentTimer - 1);
        }

        // Tăng tiến trình nướng/luộc
        int timer = currentTimer + 1;

        // Âm thanh định kỳ khi nấu
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

        // Hạt hiệu ứng
        if (level instanceof ServerLevel serverLevel) {
            if (isCauldron) {
                serverLevel.sendParticles(
                        ParticleTypes.BUBBLE,
                        entity.getX(), entity.getY() + 0.1, entity.getZ(),
                        1, 0.1, 0.1, 0.1, 0.0
                );
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

        // Kiểm tra chín thức ăn
        int cookTime = SmallLogicTweaksConfig.ACTIVE_INSTANCE.getCookTime(stack.getItem());
        int targetTime = (int) (cookTime / heatMultiplier);
        if (timer >= targetTime) {
            ItemStack cookedResult = KitchenHelper.getCookedResult(level, stack);
            if (!cookedResult.isEmpty()) {
                if (isCauldron) {
                    // Luộc tuần tự từng cái một từ xấp vật phẩm
                    ItemStack singleCooked = cookedResult.copy();
                    singleCooked.setCount(1);
                    stack.shrink(1);
                    entity.setItem(stack);

                    ItemEntity cookedEntity = new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), singleCooked);
                    cookedEntity.setDeltaMovement(
                            (level.getRandom().nextFloat() - 0.5) * 0.1,
                            0.2 + level.getRandom().nextFloat() * 0.1,
                            (level.getRandom().nextFloat() - 0.5) * 0.1
                    );
                    level.addFreshEntity(cookedEntity);

                    level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS,
                            0.7f, 1.2f);
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.BUBBLE, entity.getX(), entity.getY() + 0.4, entity.getZ(), 6, 0.15, 0.15, 0.15, 0.05);
                        serverLevel.sendParticles(ParticleTypes.SPLASH, entity.getX(), entity.getY() + 0.4, entity.getZ(), 4, 0.15, 0.15, 0.15, 0.05);
                    }
                } else {
                    // Nướng khô thay thế tại chỗ
                    entity.setItem(cookedResult.copy());

                    level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                            0.4f, 1.5f + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2f);
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, entity.getX(), entity.getY() + 0.2, entity.getZ(), 5, 0.15, 0.1, 0.15, 0.02);
                    }
                }
            }
            return 0;
        }

        return timer;
    }
}
