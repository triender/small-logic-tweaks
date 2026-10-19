package net.enderirt.smalllogictweaks.handler;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.enderirt.smalllogictweaks.ModEnchants.Timber;
import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * VI: Bộ xử lý logic phù phép đốn gỗ hàng loạt (Timber) và tính toán lực cản cấu trúc cây.
 * EN: Logic handler for Timber tree felling enchantment and structural mining resistance.
 */
public final class TimberHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("small_logic_tweaks");

    /**
     * VI: Cấu trúc kết quả phân tích cấu trúc cây gỗ Timber.
     * EN: Timber tree structure analysis result record.
     */
    public record TimberResult(boolean shouldChop, List<BlockPos> logs, List<BlockPos> leaves, int level) {}

    /**
     * VI: Khóa bộ nhớ đệm tốc độ đào Timber tổng hợp toàn bộ các chiều phụ thuộc dữ liệu:
     * Chiều không gian, tọa độ khối, UUID người chơi, cấp độ phù phép Timber và trạng thái cúi người (Shift).
     * EN: Composite Timber mining speed cache key incorporating all data dependencies:
     * Dimension, block position, player UUID, Timber enchantment level, and sneak (Shift) state.
     */
    public record TimberSpeedKey(
            Object dimension,
            BlockPos pos,
            UUID playerId,
            int timberLevel,
            boolean isShiftKeyDown
    ) {}

    // Bộ nhớ đệm ngắn hạn (2 giây, tối đa 20 mục) ngăn ngừa tụt FPS khi tia đào va chạm liên tục mỗi tick
    private static final Cache<TimberSpeedKey, Float> TIMBER_SPEED_CACHE = CacheBuilder.newBuilder()
            .maximumSize(20)
            .expireAfterWrite(2, TimeUnit.SECONDS)
            .build();

    private TimberHandler() {}

    private static void debugLog(String message, Object... args) {
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_TIMBER_DEBUG_LOGS) {
            LOGGER.info(message, args);
        }
    }

    /**
     * VI: Xử lý sự kiện phá vỡ khối gỗ bằng rìu có phù phép Timber.
     * EN: Handles block breaking event when felling trees with a Timber axe.
     *
     * @param world  VI: Thế giới cấp độ / EN: Level instance
     * @param player VI: Người chơi thực hiện / EN: Acting player
     * @param pos    VI: Tọa độ khối gỗ / EN: Target block position
     * @param state  VI: Trạng thái khối / EN: Block state
     * @return       VI: False nếu đã thực thi Timber (để hủy phá khối đơn lẻ mặc định), True nếu tiếp tục bình thường /
     *               EN: False if Timber handled the action (cancels default single break), True to continue normally
     */
    public static boolean handleTimberChop(Level world, Player player, BlockPos pos, BlockState state) {
        if (!(world instanceof ServerLevel serverLevel) || player.isSpectator()) return true;

        ItemStack axe = player.getMainHandItem();

        if (state.is(BlockTags.LOGS)) {
            debugLog("[Timber] Player {} is breaking a log at {}", player.getName().getString(), pos.toShortString());
        }

        TimberResult result = analyzeTimber(serverLevel, player, pos, axe);

        if (result.shouldChop()) {
            debugLog("[Timber] Validation successful! Found {} logs to break.", result.logs().size());
            performTimberChop(serverLevel, player, axe, result);
            return false;
        }

        return true;
    }

    /**
     * VI: Tính toán và tra cứu hệ số làm chậm tốc độ đào (sức cản cấu trúc) tương ứng với quy mô của cây gỗ.
     * EN: Calculates and retrieves the mining speed slowdown factor (structural resistance) relative to tree scale.
     *
     * @param player VI: Người chơi đang khai thác / EN: Player mining the block
     * @param pos    VI: Vị trí khối gỗ mục tiêu / EN: Target log block position
     * @return       VI: Hệ số làm chậm (1.0f là tốc độ bình thường) / EN: Slowdown factor (1.0f represents vanilla speed)
     */
    public static float getTimberSpeedFactor(Player player, BlockPos pos) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_TIMBER_TWEAK || player.isShiftKeyDown()) {
            return 1.0f;
        }

        Level level = player.level();
        if (!level.getBlockState(pos).is(BlockTags.LOGS)) {
            return 1.0f;
        }

        ItemStack axe = player.getMainHandItem();
        int levelEnchant = 0;
        var enchantLookup = level.registryAccess().lookup(Registries.ENCHANTMENT);
        if (enchantLookup.isPresent()) {
            var timberHolder = enchantLookup.get().get(Timber.TIMBER);
            if (timberHolder.isPresent()) {
                levelEnchant = EnchantmentHelper.getItemEnchantmentLevel(timberHolder.get(), axe);
            }
        }

        if (levelEnchant <= 0) {
            return 1.0f;
        }

        TimberSpeedKey key = new TimberSpeedKey(
                level.dimension(),
                pos.immutable(),
                player.getUUID(),
                levelEnchant,
                player.isShiftKeyDown()
        );

        Float cachedFactor = TIMBER_SPEED_CACHE.getIfPresent(key);
        if (cachedFactor != null) {
            return cachedFactor;
        }

        var result = analyzeTimber(level, player, pos, axe);

        float newFactor = 1.0f;
        if (result.shouldChop() && !result.logs().isEmpty()) {
            float multiplier = switch (result.level()) {
                case 1 -> 1.0f;
                case 2 -> 0.9f;
                case 3 -> 0.8f;
                default -> 1.0f;
            };
            newFactor = result.logs().size() * multiplier;
        }

        TIMBER_SPEED_CACHE.put(key, newFactor);
        return newFactor;
    }

    /**
     * VI: Phân tích cấu trúc hình học cây bằng thuật toán tìm kiếm theo chiều rộng (BFS).
     * EN: Analyzes tree geometry using Breadth-First Search (BFS).
     */
    public static TimberResult analyzeTimber(Level level, Player player, BlockPos startPos, ItemStack axe) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_TIMBER_TWEAK)
            return new TimberResult(false, List.of(), List.of(), 0);

        if (player.isShiftKeyDown() || !level.getBlockState(startPos).is(BlockTags.LOGS))
            return new TimberResult(false, List.of(), List.of(), 0);

        var enchantLookup = level.registryAccess().lookup(Registries.ENCHANTMENT);
        if (enchantLookup.isEmpty()) {
            return new TimberResult(false, List.of(), List.of(), 0);
        }
        var timberHolder = enchantLookup.get().get(Timber.TIMBER);
        if (timberHolder.isEmpty()) {
            return new TimberResult(false, List.of(), List.of(), 0);
        }
        int levelEnchant = EnchantmentHelper.getItemEnchantmentLevel(timberHolder.get(), axe);

        if (levelEnchant <= 0) {
            return new TimberResult(false, List.of(), List.of(), 0);
        }

        int maxLogs = switch (levelEnchant) {
            case 1 -> 16;
            case 2 -> 64;
            default -> 140;
        };

        int maxHorizontalRadius = SmallLogicTweaksConfig.ACTIVE_INSTANCE.MAX_LOG_HORIZONTAL_RADIUS;
        List<BlockPos> logs = collectConnectedLogs(level, startPos, maxLogs, maxHorizontalRadius);

        int maxLeafDistance = SmallLogicTweaksConfig.ACTIVE_INSTANCE.MAX_LEAF_DISTANCE;
        List<BlockPos> leaves = collectConnectedLeaves(level, logs, maxLeafDistance);

        boolean isTree = !logs.isEmpty() && leaves.size() >= SmallLogicTweaksConfig.ACTIVE_INSTANCE.MIN_LEAVES_FOR_TREE;
        return new TimberResult(isTree, logs, leaves, levelEnchant);
    }

    private static List<BlockPos> collectConnectedLogs(Level level, BlockPos startPos, int maxLogs, int maxHorizontalRadius) {
        Set<BlockPos> visitedLogs = new HashSet<>();
        List<BlockPos> logs = new ArrayList<>();
        Queue<BlockPos> logQueue = new LinkedList<>();

        logQueue.add(startPos);
        visitedLogs.add(startPos);

        while (!logQueue.isEmpty() && logs.size() < maxLogs) {
            BlockPos current = logQueue.poll();
            logs.add(current);

            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        BlockPos neighbor = current.offset(x, y, z);

                        if (Math.abs(neighbor.getX() - startPos.getX()) > maxHorizontalRadius ||
                                Math.abs(neighbor.getZ() - startPos.getZ()) > maxHorizontalRadius) continue;

                        if (!visitedLogs.contains(neighbor) && level.getBlockState(neighbor).is(BlockTags.LOGS)) {
                            visitedLogs.add(neighbor);
                            logQueue.add(neighbor);
                        }
                    }
                }
            }
        }
        return logs;
    }

    private static List<BlockPos> collectConnectedLeaves(Level level, List<BlockPos> logs, int maxLeafDistance) {
        Set<BlockPos> visited = new HashSet<>(logs);
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

            if (currentDist >= maxLeafDistance) continue;

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);

                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    BlockState state = level.getBlockState(neighbor);

                    if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)) {
                        leaves.add(neighbor);
                        leafDistance.put(neighbor, currentDist + 1);
                        leafQueue.add(neighbor);
                    }
                }
            }
        }
        return leaves;
    }

    /**
     * VI: Thực thi đào toàn bộ khối thân gỗ và kích hoạt phân rã lá tự động.
     * EN: Executes breaking all log blocks and initiates auto leaf decay.
     */
    public static void performTimberChop(ServerLevel level, Player player, ItemStack axe, TimberResult result) {
        debugLog("[Timber] Execution: Breaking {} logs and checking {} leaves...", result.logs().size(), result.leaves().size());
        int brokenLogsCount = 0;

        // 1. Phá thân gỗ
        for (BlockPos logPos : result.logs()) {
            if (axe.isEmpty() || (axe.isDamageableItem() && axe.getDamageValue() >= axe.getMaxDamage())) {
                break;
            }
            if (level.destroyBlock(logPos, true, player)) {
                brokenLogsCount++;

                ServerPlayer sp = player instanceof ServerPlayer ? (ServerPlayer) player : null;
                axe.hurtAndBreak(1, level, sp, (item) ->
                        player.onEquippedItemBroken(item, EquipmentSlot.MAINHAND));
            }
        }

        // 2. Ép game logic xử lý lá (Tối ưu hóa In-Memory)
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_AUTO_LEAVES_DECAY) {
            processAutoLeavesDecay(level, result.leaves(), SmallLogicTweaksConfig.ACTIVE_INSTANCE.DECAY_THRESHOLD);
        }

        debugLog("[Timber] Execution finished. Logs broken: {}", brokenLogsCount);
    }

    private static void processAutoLeavesDecay(ServerLevel level, List<BlockPos> leaves, int decayThreshold) {
        Map<BlockPos, Integer> virtualDistances = new HashMap<>();
        for (BlockPos leafPos : leaves) {
            BlockState state = level.getBlockState(leafPos);
            if (state.hasProperty(LeavesBlock.DISTANCE)) {
                virtualDistances.put(leafPos, state.getValue(LeavesBlock.DISTANCE));
            }
        }

        boolean changed;
        int loops = 0;

        // Bước A: Chạy thuật toán cập nhật khoảng cách hoàn toàn trên RAM
        do {
            changed = false;
            for (BlockPos leafPos : leaves) {
                BlockState state = level.getBlockState(leafPos);

                if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.DISTANCE) && !state.getValue(LeavesBlock.PERSISTENT)) {
                    int currentDist = virtualDistances.getOrDefault(leafPos, 7);
                    int minDistance = 7;

                    for (Direction dir : Direction.values()) {
                        BlockPos neighborPos = leafPos.relative(dir);
                        BlockState neighbor = level.getBlockState(neighborPos);

                        if (neighbor.is(BlockTags.LOGS)) {
                            minDistance = 1;
                            break;
                        } else if (neighbor.is(BlockTags.LEAVES) && neighbor.hasProperty(LeavesBlock.DISTANCE)) {
                            int neighborDist = virtualDistances.containsKey(neighborPos)
                                    ? virtualDistances.get(neighborPos)
                                    : neighbor.getValue(LeavesBlock.DISTANCE);

                            minDistance = Math.min(minDistance, neighborDist + 1);
                        }
                    }

                    if (currentDist != minDistance) {
                        virtualDistances.put(leafPos, minDistance);
                        changed = true;
                    }
                }
            }
            loops++;
        } while (changed && loops < 10);

        // Bước B: Phá vỡ hoặc cập nhật lại trạng thái thực tế
        for (BlockPos leafPos : leaves) {
            BlockState state = level.getBlockState(leafPos);

            if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.DISTANCE) && !state.getValue(LeavesBlock.PERSISTENT)) {
                int finalDistance = virtualDistances.getOrDefault(leafPos, 7);

                if (finalDistance >= decayThreshold) {
                    level.destroyBlock(leafPos, true);
                } else if (finalDistance != state.getValue(LeavesBlock.DISTANCE)) {
                    level.setBlock(leafPos, state.setValue(LeavesBlock.DISTANCE, finalDistance), 20);
                }
            }
        }
    }
}
