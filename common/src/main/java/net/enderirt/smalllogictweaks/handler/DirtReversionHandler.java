package net.enderirt.smalllogictweaks.handler;

import net.enderirt.smalllogictweaks.SmallLogicTweaksCommon;
import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * VI: Bộ xử lý logic khôi phục đất cày hoặc đường đất thành đất nguyên thủy bằng cúp.
 * EN: Logic handler for reverting farmland or dirt paths back to vanilla dirt using pickaxes.
 */
public final class DirtReversionHandler {

    /**
     * VI: Tag danh sách các khối có thể hoàn nguyên thành đất khi dùng cúp tương tác.
     * EN: Block tag identifying blocks that can be reverted to dirt when interacted with a pickaxe.
     */
    public static final TagKey<Block> PICKAXE_REVERSION_BLOCKS = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(SmallLogicTweaksCommon.MOD_ID, "pickaxe_reversion")
    );

    private DirtReversionHandler() {}

    /**
     * VI: Xử lý hành động dùng cúp chuột phải lên khối đường đất / đất cày.
     * EN: Handles right-clicking farmland or dirt paths with a pickaxe.
     *
     * @param world     VI: Thế giới cấp độ / EN: Level instance
     * @param player    VI: Người chơi thực hiện / EN: Acting player
     * @param hand      VI: Tay cầm vật phẩm / EN: Interaction hand
     * @param hitResult VI: Kết quả va chạm tia khối / EN: Raycast block hit result
     * @return          VI: Kết quả tương tác / EN: Interaction result
     */
    public static InteractionResult handlePickaxeReversion(Level world, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_PICKAXE_DIRT_REVERSION) return InteractionResult.PASS;
        if (player.isSpectator()) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);

        // 1. Kiểm tra công cụ phải là cúp (Pickaxe)
        if (!stack.is(ItemTags.PICKAXES)) return InteractionResult.PASS;

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);

        // 2. Kiểm tra khối phải thuộc tag #small_logic_tweaks:pickaxe_reversion
        if (!state.is(PICKAXE_REVERSION_BLOCKS)) return InteractionResult.PASS;

        // 3. Client-side prediction: Giao tập hợp giữa Hitbox người chơi và mặt trên của khối
        double targetY = pos.getY() + 1.0;
        AABB blockTopArea = new AABB(
                pos.getX(), pos.getY() + 0.85, pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.05, pos.getZ() + 1.0
        );

        if (player.getBoundingBox().intersects(blockTopArea) && player.getY() < targetY) {
            player.setPos(player.getX(), targetY, player.getZ());
            // Triệt tiêu quán tính rơi xuống nếu có
            var movement = player.getDeltaMovement();
            if (movement.y < 0) {
                player.setDeltaMovement(movement.x, 0.0, movement.z);
            }
        }

        if (world.isClientSide()) return InteractionResult.SUCCESS;

        // Logic xử lý phía server:
        // 4. Chuyển đổi khối thành đất nguyên thủy
        world.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());

        // 4.1. Bù đắp tọa độ Y phía server cho các thực thể lân cận (Mobs, động vật, người chơi khác)
        if (world instanceof ServerLevel serverLevel) {
            for (Entity entity : serverLevel.getEntitiesOfClass(Entity.class, blockTopArea)) {
                if (entity.getY() < targetY) {
                    entity.setPos(entity.getX(), targetY, entity.getZ());
                    var entMovement = entity.getDeltaMovement();
                    if (entMovement.y < 0) {
                        entity.setDeltaMovement(entMovement.x, 0.0, entMovement.z);
                    }
                }
            }
        }

        // 5. Phát âm thanh cuốc xới đất
        world.playSound(null, pos, SoundEvents.SHOVEL_FLATTEN.value(), SoundSource.BLOCKS, 1.0f, 0.9f + world.getRandom().nextFloat() * 0.2f);

        // 6. Bắn hiệu ứng hạt bụi / khói nhẹ
        if (world instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.SMOKE,
                    pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5,
                    6, 0.2, 0.05, 0.2, 0.02
            );
        }

        // 7. Trừ độ bền cúp (hỗ trợ Unbreaking, kích hoạt gãy nếu hết độ bền)
        if (!player.getAbilities().instabuild) {
            ServerPlayer sp = player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(1, (ServerLevel) world, sp, item -> player.onEquippedItemBroken(item, slot));
        }

        return InteractionResult.SUCCESS;
    }
}
