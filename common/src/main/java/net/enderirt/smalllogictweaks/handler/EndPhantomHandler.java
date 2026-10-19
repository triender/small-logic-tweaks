package net.enderirt.smalllogictweaks.handler;

import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VI: Bộ xử lý logic sinh quái Phantom tại chiều không gian The End khi người chơi mất ngủ.
 * EN: Logic handler for spawning Phantoms in the End dimension based on insomnia stats.
 */
public final class EndPhantomHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("small_logic_tweaks");

    private EndPhantomHandler() {}

    /**
     * VI: Thiết lập lại chỉ số thời gian từ lần nghỉ ngơi cuối cùng của người chơi khi rời khỏi The End.
     * EN: Resets player's time since last rest stat when leaving the End dimension.
     *
     * @param player VI: Người chơi mục tiêu / EN: Target player
     */
    public static void resetInsomnia(ServerPlayer player) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_END_PHANTOM) return;

        player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_DEBUG_LOGS) {
            LOGGER.info("[End-Phantom Debug] Reset Player stats: {}", player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)));
        }
    }

    /**
     * VI: Xử lý nhịp tick server trong chiều không gian The End để kiểm tra điều kiện sinh Phantom.
     * EN: Handles server tick in the End dimension to evaluate Phantom spawn conditions.
     *
     * @param level VI: Cấp độ The End của server / EN: Server End level
     */
    public static void handleEndTick(ServerLevel level) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_END_PHANTOM) return;
        if (level.dimension() != Level.END) return;
        if (!level.getGameRules().get(GameRules.SPAWN_PHANTOMS)) return;

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative()) continue;
            if ((level.getGameTime() + player.getId()) % SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_CHECK_COOLDOWN != 0) continue;

            executePhantomSpawnLogic(level, player);
        }
    }

    /**
     * VI: Thực thi thuật toán kiểm tra và sinh Phantom xung quanh người chơi thỏa mãn điều kiện mất ngủ.
     * EN: Executes evaluation and spawn logic for Phantoms around players meeting insomnia criteria.
     *
     * @param level  VI: Cấp độ server / EN: Server level
     * @param player VI: Người chơi kiểm tra / EN: Player to check
     */
    public static void executePhantomSpawnLogic(ServerLevel level, Player player) {
        int timeSinceRest = 0;
        if (player instanceof ServerPlayer sp) {
            var stats = sp.getStats();
            if (stats != null) {
                timeSinceRest = stats.getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
            }
        }
        executePhantomSpawnLogic(level, player, timeSinceRest, false);
    }

    /**
     * VI: Thực thi thuật toán kiểm tra và sinh Phantom với thời gian mất ngủ được chỉ định.
     * EN: Executes evaluation and spawn logic for Phantoms with specified insomnia ticks.
     *
     * @param level         VI: Cấp độ server / EN: Server level
     * @param player        VI: Người chơi kiểm tra / EN: Player to check
     * @param timeSinceRest VI: Thời gian mất ngủ (ticks) / EN: Time since rest in ticks
     * @param forceRollPass VI: True nếu ép tỉ lệ ngẫu nhiên luôn đạt (hỗ trợ kiểm thử) / EN: True to bypass RNG for testing
     */
    public static void executePhantomSpawnLogic(ServerLevel level, Player player, int timeSinceRest, boolean forceRollPass) {
        // 1. Kiểm tra cánh cứng Elytra
        boolean hasElytra = player.getInventory().hasAnyMatching(stack -> stack.is(Items.ELYTRA))
                || player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);

        int currentInsomniaThreshold = hasElytra
                ? SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_THRESHOLD_POST_ELYTRA
                : SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_THRESHOLD_PRE_ELYTRA;

        // 2. Logic Roll RNG và Spawn
        if (timeSinceRest > 0 && timeSinceRest >= currentInsomniaThreshold) {
            var random = level.getRandom();
            int rollValue = forceRollPass ? timeSinceRest : random.nextInt(timeSinceRest);

            if (rollValue >= currentInsomniaThreshold) {
                var nearbyPhantoms = level.getEntitiesOfClass(
                        Phantom.class,
                        player.getBoundingBox().inflate(128.0, 128.0, 128.0)
                );

                int localCap = SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_MOB_CAP;
                if (nearbyPhantoms.size() >= localCap) return;

                int minCount = Math.max(1, SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_MIN_COUNT);
                int maxCount = Math.max(minCount, SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_MAX_COUNT);
                int countRange = Math.max(1, (maxCount - minCount) + 1);

                // Tính số lượng cần sinh
                int desiredSpawnCount = minCount + random.nextInt(countRange);
                int phantomCount = Math.min(desiredSpawnCount, localCap - nearbyPhantoms.size());

                BlockPos playerPos = player.blockPosition();
                int minHeight = Math.max(0, SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_MIN_SPAWN_HEIGHT);
                int maxHeight = Math.max(minHeight, SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_MAX_SPAWN_HEIGHT);
                int heightRange = Math.max(1, (maxHeight - minHeight) + 1);

                for (int i = 0; i < phantomCount; i++) {
                    int randomHeight = minHeight + random.nextInt(heightRange);
                    BlockPos spawnPos = playerPos.above(randomHeight).offset(-10 + random.nextInt(21), 0, -10 + random.nextInt(21));

                    // Kiểm tra va chạm để sinh an toàn
                    boolean isValidSpawn = false;
                    for (int attempt = 0; attempt < 10; attempt++) {
                        AABB spawnBox = EntityTypes.PHANTOM
                                .getDimensions()
                                .makeBoundingBox(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);

                        if (level.noCollision(spawnBox)) {
                            isValidSpawn = true;
                            break;
                        }
                        spawnPos = spawnPos.above();
                    }

                    if (!isValidSpawn) continue;

                    Phantom phantom = EntityTypes.PHANTOM.create(level, EntitySpawnReason.NATURAL);
                    if (phantom != null) {
                        phantom.setPos(spawnPos.getX() + 0.5D, (double) spawnPos.getY(), spawnPos.getZ() + 0.5D);
                        phantom.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.NATURAL, null);
                        level.addFreshEntityWithPassengers(phantom);
                    }
                }
            }
        }
    }
}
