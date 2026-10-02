package net.enderirt.smalllogictweaks.handler;

import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * VI: Bộ xử lý logic bón bột xương tái sinh đất thành khối cỏ hoặc khuẩn ty.
 * EN: Logic handler for bone meal soil regeneration into grass or mycelium.
 */
public final class BoneMealHandler {

    private BoneMealHandler() {}

    /**
     * VI: Xử lý hành động dùng bột xương lên khối đất.
     * EN: Handles using bone meal on soil blocks.
     *
     * @param world     VI: Thế giới cấp độ / EN: Level instance
     * @param player    VI: Người chơi thực hiện / EN: Acting player
     * @param hand      VI: Tay cầm vật phẩm / EN: Interaction hand
     * @param hitResult VI: Kết quả va chạm tia khối / EN: Raycast block hit result
     * @return          VI: Kết quả tương tác / EN: Interaction result
     */
    public static InteractionResult handleBoneMeal(Level world, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_BONE_MEAL_TWEAK) return InteractionResult.PASS;
        if (player.isSpectator()) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);

        // 1. Kiểm tra loại khối đất hợp lệ
        boolean isValidDirt = SmallLogicTweaksConfig.ACTIVE_INSTANCE.ALLOW_ALL_DIRT_TYPES
                ? state.is(BlockTags.DIRT)
                : state.is(Blocks.DIRT);

        if (stack.is(Items.BONE_MEAL) && isValidDirt) {
            BlockPos abovePos = pos.above();

            // 2. Kiểm tra không gian trống phía trên
            if (world.getBlockState(abovePos).isSolidRender() || !world.getFluidState(abovePos).isEmpty()) {
                return InteractionResult.PASS;
            }

            BlockState newState = null;

            // 3. Xử lý logic khối nguồn lân cận
            if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.REQUIRE_NEIGHBOR_SOURCE) {
                boolean hasGrassNeighbor = false;
                boolean hasMyceliumNeighbor = false;

                for (BlockPos checkPos : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                    if (checkPos.equals(pos)) continue;

                    BlockState neighborState = world.getBlockState(checkPos);
                    if (neighborState.is(Blocks.GRASS_BLOCK)) hasGrassNeighbor = true;
                    if (neighborState.is(Blocks.MYCELIUM)) hasMyceliumNeighbor = true;
                }

                // Chọn khối đích dựa trên khối nguồn thực tế xung quanh (Ưu tiên tính logic thực tế)
                if (hasMyceliumNeighbor || hasGrassNeighbor) {
                    if (world.getBiome(pos).is(Biomes.MUSHROOM_FIELDS)) {
                        // Biome nấm ưu tiên khuẩn ty, nếu không có khuẩn ty lân cận thì mới thành cỏ
                        newState = hasMyceliumNeighbor ? Blocks.MYCELIUM.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                    } else {
                        // Các biome thường ưu tiên cỏ, nếu không có cỏ lân cận thì mới thành khuẩn ty
                        newState = hasGrassNeighbor ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.MYCELIUM.defaultBlockState();
                    }
                }

                // Nếu bật REQUIRE_NEIGHBOR_SOURCE nhưng xung quanh hoàn toàn không có cỏ/khuẩn ty -> Hủy bỏ
                if (newState == null) return InteractionResult.PASS;
            } else {
                // Nếu không yêu cầu khối nguồn, áp dụng logic biome mặc định
                newState = world.getBiome(pos).is(Biomes.MUSHROOM_FIELDS)
                        ? Blocks.MYCELIUM.defaultBlockState()
                        : Blocks.GRASS_BLOCK.defaultBlockState();
            }

            // Điểm chốt làm mượt game (Client-side prediction):
            // Phía client thấy mọi điều kiện đã đủ thì trả về SUCCESS ngay để vung tay bón phân lập tức
            if (world.isClientSide()) return InteractionResult.SUCCESS;

            // Logic xử lý phía server
            world.setBlockAndUpdate(pos, newState);
            world.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);

            if (world instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        5, 0.25, 0.25, 0.25, 0.05
                );
            }

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}
