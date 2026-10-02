package net.enderirt.smalllogictweaks.handler;

import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.Map;

/**
 * VI: Bộ xử lý logic hóa rắn bột bê tông bằng bình nước hoặc lọ thuốc ném nước.
 * EN: Logic handler for concrete powder hydration into hardened concrete.
 */
public final class ConcreteHardeningHandler {

    /**
     * VI: Bản đồ ánh xạ 16 màu của bột bê tông sang khối bê tông tương ứng.
     * EN: Immutable map of concrete powder blocks to their hardened counterparts.
     */
    public static final Map<Block, Block> POWDER_TO_CONCRETE;

    static {
        Map<Block, Block> map = new HashMap<>();
        for (DyeColor color : DyeColor.values()) {
            map.put(Blocks.CONCRETE_POWDER.pick(color), Blocks.CONCRETE.pick(color));
        }
        POWDER_TO_CONCRETE = Map.copyOf(map);
    }

    private ConcreteHardeningHandler() {}

    /**
     * VI: Cố gắng hóa rắn khối bột bê tông tại tọa độ xác định.
     * EN: Attempts to hydrate and harden concrete powder at the specified position.
     *
     * @param world VI: Thế giới cấp độ / EN: Level instance
     * @param pos   VI: Tọa độ khối / EN: Target block position
     * @return      VI: True nếu hóa rắn thành công / EN: True if hardening succeeded
     */
    public static boolean tryHardenConcrete(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Block hardenedBlock = POWDER_TO_CONCRETE.get(state.getBlock());

        if (hardenedBlock != null) {
            world.setBlockAndUpdate(pos, hardenedBlock.defaultBlockState());
            if (world instanceof ServerLevel serverLevel) {
                // Tạo hiệu ứng hạt nước bắn tung tóe tại vị trí khối được hóa rắn
                serverLevel.sendParticles(
                        ParticleTypes.SPLASH,
                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        5, 0.25, 0.25, 0.25, 0.05
                );
            }
            return true;
        }
        return false;
    }

    /**
     * VI: Xử lý hành động dùng chai nước cất tương tác trực tiếp lên khối bột bê tông.
     * EN: Handles right-clicking concrete powder with a water bottle.
     *
     * @param world     VI: Thế giới cấp độ / EN: Level instance
     * @param player    VI: Người chơi thực hiện / EN: Acting player
     * @param hand      VI: Tay cầm vật phẩm / EN: Interaction hand
     * @param hitResult VI: Kết quả va chạm tia khối / EN: Raycast block hit result
     * @return          VI: Kết quả tương tác / EN: Interaction result
     */
    public static InteractionResult handleWaterBottle(Level world, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (player.isSpectator() || !SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_HYDRO_HARDENING) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(hand);
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);

        // Xác minh vật phẩm trên tay là chai thuốc (Potion)
        if (stack.is(Items.POTION)) {
            // Đọc component dữ liệu để kiểm tra xem có phải chai nước (Water) hay không
            var potionContents = stack.get(DataComponents.POTION_CONTENTS);

            if (potionContents != null && potionContents.is(Potions.WATER)) {
                Block hardenedBlock = POWDER_TO_CONCRETE.get(state.getBlock());

                if (hardenedBlock != null) {
                    // Đồng bộ phía client (Client-side prediction):
                    // Trả về SUCCESS ở client để chặn hành vi mặc định (uống nước)
                    if (world.isClientSide()) {
                        return InteractionResult.SUCCESS;
                    }

                    // Logic xử lý phía server:
                    // 1. Cập nhật khối thành bê tông đặc
                    world.setBlockAndUpdate(pos, hardenedBlock.defaultBlockState());

                    // 2. Phát âm thanh kết hợp: Tiếng đổ nước và tiếng dọn chai
                    world.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.5f, 1.0f);
                    world.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);

                    // 3. Xử lý hoàn trả chai thủy tinh rỗng (Nếu không phải chế độ sáng tạo)
                    if (!player.getAbilities().instabuild) {
                        ItemStack glassBottle = new ItemStack(Items.GLASS_BOTTLE);
                        stack.shrink(1);

                        if (stack.isEmpty()) {
                            // Nếu trên tay chỉ có đúng 1 bình, thay trực tiếp bằng chai rỗng
                            player.setItemInHand(hand, glassBottle);
                        } else if (!player.getInventory().add(glassBottle)) {
                            // Nếu hành trang đầy, rơi chai rỗng xuống đất
                            player.drop(glassBottle, false, Prediction.SERVER_ONLY);
                        }
                    }

                    return InteractionResult.SUCCESS;
                }
            }
        }

        return InteractionResult.PASS;
    }
}
