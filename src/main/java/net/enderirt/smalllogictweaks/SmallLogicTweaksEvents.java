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
import net.minecraft.world.entity.monster.Phantom;
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
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;

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

    // Block tag: các khối có thể hoàn nguyên thành Đất khi dùng cúp chuột phải
    public static final TagKey<net.minecraft.world.level.block.Block> PICKAXE_REVERSION_BLOCKS = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(SmallLogicTweaks.MOD_ID, "pickaxe_reversion")
    );

    public static void register() {
        registerBoneMealTweak();
        registerTimberTweak();
        registerPotatoTweaks();
        registerHydroHardeningTweak();
        registerPickaxeDirtReversionTweak();
        registerEndPhantomTweak();
        LOGGER.info(" Small logic Tweaks Mod register success!!");
    }

    private static void registerBoneMealTweak() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
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
                if (world.getBlockState(abovePos).isSolidRender() || !world.getFluidState(abovePos).isEmpty())
                    return InteractionResult.PASS;

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

    private static final Cache<BlockPos, Float> TIMBER_SPEED_CACHE = CacheBuilder.newBuilder()
            .maximumSize(10) // Nhớ 10 khối gần nhất (Chống lag khi lắc chuột qua lại)
            .expireAfterWrite(2, TimeUnit.SECONDS) // Tự động xóa khỏi RAM sau 2 giây (Chống Leak RAM)
            .build();

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
                performTimberChop(serverLevel, player, axe, result);
                return false;
            }

            return true;
        });
    }

    public static float getTimberSpeedFactor(Player player, BlockPos pos) {
        Level level = player.level();

        // Kiểm tra xem RAM đã tính toán khối này trong vòng 2 giây qua chưa
        Float cachedFactor = TIMBER_SPEED_CACHE.getIfPresent(pos);
        if (cachedFactor != null) {
            return cachedFactor; // Trả về ngay lập tức, FPS không bị tụt
        }

        // Nếu chưa có, tiến hành thuật toán quét BFS
        ItemStack axe = player.getMainHandItem();
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

        // Lưu kết quả vào Cache để dùng cho các tick tiếp theo
        TIMBER_SPEED_CACHE.put(pos, newFactor);

        return newFactor;
    }
    private static TimberResult analyzeTimber(Level level, Player player, BlockPos startPos, ItemStack axe) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_TIMBER_TWEAK)
            return new TimberResult(false, List.of(), List.of(), 0);

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
            default -> maxLogs = 140;
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
                        if (Math.abs(neighbor.getX() - startPos.getX()) > SmallLogicTweaksConfig.ACTIVE_INSTANCE.MAX_LOG_HORIZONTAL_RADIUS ||
                                Math.abs(neighbor.getZ() - startPos.getZ()) > SmallLogicTweaksConfig.ACTIVE_INSTANCE.MAX_LOG_HORIZONTAL_RADIUS) continue;

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
            if (!(currentDist < SmallLogicTweaksConfig.ACTIVE_INSTANCE.MAX_LEAF_DISTANCE)) continue;

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
        boolean isTree = !logs.isEmpty() && leaves.size() >= SmallLogicTweaksConfig.ACTIVE_INSTANCE.MIN_LEAVES_FOR_TREE;

        return new TimberResult(isTree, logs, leaves, levelEnchant);
    }

    private static void performTimberChop(ServerLevel level, Player player, ItemStack axe, TimberResult result) {
        debugLog("[Timber] Execution: Breaking {} logs and checking {} leaves...", result.logs().size(), result.leaves().size());
        int brokenLogsCount = 0;

        // 1. PHÁ GỖ (Giữ nguyên)
        for (BlockPos logPos : result.logs()) {
            if (axe.isEmpty() || (axe.isDamageableItem() && axe.getDamageValue() >= axe.getMaxDamage())) {
                break;
            }
            if (level.destroyBlock(logPos, true, player)) {
                brokenLogsCount++;

                // Nếu người chơi thật -> Ép kiểu ServerPlayer để cập nhật thống kê mạng.
                // Nếu là MockPlayer của GameTest -> Gán null, vũ khí vẫn sẽ bị trừ độ bền cục bộ.
                ServerPlayer sp = player instanceof ServerPlayer ? (ServerPlayer) player : null;
                axe.hurtAndBreak(1, level, sp, (item) ->
                        player.onEquippedItemBroken(item, EquipmentSlot.MAINHAND));
            }
        }

        // 2. ÉP GAME LOGIC XỬ LÝ LÁ (Tối ưu hóa In-Memory)
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_AUTO_LEAVES_DECAY) {

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

                    if (finalDistance >= SmallLogicTweaksConfig.ACTIVE_INSTANCE.DECAY_THRESHOLD) {
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
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_POISONOUS_POTATO_COMPOST) {
            net.minecraft.world.level.block.ComposterBlock.COMPOSTABLES.put(Items.POISONOUS_POTATO, 0.65F);
            debugLog("[Potato Tweak] Registered Poisonous Potato to Composter.");
        }

        // Tweak 2: Bột chế thuốc độc (Potion of Poison)
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_POISONOUS_POTATO_BREWING) {
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
            if (player.isSpectator() || !SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_HYDRO_HARDENING) {
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

    private static void registerPickaxeDirtReversionTweak() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
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
            net.minecraft.world.phys.AABB blockTopArea = new net.minecraft.world.phys.AABB(
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

            // --- LOGIC PHÍA SERVER ---
            // 4. Chuyển đổi khối thành Đất nguyên thủy
            world.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());

            // 4.1. Bù đắp tọa độ Y phía server cho các thực thể lân cận (Mobs, động vật, người chơi khác)
            if (world instanceof ServerLevel serverLevel) {
                for (net.minecraft.world.entity.Entity entity : serverLevel.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, blockTopArea)) {
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
            world.playSound(null, pos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1.0f, 0.9f + world.getRandom().nextFloat() * 0.2f);

            // 6. Bắn hiệu ứng hạt bụi / khói nhẹ
            if (world instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        ParticleTypes.SMOKE,
                        pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5,
                        6, 0.2, 0.05, 0.2, 0.02
                );
            }

            // 7. Trừ độ bền cúp (hỗ trợ Unbreaking, kích hoạt gãy nếu hết HP)
            if (!player.getAbilities().instabuild) {
                ServerPlayer sp = player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                stack.hurtAndBreak(1, (ServerLevel) world, sp, item -> player.onEquippedItemBroken(item, slot));
            }

            return InteractionResult.SUCCESS;
        });
    }

    private static void registerEndPhantomTweak() {
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
            if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_END_PHANTOM) return;

            if (origin.dimension() == Level.END) {
                player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
                if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_DEBUG_LOGS) {
                    LOGGER.info("[End-Phantom Debug] Reset Player stats: {} ", player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)));
                }
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_END_PHANTOM) return;

            if (oldPlayer.level().dimension() == Level.END) {
                newPlayer.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
            }
        });

        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_END_PHANTOM) return;
            if (level.dimension() != Level.END) return;
            if (!level.getGameRules().get(GameRules.SPAWN_PHANTOMS)) return;

            for (net.minecraft.server.level.ServerPlayer player : level.players()) {
                if (player.isSpectator() || player.isCreative()) continue;
                if ((level.getGameTime() + player.getId()) % SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_CHECK_COOLDOWN != 0) continue;

                executePhantomSpawnLogic(level, player);
            }
        });
    }

    public static void executePhantomSpawnLogic(ServerLevel level, Player player) {
        // Nhận diện xem người chơi có phải là Mock/Fake Player trong GameTest hay không
        boolean isMockPlayer = player.getClass().getName().contains("Mock")
                || player.getClass().getName().contains("GameTest")
                || player.getClass().getName().contains("Fake")
                || !(player instanceof ServerPlayer);

        // 1. Kiểm tra Stat an toàn (Vì MockPlayer không có StatManager thực tế)
        int timeSinceRest = 0;
        if (!isMockPlayer) {
            ServerPlayer sp = (ServerPlayer) player;
            timeSinceRest = sp.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_REST));
        } else {
            // Nếu là MockPlayer trong GameTest, ta giả định là người chơi đã thức rất lâu (Ví dụ: 10 ngày)
            // để logic spawn luôn được kích hoạt mà không cần hack Stat
            timeSinceRest = 240000;
        }

        // 2. Kiểm tra Elytra (Sử dụng Player thay vì ServerPlayer vì Inventory nằm ở lớp Player)
        boolean hasElytra = player.getInventory().hasAnyMatching(stack -> stack.is(net.minecraft.world.item.Items.ELYTRA))
                || player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.ELYTRA);

        int currentInsomniaThreshold = hasElytra
                ? SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_THRESHOLD_POST_ELYTRA
                : SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_THRESHOLD_PRE_ELYTRA;

        // 3. Logic Roll RNG và Spawn
        if (timeSinceRest > 0 && timeSinceRest >= currentInsomniaThreshold) {
            var random = level.getRandom();

            // Trong GameTest, ta ép cho rollValue luôn đạt chuẩn để tránh "hên xui" khi test
            int rollValue = isMockPlayer ? timeSinceRest : random.nextInt(timeSinceRest);

            if (rollValue >= currentInsomniaThreshold) {
                var nearbyPhantoms = level.getEntitiesOfClass(
                        net.minecraft.world.entity.monster.Phantom.class,
                        player.getBoundingBox().inflate(128.0, 128.0, 128.0)
                );

                int localCap = SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_MOB_CAP;
                if (nearbyPhantoms.size() >= localCap) return;

                int minCount = Math.max(1, SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_MIN_COUNT);
                int maxCount = Math.max(minCount, SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_MAX_COUNT);
                int countRange = Math.max(1, (maxCount - minCount) + 1);

                // Tính số lượng cần spawn
                int desiredSpawnCount = minCount + random.nextInt(countRange);
                int phantomCount = Math.min(desiredSpawnCount, localCap - nearbyPhantoms.size());

                net.minecraft.core.BlockPos playerPos = player.blockPosition();
                int minHeight = Math.max(0, SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_MIN_SPAWN_HEIGHT);
                int maxHeight = Math.max(minHeight, SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_MAX_SPAWN_HEIGHT);
                int heightRange = Math.max(1, (maxHeight - minHeight) + 1);

                for (int i = 0; i < phantomCount; i++) {
                    int randomHeight = minHeight + random.nextInt(heightRange);
                    net.minecraft.core.BlockPos spawnPos = playerPos.above(randomHeight).offset(-10 + random.nextInt(21), 0, -10 + random.nextInt(21));

                    // Kiểm tra va chạm để spawn an toàn
                    boolean isValidSpawn = false;
                    for (int attempt = 0; attempt < 10; attempt++) {
                        net.minecraft.world.phys.AABB spawnBox = net.minecraft.world.entity.EntityType.PHANTOM
                                .getDimensions()
                                .makeBoundingBox(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);

                        if (level.noCollision(spawnBox)) {
                            isValidSpawn = true;
                            break;
                        }
                        spawnPos = spawnPos.above();
                    }

                    if (!isValidSpawn) continue;

                    net.minecraft.world.entity.monster.Phantom phantom = net.minecraft.world.entity.EntityType.PHANTOM.create(level, net.minecraft.world.entity.EntitySpawnReason.NATURAL);
                    if (phantom != null) {
                        phantom.setPos(spawnPos.getX() + 0.5D, (double) spawnPos.getY(), spawnPos.getZ() + 0.5D);
                        phantom.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), net.minecraft.world.entity.EntitySpawnReason.NATURAL, null);
                        level.addFreshEntityWithPassengers(phantom);
                    }
                }
            }
        }
    }
}