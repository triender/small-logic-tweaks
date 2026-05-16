package net.enderirt.smalllogictweaks;

import net.enderirt.smalllogictweaks.ModEnchants.Timber;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.LeavesBlock;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class SmallLogicTweaksEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger("small_logic_tweaks");
    public static void register() {
        registerBoneMealTweak();
        registerTimberTweak();
        LOGGER.info(" Small logic Tweaks Mod register success!!");
    }

    private static void registerBoneMealTweak() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || player.isSpectator()) return InteractionResult.PASS;

            ItemStack stack = player.getItemInHand(hand);
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            if (stack.is(Items.BONE_MEAL) && state.is(BlockTags.DIRT)) {
                BlockPos abovePos = pos.above();
                // Kiểm tra khối phía trên có đặc không
                if (world.getBlockState(abovePos).isSolidRender() || !world.getFluidState(abovePos).isEmpty())
                    return InteractionResult.PASS;

                // Logic Biome để chọn Cỏ hoặc Khuẩn ty
                BlockState newState = world.getBiome(pos).is(Biomes.MUSHROOM_FIELDS)
                        ? net.minecraft.world.level.block.Blocks.MYCELIUM.defaultBlockState()
                        : net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();

                // 1. Đổi khối
                world.setBlockAndUpdate(pos, newState);
                // 2. PHÁT ÂM THANH
                world.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
                // 3. HIỆU ỨNG HẠT (Cách Server chắc chắn thành công)
                if (world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    // Tọa độ X, Y, Z (cộng thêm 0.5 và 1.0 để hạt nằm ở giữa bề mặt khối)
                    // 15: số lượng hạt
                    // 0.25, 0.25, 0.25: độ lan tỏa (offset)
                    // 0.05: tốc độ bay của hạt
                    serverLevel.sendParticles(
                            net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                            5, 0.25, 0.25, 0.25, 0.05
                    );
                }

                if (!player.getAbilities().instabuild)
                    stack.shrink(1);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    public static boolean ENABLE_TIMBER_DEBUG_LOGS = false;

    // Lớp chứa cấu hình - Dễ dàng thay thế hoặc liên kết với Mod Config sau này
    public static class TimberConfig {
        // Cờ bật/tắt tính năng Fast Leaf Decay tích hợp
        public static boolean ENABLE_AUTO_LEAVES_DECAY = true;

        // Các Magic Numbers được tách ra
        public static int MAX_LOG_HORIZONTAL_RADIUS = 5; // Giới hạn quét gỗ theo trục X/Z
        public static int MAX_LEAF_DISTANCE = 7;         // Bán kính tối đa từ thân cây đến lá
        public static int MIN_LEAVES_FOR_TREE = 4;       // Số lá tối thiểu để xác nhận là một cái cây
        public static int DECAY_THRESHOLD = 6;
    }
    public record TimberResult(boolean shouldChop, List<BlockPos> logs, List<BlockPos> leaves, int level) {}

    // Lưu trữ kết quả phân tích của khối đang đào hiện tại
    private static final ThreadLocal<BlockPos> LAST_CHECKED_POS = new ThreadLocal<>();
    private static final ThreadLocal<Float> CACHED_FACTOR = ThreadLocal.withInitial(() -> 1.0f);


    // Hàm tiện ích để in Log
    private static void debugLog(String message, Object... args) {
        if (ENABLE_TIMBER_DEBUG_LOGS) {
            LOGGER.info(message, args);
        }
    }

    private static void registerTimberTweak() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (!(world instanceof ServerLevel serverLevel) || player.isSpectator()) return true;

            ItemStack axe = player.getMainHandItem();

            if (state.is(BlockTags.LOGS)) {
                debugLog("[Timber] Player {} is breaking a log at {}", player.getName().getString(), pos.toShortString());
            }

            TimberResult result = analyzeTimber(serverLevel, player, pos, axe);

            if (result.shouldChop()) {
                debugLog("[Timber] Validation successful! Found {} logs to break.", result.logs().size());
                performTimberChop(serverLevel, (ServerPlayer) player, axe, result);
                return false;
            }

            return true;
        });
    }

    public static float getTimberSpeedFactor(Player player, BlockPos pos) {
        Level level = player.level();

        if (pos.equals(LAST_CHECKED_POS.get())) {
            return CACHED_FACTOR.get();
        }

        ItemStack axe = player.getMainHandItem();
        var result = analyzeTimber(level, player, pos, axe);

        if (result.shouldChop() && !result.logs().isEmpty()) {
            float multiplier = switch (result.level()) {
                case 1 -> 1.0f;
                case 2 -> 0.9f;
                case 3 -> 0.8f;
                default -> 1.0f;
            };
            CACHED_FACTOR.set(result.logs().size() * multiplier);
        } else {
            CACHED_FACTOR.set(1.0f);
        }

        LAST_CHECKED_POS.set(pos.immutable());
        return CACHED_FACTOR.get();
    }

    private static TimberResult analyzeTimber(Level level, Player player, BlockPos startPos, ItemStack axe) {
        if (player.isShiftKeyDown() || !level.getBlockState(startPos).is(BlockTags.LOGS))
            return new TimberResult(false, List.of(), List.of(), 0);

        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int levelEnchant = EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(Timber.TIMBER), axe);

        if (levelEnchant <= 0) {
            return new TimberResult(false, List.of(), List.of(), 0);
        }

        int maxLogs;
        switch (levelEnchant) {
            case 1 -> maxLogs = 16;
            case 2 -> maxLogs = 64;
            case 3 -> maxLogs = 140;
            default -> maxLogs = 0;
        }

        Set<BlockPos> visitedLogs = new HashSet<>();
        List<BlockPos> logs = new ArrayList<>();
        Queue<BlockPos> logQueue = new LinkedList<>();

        logQueue.add(startPos);
        visitedLogs.add(startPos);

        // --- PHA 1: TÌM GỖ ---
        while (!logQueue.isEmpty() && logs.size() < maxLogs) {
            BlockPos current = logQueue.poll();
            logs.add(current);

            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        BlockPos neighbor = current.offset(x, y, z);

                        // Sử dụng biến cấu hình thay vì Magic Number
                        if (Math.abs(neighbor.getX() - startPos.getX()) > TimberConfig.MAX_LOG_HORIZONTAL_RADIUS ||
                                Math.abs(neighbor.getZ() - startPos.getZ()) > TimberConfig.MAX_LOG_HORIZONTAL_RADIUS) continue;

                        if (!visitedLogs.contains(neighbor) && level.getBlockState(neighbor).is(BlockTags.LOGS)) {
                            visitedLogs.add(neighbor);
                            logQueue.add(neighbor);
                        }
                    }
                }
            }
        }

        // --- PHA 2: TÌM LÁ ---
        Set<BlockPos> visitedLeaves = new HashSet<>();
        List<BlockPos> leaves = new ArrayList<>();
        Queue<BlockPos> leafQueue = new LinkedList<>();
        Map<BlockPos, Integer> leafDistance = new HashMap<>();

        for (BlockPos log : logs) {
            leafQueue.add(log);
            leafDistance.put(log, 0);
        }

        while (!leafQueue.isEmpty()) {
            BlockPos current = leafQueue.poll();
            int currentDist = leafDistance.get(current);

            // Sử dụng biến cấu hình khoảng cách lá
            if (currentDist >= TimberConfig.MAX_LEAF_DISTANCE) continue;

            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos neighbor = current.relative(dir);

                if (!visitedLogs.contains(neighbor) && !visitedLeaves.contains(neighbor)) {
                    visitedLeaves.add(neighbor);
                    BlockState state = level.getBlockState(neighbor);

                    if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)) {
                        leaves.add(neighbor);
                        leafDistance.put(neighbor, currentDist + 1);
                        leafQueue.add(neighbor);
                    }
                }
            }
        }

        // Sử dụng biến cấu hình điều kiện lá tối thiểu
        boolean isTree = !logs.isEmpty() && leaves.size() >= TimberConfig.MIN_LEAVES_FOR_TREE;

        return new TimberResult(isTree, logs, leaves, levelEnchant);
    }

    private static void performTimberChop(ServerLevel level, ServerPlayer player, ItemStack axe, TimberResult result) {
        debugLog("[Timber] Execution: Breaking {} logs and checking {} leaves...", result.logs().size(), result.leaves().size());
        int brokenLogsCount = 0;

        // 1. PHÁ GỖ (Giữ nguyên)
        for (BlockPos logPos : result.logs()) {
            if (axe.isEmpty() || (axe.isDamageableItem() && axe.getDamageValue() >= axe.getMaxDamage())) {
                break;
            }
            if (level.destroyBlock(logPos, true, player)) {
                brokenLogsCount++;
                axe.hurtAndBreak(1, level, player, (item) ->
                        player.onEquippedItemBroken(item, EquipmentSlot.MAINHAND));
            }
        }

        // 2. ÉP GAME LOGIC XỬ LÝ LÁ (Tối ưu hóa In-Memory)
        if (TimberConfig.ENABLE_AUTO_LEAVES_DECAY) {

            // Khởi tạo không gian RAM để lưu trữ khoảng cách tính toán
            Map<BlockPos, Integer> virtualDistances = new HashMap<>();
            for (BlockPos leafPos : result.leaves()) {
                BlockState state = level.getBlockState(leafPos);
                if (state.hasProperty(LeavesBlock.DISTANCE)) {
                    virtualDistances.put(leafPos, state.getValue(LeavesBlock.DISTANCE));
                }
            }

            boolean changed;
            int loops = 0;

            // BƯỚC A: Chạy thuật toán cập nhật khoảng cách hoàn toàn trên RAM
            do {
                changed = false;
                for (BlockPos leafPos : result.leaves()) {
                    BlockState state = level.getBlockState(leafPos);

                    if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.DISTANCE) && !state.getValue(LeavesBlock.PERSISTENT)) {
                        int currentDist = virtualDistances.getOrDefault(leafPos, 7);
                        int minDistance = 7;

                        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                            BlockPos neighborPos = leafPos.relative(dir);
                            BlockState neighbor = level.getBlockState(neighborPos);

                            if (neighbor.is(BlockTags.LOGS)) {
                                minDistance = 1;
                                break;
                            } else if (neighbor.is(BlockTags.LEAVES) && neighbor.hasProperty(LeavesBlock.DISTANCE)) {
                                // Lấy khoảng cách từ RAM nếu có, ngược lại lấy từ thế giới thực
                                int neighborDist = virtualDistances.containsKey(neighborPos)
                                        ? virtualDistances.get(neighborPos)
                                        : neighbor.getValue(LeavesBlock.DISTANCE);

                                minDistance = Math.min(minDistance, neighborDist + 1);
                            }
                        }

                        // So sánh trên RAM, không gọi level.setBlock
                        if (currentDist != minDistance) {
                            virtualDistances.put(leafPos, minDistance);
                            changed = true;
                        }
                    }
                }
                loops++;
            } while (changed && loops < 10);

            // BƯỚC B: Phá vỡ hoặc cập nhật lại trạng thái thực tế
            for (BlockPos leafPos : result.leaves()) {
                BlockState state = level.getBlockState(leafPos);

                if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.DISTANCE) && !state.getValue(LeavesBlock.PERSISTENT)) {
                    int finalDistance = virtualDistances.getOrDefault(leafPos, 7);

                    if (finalDistance >= TimberConfig.DECAY_THRESHOLD) {
                        // Lá đạt ngưỡng: Thực hiện phá khối
                        level.destroyBlock(leafPos, true);
                    } else if (finalDistance != state.getValue(LeavesBlock.DISTANCE)) {
                        // Lá còn sống (do nối với cây khác): Cập nhật lại khoảng cách thực tế vào thế giới
                        level.setBlock(leafPos, state.setValue(LeavesBlock.DISTANCE, finalDistance), 20);
                    }
                }
            }
        }

        debugLog("[Timber] Execution finished. Logs broken: {}", brokenLogsCount);
    }
}