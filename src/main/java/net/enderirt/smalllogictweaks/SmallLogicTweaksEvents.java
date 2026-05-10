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

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


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

    public record TimberResult(boolean shouldChop, List<BlockPos> logPositions, int level) {}

    // Lưu trữ kết quả phân tích của khối đang đào hiện tại
    private static final ThreadLocal<BlockPos> LAST_CHECKED_POS = new ThreadLocal<>();
    private static final ThreadLocal<Float> CACHED_FACTOR = ThreadLocal.withInitial(() -> 1.0f);

    public static boolean ENABLE_TIMBER_DEBUG_LOGS = false;

    // Hàm tiện ích để in Log
    private static void debugLog(String message, Object... args) {
        if (ENABLE_TIMBER_DEBUG_LOGS) {
            LOGGER.info(message, args);
        }
    }

    private static void registerTimberTweak() {
        // Đăng ký sự kiện TRƯỚC khi khối bị phá vỡ (Thực thi phá cây)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (!(world instanceof ServerLevel serverLevel) || player.isSpectator()) return true;

            ItemStack axe = player.getMainHandItem();

            if (state.is(BlockTags.LOGS)) {
                debugLog("[Timber] Player {} is breaking a log at {}", player.getName().getString(), pos.toShortString());
            }

            TimberResult result = analyzeTimber(serverLevel, player, pos, axe);

            if (result.shouldChop()) {
                debugLog("[Timber] Validation successful! Found {} logs to break.", result.logPositions().size());
                performTimberChop(serverLevel, (ServerPlayer) player, axe, result.logPositions());
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

        if (result.shouldChop() && !result.logPositions().isEmpty()) {
            float multiplier = switch (result.level()) {
                case 1 -> 1.0f;
                case 2 -> 0.9f;
                case 3 -> 0.8f;
                default -> 1.0f;
            };
            CACHED_FACTOR.set(result.logPositions().size() * multiplier);
        } else {
            CACHED_FACTOR.set(1.0f);
        }

        LAST_CHECKED_POS.set(pos.immutable());
        return CACHED_FACTOR.get();
    }

    private static TimberResult analyzeTimber(Level level, Player player, BlockPos startPos, ItemStack axe) {
        if (player.isShiftKeyDown() || !level.getBlockState(startPos).is(BlockTags.LOGS))
            return new TimberResult(false, List.of(), 0);

        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int levelEnchant = EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(Timber.TIMBER), axe);

        if (levelEnchant <= 0) {
            return new TimberResult(false, List.of(), 0);
        }

        debugLog("[Timber] Analyze started. Enchantment Level: {}", levelEnchant);

        int maxBlocks = switch (levelEnchant) {
            case 1 -> 16;
            case 2 -> 64;
            case 3 -> 128;
            default -> 0;
        };

        debugLog("[Timber] Level: {}, Max Capacity: {} blocks.", levelEnchant, maxBlocks);

        List<BlockPos> finalToBreak = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> currentLayer = new ArrayList<>();

        currentLayer.add(startPos);
        visited.add(startPos);

        int leafCount = 0;
        Set<BlockPos> countedLeaves = new HashSet<>();

        while (!currentLayer.isEmpty() && finalToBreak.size() < maxBlocks) {
            List<BlockPos> nextLayerWaiting = new ArrayList<>();

            for (int i = 0; i < currentLayer.size() && finalToBreak.size() < maxBlocks; i++) {
                BlockPos currentPos = currentLayer.get(i);
                finalToBreak.add(currentPos);

                if (leafCount < 5) {
                    for (BlockPos p : BlockPos.betweenClosed(currentPos.offset(-1, -1, -1), currentPos.offset(1, 1, 1))) {
                        if (!countedLeaves.contains(p)) {
                            BlockState s = level.getBlockState(p);
                            if (s.is(BlockTags.LEAVES) && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT)) {
                                countedLeaves.add(p.immutable());
                                leafCount++;
                                if (leafCount >= 5) {
                                    debugLog("[Timber] Sufficient natural leaves found (5+). Tree confirmed.");
                                    break;
                                }
                            }
                        }
                    }
                }

                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        for (int y = 0; y <= 1; y++) {
                            if (x == 0 && y == 0 && z == 0) continue;
                            BlockPos neighbor = currentPos.offset(x, y, z);

                            if (Math.abs(neighbor.getX() - startPos.getX()) > 5 || Math.abs(neighbor.getZ() - startPos.getZ()) > 5) continue;

                            if (!visited.contains(neighbor) && level.getBlockState(neighbor).is(BlockTags.LOGS)) {
                                visited.add(neighbor);
                                if (y == 0) currentLayer.add(neighbor);
                                else nextLayerWaiting.add(neighbor);
                            }
                        }
                    }
                }
            }
            currentLayer = nextLayerWaiting;
        }

        boolean isTree = leafCount >= 5;
        if (!isTree) {
            debugLog("[Timber] Chop cancelled: Insufficient natural leaves detected ({} found).", leafCount);
        }

        return new TimberResult(isTree && !finalToBreak.isEmpty(), finalToBreak, levelEnchant);
    }

    private static void performTimberChop(ServerLevel level, ServerPlayer player, ItemStack axe, List<BlockPos> positions) {
        debugLog("[Timber] Execution: Breaking {} blocks...", positions.size());
        int brokenCount = 0;

        for (BlockPos pos : positions) {
            if (axe.isEmpty() || (axe.isDamageableItem() && axe.getDamageValue() >= axe.getMaxDamage())) {
                debugLog("[Timber] Execution halted: Tool is broken or empty.");
                break;
            }

            if (level.destroyBlock(pos, true, player)) {
                brokenCount++;
                axe.hurtAndBreak(1, level, player, (item) ->
                        player.onEquippedItemBroken(item, EquipmentSlot.MAINHAND));
            }
        }
        debugLog("[Timber] Execution finished. Total blocks broken: {}", brokenCount);
    }
}