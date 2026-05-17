package net.enderirt.smalllogictweaks;

import net.enderirt.smalllogictweaks.ModEnchants.Timber;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.LeavesBlock;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class SmallLogicTweaksEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger("small_logic_tweaks");

    // Ánh xạ 16 màu của Bột Bê Tông sang Khối Bê Tông tương ứng
    public static final Map<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Block> POWDER_TO_CONCRETE = Map.ofEntries(
            Map.entry(net.minecraft.world.level.block.Blocks.WHITE_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.WHITE_CONCRETE),
            Map.entry(net.minecraft.world.level.block.Blocks.ORANGE_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.ORANGE_CONCRETE),
            Map.entry(net.minecraft.world.level.block.Blocks.MAGENTA_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.MAGENTA_CONCRETE),
            Map.entry(net.minecraft.world.level.block.Blocks.LIGHT_BLUE_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.LIGHT_BLUE_CONCRETE),
            Map.entry(net.minecraft.world.level.block.Blocks.YELLOW_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.YELLOW_CONCRETE),
            Map.entry(net.minecraft.world.level.block.Blocks.LIME_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.LIME_CONCRETE),
            Map.entry(net.minecraft.world.level.block.Blocks.PINK_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.PINK_CONCRETE),
            Map.entry(net.minecraft.world.level.block.Blocks.GRAY_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.GRAY_CONCRETE),
            Map.entry(net.minecraft.world.level.block.Blocks.LIGHT_GRAY_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.LIGHT_GRAY_CONCRETE),
            Map.entry(net.minecraft.world.level.block.Blocks.CYAN_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.CYAN_CONCRETE),
            Map.entry(net.minecraft.world.level.block.Blocks.PURPLE_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.PURPLE_CONCRETE),
            Map.entry(net.minecraft.world.level.block.Blocks.BLUE_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.BLUE_CONCRETE),
            Map.entry(net.minecraft.world.level.block.Blocks.BROWN_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.BROWN_CONCRETE),
            Map.entry(net.minecraft.world.level.block.Blocks.GREEN_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.GREEN_CONCRETE),
            Map.entry(net.minecraft.world.level.block.Blocks.RED_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.RED_CONCRETE),
            Map.entry(net.minecraft.world.level.block.Blocks.BLACK_CONCRETE_POWDER, net.minecraft.world.level.block.Blocks.BLACK_CONCRETE)
    );

    public static void register() {
        registerBoneMealTweak();
        registerTimberTweak();
        registerPotatoTweaks();
        registerHydroHardeningTweak();
        registerEndPhantomTweak();
        LOGGER.info(" Small logic Tweaks Mod register success!!");
    }

    private static void registerBoneMealTweak() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!SmallLogicTweaksConfig.INSTANCE.ENABLE_BONE_MEAL_TWEAK) return InteractionResult.PASS;
            if (player.isSpectator()) return InteractionResult.PASS;

            ItemStack stack = player.getItemInHand(hand);
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            // 1. Kiểm tra loại khối đất hợp lệ
            boolean isValidDirt = SmallLogicTweaksConfig.INSTANCE.ALLOW_ALL_DIRT_TYPES
                    ? state.is(BlockTags.DIRT)
                    : state.is(Blocks.DIRT);

            if (stack.is(Items.BONE_MEAL) && isValidDirt) {
                BlockPos abovePos = pos.above();

                // 2. Kiểm tra không gian trống phía trên
                if (world.getBlockState(abovePos).isSolidRender() || !world.getFluidState(abovePos).isEmpty())
                    return InteractionResult.PASS;

                BlockState newState = null;

                // 3. Xử lý logic khối nguồn lân cận
                if (SmallLogicTweaksConfig.INSTANCE.REQUIRE_NEIGHBOR_SOURCE) {
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
                            // Biome Nấm ưu tiên Khuẩn ty, nếu không có Khuẩn ty lân cận thì mới thành Cỏ
                            newState = hasMyceliumNeighbor ? Blocks.MYCELIUM.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                        } else {
                            // Các Biome thường ưu tiên Cỏ, nếu không có Cỏ lân cận thì mới thành Khuẩn ty
                            newState = hasGrassNeighbor ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.MYCELIUM.defaultBlockState();
                        }
                    }

                    // Nếu bật REQUIRE_NEIGHBOR_SOURCE nhưng xung quanh hoàn toàn không có cỏ/khuẩn ty -> Hủy bỏ
                    if (newState == null) return InteractionResult.PASS;
                } else {
                    // Nếu không yêu cầu khối nguồn, áp dụng logic Biome gốc của bạn
                    newState = world.getBiome(pos).is(Biomes.MUSHROOM_FIELDS)
                            ? Blocks.MYCELIUM.defaultBlockState()
                            : Blocks.GRASS_BLOCK.defaultBlockState();
                }

                // --- ĐIỂM CHỐT LÀM MƯỢT GAME (CLIENT-SIDE PREDICTION) ---
                // Phía Client thấy mọi điều kiện đã đủ thì trả về SUCCESS ngay để vung tay vón phân lập tức
                if (world.isClientSide()) return InteractionResult.SUCCESS;

                // --- LOGIC XỬ LÝ PHÍA SERVER ---
                world.setBlockAndUpdate(pos, newState);
                world.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);

                if (world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
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
                        if (Math.abs(neighbor.getX() - startPos.getX()) > SmallLogicTweaksConfig.INSTANCE.MAX_LOG_HORIZONTAL_RADIUS ||
                                Math.abs(neighbor.getZ() - startPos.getZ()) > SmallLogicTweaksConfig.INSTANCE.MAX_LOG_HORIZONTAL_RADIUS) continue;

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
            if (!(currentDist < SmallLogicTweaksConfig.INSTANCE.MAX_LEAF_DISTANCE)) continue;

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
        boolean isTree = !logs.isEmpty() && leaves.size() >= SmallLogicTweaksConfig.INSTANCE.MIN_LEAVES_FOR_TREE;

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
        if (SmallLogicTweaksConfig.INSTANCE.ENABLE_AUTO_LEAVES_DECAY) {

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

                    if (finalDistance >= SmallLogicTweaksConfig.INSTANCE.DECAY_THRESHOLD) {
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

    private static void registerPotatoTweaks() {
        // Tweak 1: Sử dụng với Thùng ủ phân (Composter)
        if (SmallLogicTweaksConfig.INSTANCE.ENABLE_POISONOUS_POTATO_COMPOST) {
            net.minecraft.world.level.block.ComposterBlock.COMPOSTABLES.put(Items.POISONOUS_POTATO, 0.65F);
            debugLog("[Potato Tweak] Registered Poisonous Potato to Composter.");
        }

        // Tweak 2: Bột chế thuốc độc (Potion of Poison)
        if (SmallLogicTweaksConfig.INSTANCE.ENABLE_POISONOUS_POTATO_BREWING) {
            FabricPotionBrewingBuilder.BUILD.register(builder -> {
                // Dùng phương thức addMix thông qua biến builder
                builder.addMix(Potions.AWKWARD, Items.POISONOUS_POTATO, Potions.POISON);
            });
            debugLog("[Potato Tweak] Registered Poisonous Potato brewing recipe via Fabric API.");
        }
    }

    public static boolean tryHardenConcrete(net.minecraft.world.level.Level world, net.minecraft.core.BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        net.minecraft.world.level.block.Block hardenedBlock = POWDER_TO_CONCRETE.get(state.getBlock());

        if (hardenedBlock != null) {
            world.setBlockAndUpdate(pos, hardenedBlock.defaultBlockState());
            if (world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                // Tạo hiệu ứng hạt khói/nước tại vị trí khối được hóa cứng
                serverLevel.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.SPLASH,
                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        5, 0.25, 0.25, 0.25, 0.05
                );
            }
            return true;
        }
        return false;
    }

    private static void registerHydroHardeningTweak() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            // Chỉ bỏ qua chế độ Khán giả và kiểm tra cấu hình ngay từ đầu
            if (player.isSpectator() || !SmallLogicTweaksConfig.INSTANCE.ENABLE_HYDRO_HARDENING) {
                return InteractionResult.PASS;
            }

            ItemStack stack = player.getItemInHand(hand);
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            // Xác minh vật phẩm trên tay là Thuốc (Potion)
            if (stack.is(Items.POTION)) {
                // Đọc Component dữ liệu để xem đây có phải là chai Nước cất (Water) không
                var potionContents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);

                if (potionContents != null && potionContents.is(net.minecraft.world.item.alchemy.Potions.WATER)) {

                    // Tra cứu xem khối đang tương tác có nằm trong từ điển Bột Bê Tông không
                    net.minecraft.world.level.block.Block hardenedBlock = POWDER_TO_CONCRETE.get(state.getBlock());

                    if (hardenedBlock != null) {
                        // ĐỒNG BỘ CLIENT-SIDE (QUAN TRỌNG):
                        // Trả về SUCCESS ở Client để chặn đứng hành vi mặc định (uống nước)
                        if (world.isClientSide()) {
                            return InteractionResult.SUCCESS;
                        }

                        // --- LOGIC PHÍA SERVER ---
                        // 1. Cập nhật khối thành Bê tông đặc
                        world.setBlockAndUpdate(pos, hardenedBlock.defaultBlockState());

                        // 2. Phát âm thanh kết hợp: Tiếng đổ nước và tiếng dọn chai
                        world.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.5f, 1.0f);
                        world.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);

                        // 3. Xử lý trả lại Chai Thủy Tinh rỗng (Nếu không phải chế độ Sáng tạo)
                        if (!player.getAbilities().instabuild) {
                            ItemStack glassBottle = new ItemStack(Items.GLASS_BOTTLE);
                            stack.shrink(1); // Giảm đi 1 bình nước

                            if (stack.isEmpty()) {
                                // Nếu trên tay chỉ có đúng 1 bình, thay trực tiếp bằng chai rỗng
                                player.setItemInHand(hand, glassBottle);
                            } else if (!player.getInventory().add(glassBottle)) {
                                // Nếu hành trang đầy, vứt chai rỗng xuống đất
                                player.drop(glassBottle, false);
                            }
                        }

                        debugLog("[Hydro-Hardening] Converted Concrete Powder at {}", pos.toShortString());
                        return InteractionResult.SUCCESS;
                    }
                }
            }
            return InteractionResult.PASS;
        });
    }

    private static void registerEndPhantomTweak() {
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
            if (!SmallLogicTweaksConfig.INSTANCE.ENABLE_END_PHANTOM) return;

            if (origin.dimension() == Level.END) {
                player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
                if (SmallLogicTweaksConfig.INSTANCE.ENABLE_DEBUG_LOGS) {
                    LOGGER.info("[End-Phantom Debug] Reset Player stats: {} ", player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)));
                }
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!SmallLogicTweaksConfig.INSTANCE.ENABLE_END_PHANTOM) return;

            if (oldPlayer.level().dimension() == Level.END) {
                newPlayer.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
            }
        });

        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (!SmallLogicTweaksConfig.INSTANCE.ENABLE_END_PHANTOM) return;
            if (level.dimension() != Level.END) return;

            if (!level.getGameRules().get(GameRules.SPAWN_PHANTOMS))
                return;

            var random = level.getRandom();

            for (net.minecraft.server.level.ServerPlayer player : level.players()) {
                if (player.isSpectator() || player.isCreative()) continue;

                if ((level.getGameTime() + player.getId()) % SmallLogicTweaksConfig.INSTANCE.PHANTOM_CHECK_COOLDOWN != 0) continue;

                int timeSinceRest = player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));

                boolean hasElytra = player.getInventory().hasAnyMatching(stack -> stack.is(net.minecraft.world.item.Items.ELYTRA))
                        || player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.ELYTRA);

                int currentInsomniaThreshold = hasElytra
                        ? SmallLogicTweaksConfig.INSTANCE.PHANTOM_THRESHOLD_POST_ELYTRA
                        : SmallLogicTweaksConfig.INSTANCE.PHANTOM_THRESHOLD_PRE_ELYTRA;

                if (SmallLogicTweaksConfig.INSTANCE.ENABLE_DEBUG_LOGS) {
                    LOGGER.info("[End-Phantom Debug] Checking {}: Insomnia = {}|{} ticks. HasElytra: {}",
                            player.getName().getString(), timeSinceRest, currentInsomniaThreshold, hasElytra);
                }

                if (timeSinceRest >= currentInsomniaThreshold) {
                    int rollValue = random.nextInt(timeSinceRest);

                    if (SmallLogicTweaksConfig.INSTANCE.ENABLE_DEBUG_LOGS) {
                        LOGGER.info("[End-Phantom Debug] Player {}. Roll value {}",
                                player.getName().getString(), rollValue);
                    }

                    if (rollValue >= currentInsomniaThreshold) {
                        int minCount = SmallLogicTweaksConfig.INSTANCE.PHANTOM_MIN_COUNT;
                        int maxCount = SmallLogicTweaksConfig.INSTANCE.PHANTOM_MAX_COUNT;
                        int phantomCount = minCount + random.nextInt((maxCount - minCount) + 1);

                        net.minecraft.core.BlockPos playerPos = player.blockPosition();

                        LOGGER.info("[End-Phantom] Player {} luckily have starting spawn {} Phantom.",
                                player.getName().getString(), phantomCount);

                        int minHeight = SmallLogicTweaksConfig.INSTANCE.PHANTOM_MIN_SPAWN_HEIGHT;
                        int maxHeight = SmallLogicTweaksConfig.INSTANCE.PHANTOM_MAX_SPAWN_HEIGHT;
                        int heightRange = (maxHeight - minHeight) + 1;

                        for (int i = 0; i < phantomCount; i++) {
                            int randomHeight = minHeight + random.nextInt(heightRange);
                            net.minecraft.core.BlockPos spawnPos = playerPos.above(randomHeight).offset(-10 + random.nextInt(21), 0, -10 + random.nextInt(21));

                            boolean isValidSpawn = false;
                            for (int attempt = 0; attempt < 10; attempt++) {
                                if (level.isEmptyBlock(spawnPos) && level.isEmptyBlock(spawnPos.above())) {
                                    isValidSpawn = true;
                                    break;
                                }
                                spawnPos = spawnPos.above();
                            }

                            if (!isValidSpawn) {
                                continue;
                            }

                            net.minecraft.world.entity.monster.Phantom phantom = net.minecraft.world.entity.EntityType.PHANTOM.create(level, net.minecraft.world.entity.EntitySpawnReason.NATURAL);

                            if (phantom != null) {
                                phantom.setPos(spawnPos.getX() + 0.5D, (double) spawnPos.getY(), spawnPos.getZ() + 0.5D);

                                phantom.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), net.minecraft.world.entity.EntitySpawnReason.NATURAL, null);
                                level.addFreshEntityWithPassengers(phantom);

                                LOGGER.info("[End-Phantom] Đã gọi sinh thành công Phantom thứ {} tại vị trí không gian: {}",
                                        i + 1, spawnPos.toShortString());
                            }
                        }
                    }
                }
            }
        });
    }
}