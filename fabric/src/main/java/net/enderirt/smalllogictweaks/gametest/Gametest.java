package net.enderirt.smalllogictweaks.gametest;

import net.enderirt.smalllogictweaks.ModEnchants.Timber;
import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.enderirt.smalllogictweaks.SmallLogicTweaksEvents;
import net.enderirt.smalllogictweaks.network.ConfigSyncPayload;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

public class Gametest {

    @GameTest(maxTicks = 100)
    public void testConcreteHardening(GameTestHelper helper) {
        BlockPos targetPos = new BlockPos(1, 2, 1);
        helper.setBlock(targetPos, Blocks.CONCRETE_POWDER.red());

        boolean hasHardened = SmallLogicTweaksEvents.tryHardenConcrete(helper.getLevel(), helper.absolutePos(targetPos));

        helper.assertTrue(hasHardened, "Hàm tryHardenConcrete phải trả về true");
        helper.assertBlockPresent(Blocks.CONCRETE.red(), targetPos);

        helper.succeed();
    }

    // Định nghĩa ID của các công thức cần kiểm tra dựa trên cấu trúc thư mục data của mod
    // Khởi tạo ResourceKey đúng chuẩn: Ghép nối loại Registry (RECIPE) và Identifier
    private static final ResourceKey<Recipe<?>> COAL_RECIPE_ID = ResourceKey.create(
            Registries.RECIPE,
            Identifier.fromNamespaceAndPath("small_logic_tweaks", "black_dye_from_coal")
    );

    private static final ResourceKey<Recipe<?>> CHARCOAL_RECIPE_ID = ResourceKey.create(
            Registries.RECIPE,
            Identifier.fromNamespaceAndPath("small_logic_tweaks", "black_dye_from_charcoal")
    );
    /**
     * Kiểm tra trạng thái nạp tài nguyên của hệ thống dựa trên cấu hình ACTIVE_INSTANCE hiện tại.
     * Vì môi trường GameTest khởi tạo Data Pack ngay từ lúc bật Server, bài test này sẽ xác thực
     * xem các tệp JSON có tuân thủ đúng bộ lọc điều kiện Fabric hay không.
     */
    @GameTest(maxTicks = 10)
    public void testResourcePackRecipeRegistry(GameTestHelper helper) {
        var recipeManager = helper.getLevel().recipeAccess();

        // 1. Xác thực trạng thái nạp của công thức Than đá (Coal)
        var coalRecipe = recipeManager.byKey(COAL_RECIPE_ID);
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_COAL_TO_BLACK_DYE) {
            helper.assertTrue(coalRecipe.isPresent(),
                    "Lỗi Registry: Đã bật ENABLE_COAL_TO_BLACK_DYE nhưng thiếu công thức Than đá.");
        } else {
            helper.assertTrue(coalRecipe.isEmpty(),
                    "Lỗi Registry: Đã tắt cấu hình nhưng công thức Than đá vẫn được nạp trái phép.");
        }

        // 2. Xác thực trạng thái nạp của công thức Than củi (Charcoal)
        var charcoalRecipe = recipeManager.byKey(CHARCOAL_RECIPE_ID);
        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_CHARCOAL_TO_BLACK_DYE) {
            helper.assertTrue(charcoalRecipe.isPresent(),
                    "Lỗi Registry: Đã bật ENABLE_CHARCOAL_TO_BLACK_DYE nhưng thiếu công thức Than củi.");
        } else {
            helper.assertTrue(charcoalRecipe.isEmpty(),
                    "Lỗi Registry: Đã tắt cấu hình nhưng công thức Than củi vẫn được nạp trái phép.");
        }

        helper.succeed();
    }

    @GameTest(maxTicks = 10)
    public void testPoisonousPotatoComposter(GameTestHelper helper) {
        // MC 26.3: ComposterBlock.COMPOSTABLES đã xóa, kiểm tra qua DataComponents.COMPOSTABLE và hành vi ủ thật
        var compostable = Items.POISONOUS_POTATO.components().get(DataComponents.COMPOSTABLE);
        var vanillaPotatoCompostable = Items.POTATO.components().get(DataComponents.COMPOSTABLE);
        boolean isRegistered = compostable != null;

        BlockPos composterPos = new BlockPos(1, 2, 1);
        helper.setBlock(composterPos, Blocks.COMPOSTER.defaultBlockState());
        BlockPos absPos = helper.absolutePos(composterPos);
        ServerLevel level = helper.getLevel();

        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_POISONOUS_POTATO_COMPOST) {
            // 1. Xác thực component tồn tại và khớp với chuẩn Khoai tây thường của vanilla (65% tăng 1 tầng)
            helper.assertTrue(isRegistered,
                    "Lỗi Registry: Cấu hình ENABLE_POISONOUS_POTATO_COMPOST đang bật nhưng vật phẩm không được đăng ký vào Thùng ủ phân.");
            helper.assertTrue(compostable.equals(vanillaPotatoCompostable),
                    "Lỗi Logic: DataComponents.COMPOSTABLE của Khoai tây độc phải khớp chuẩn Khoai tây vanilla ("
                            + vanillaPotatoCompostable + "), nhưng thực tế là: " + compostable);

            // 2. Xác thực hành vi thực tế: bỏ 1 củ vào thùng rỗng (LEVEL 0) chỉ lên đúng LEVEL 1 (không đầy ngay)
            ItemStack firstPotato = new ItemStack(Items.POISONOUS_POTATO);
            BlockState afterFirst = ComposterBlock.insertItem(null, helper.getBlockState(composterPos), level, firstPotato, absPos);
            int levelAfterFirst = afterFirst.getValue(ComposterBlock.LEVEL);
            helper.assertTrue(levelAfterFirst == 1,
                    "Lỗi Ủ phân: Bỏ 1 củ Khoai tây độc vào thùng rỗng phải lên đúng LEVEL 1, nhưng thực tế là LEVEL " + levelAfterFirst);
            helper.assertTrue(firstPotato.isEmpty(),
                    "Lỗi Ủ phân: Khoai tây độc không bị tiêu hao khi bỏ vào thùng ủ.");

            // 3. Từ LEVEL 1 trở đi, mỗi lần bỏ thêm chỉ được tăng 0 hoặc 1 tầng (tuyệt đối không nhảy cóc nhiều tầng)
            BlockState current = afterFirst;
            for (int i = 0; i < 6; i++) {
                int beforeLevel = current.getValue(ComposterBlock.LEVEL);
                if (beforeLevel >= 7) {
                    break;
                }
                current = ComposterBlock.insertItem(null, current, level, new ItemStack(Items.POISONOUS_POTATO), absPos);
                int delta = current.getValue(ComposterBlock.LEVEL) - beforeLevel;
                helper.assertTrue(delta == 0 || delta == 1,
                        "Lỗi Ủ phân: Mỗi lần bỏ Khoai tây độc chỉ được tăng 0 hoặc 1 tầng, nhưng đã tăng " + delta + " tầng.");
            }
        } else {
            // Xác thực quá trình tắt tính năng
            helper.assertFalse(isRegistered,
                    "Lỗi Registry: Cấu hình đã tắt nhưng Khoai tây độc vẫn bị nạp trái phép vào hệ thống ủ phân.");
            ItemStack potato = new ItemStack(Items.POISONOUS_POTATO);
            BlockState after = ComposterBlock.insertItem(null, helper.getBlockState(composterPos), level, potato, absPos);
            helper.assertTrue(after.getValue(ComposterBlock.LEVEL) == 0 && potato.getCount() == 1,
                    "Lỗi Ủ phân: Cấu hình đã tắt nhưng thùng ủ vẫn nhận Khoai tây độc.");
        }

        helper.succeed();
    }


    /**
     * Hàm tiện ích: Dựng một mô hình cây đạt chuẩn cấu hình để kiểm thử.
     * Cấu hình mặc định yêu cầu ít nhất 4 khối lá nối với thân cây để được nhận diện là một "cây".
     */
    private void buildValidTree(GameTestHelper helper, BlockPos basePos) {
        // Xây 3 khối thân gỗ
        helper.setBlock(basePos, Blocks.OAK_LOG);
        helper.setBlock(basePos.above(1), Blocks.OAK_LOG);
        helper.setBlock(basePos.above(2), Blocks.OAK_LOG);

        // Xây 5 khối lá xung quanh đỉnh để thỏa mãn MIN_LEAVES_FOR_TREE >= 4
        BlockPos topLog = basePos.above(2);
        helper.setBlock(topLog.above(), Blocks.OAK_LEAVES);
        helper.setBlock(topLog.north(), Blocks.OAK_LEAVES);
        helper.setBlock(topLog.south(), Blocks.OAK_LEAVES);
        helper.setBlock(topLog.east(), Blocks.OAK_LEAVES);
        helper.setBlock(topLog.west(), Blocks.OAK_LEAVES);
    }

    /**
     * Hàm tiện ích: Cấp cho người chơi ảo một chiếc rìu có phù phép Timber.
     */
    private void equipTimberAxe(GameTestHelper helper, Player player, int level) {
        ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
        var registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var timberEnchantment = registry.getOrThrow(Timber.TIMBER);

        axe.enchant(timberEnchantment, level);
        player.setItemInHand(InteractionHand.MAIN_HAND, axe);
    }

    /**
     * Bài Test 1: Kiểm thử thuật toán tính toán tốc độ đào.
     * Xác nhận hệ thống có trả về đúng hệ số cản lực dựa trên số lượng gỗ hay không.
     */
    @GameTest(maxTicks = 10)
    public void testTimberMiningSpeedFactor(GameTestHelper helper) {
        BlockPos basePos = new BlockPos(1, 2, 1);
        buildValidTree(helper, basePos);

        Player mockPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        equipTimberAxe(helper, mockPlayer, 1); // Cấp độ 1: Hệ số nhân (multiplier) là 1.0f

        BlockPos absolutePos = helper.absolutePos(basePos);

        // Gọi thẳng vào API tính toán tốc độ của mod
        float speedFactor = SmallLogicTweaksEvents.getTimberSpeedFactor(mockPlayer, absolutePos);

        // Cây có 3 khối gỗ, phù phép cấp 1 -> Lực cản cấu trúc kỳ vọng phải là 3.0f
        helper.assertTrue(speedFactor == 3.0f,
                "Lỗi tính toán tốc độ: Kỳ vọng lực cản là 3.0f cho 3 khối gỗ, nhưng kết quả trả về là " + speedFactor);

        helper.succeed();
    }

    /**
     * Bài Test 2: Kiểm thử quá trình đốn hạ toàn bộ cây.
     * Đảm bảo sự kiện phá khối chặn luồng Vanilla và kích hoạt thành công vòng lặp phá hủy cấu trúc liên kết.
     */
    @GameTest(maxTicks = 20)
    public void testTimberExecution(GameTestHelper helper) {
        BlockPos basePos = new BlockPos(2, 2, 2);
        buildValidTree(helper, basePos);

        Player mockPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        equipTimberAxe(helper, mockPlayer, 1);

        BlockPos absolutePos = helper.absolutePos(basePos);
        var state = helper.getBlockState(basePos);

        // Bắn sự kiện phá khối thông qua Fabric API (Mô phỏng chính xác việc người chơi đập vỡ khối gỗ dưới cùng)
        boolean isVanillaBreakAllowed = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                helper.getLevel(), mockPlayer, absolutePos, state, null
        );

        // 1. Nếu hệ thống Timber hoạt động, nó phải hủy bỏ (cancel) hành vi phá khối của Vanilla và trả về false
        helper.assertFalse(isVanillaBreakAllowed,
                "Lỗi Event: Hệ thống Timber không đánh chặn được sự kiện đập khối của Vanilla.");

        // 2. Xác nhận toàn bộ thân gỗ đã bị xóa bỏ (trở thành khối không khí)
        helper.assertTrue(helper.getBlockState(basePos).is(Blocks.AIR), "Khối gỗ gốc không bị phá hủy.");
        helper.assertTrue(helper.getBlockState(basePos.above(1)).is(Blocks.AIR), "Khối gỗ tầng 2 không bị phá hủy.");
        helper.assertTrue(helper.getBlockState(basePos.above(2)).is(Blocks.AIR), "Khối gỗ tầng 3 không bị phá hủy.");

        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_AUTO_LEAVES_DECAY) {
            helper.assertTrue(helper.getBlockState(basePos.above(3)).is(Blocks.AIR),
                    "Lỗi Logic Lá: Khối lá không tự động phân rã dù cấu hình ENABLE_AUTO_LEAVES_DECAY đang bật.");
        }

        helper.succeed();
    }

    /**
     * Bài Test 3: Kiểm thử cơ chế bảo vệ nhà cửa (Safety Check).
     * Đảm bảo hệ thống Timber KHÔNG kích hoạt nếu đập vào một cột gỗ lẻ loi không có lá cây nối kèm.
     */
    @GameTest(maxTicks = 10)
    public void testTimberSafetyCheckForBuildings(GameTestHelper helper) {
        BlockPos basePos = new BlockPos(1, 2, 1);

        // Chỉ xây gỗ, không xây lá (Mô phỏng cột nhà của người chơi)
        helper.setBlock(basePos, Blocks.OAK_LOG);
        helper.setBlock(basePos.above(1), Blocks.OAK_LOG);

        Player mockPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        equipTimberAxe(helper, mockPlayer, 1);

        BlockPos absolutePos = helper.absolutePos(basePos);
        var state = helper.getBlockState(basePos);

        boolean isVanillaBreakAllowed = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                helper.getLevel(), mockPlayer, absolutePos, state, null
        );

        // Do không có đủ lá (dưới MIN_LEAVES_FOR_TREE), Timber phải bỏ qua và cho phép Vanilla đập khối bình thường
        helper.assertTrue(isVanillaBreakAllowed,
                "Lỗi Safety Check: Timber đã kích hoạt sai trên một cấu trúc không có lá (cột nhà).");

        helper.succeed();
    }

    @GameTest(maxTicks = 10)
    public void testBoneMealOnDirt(GameTestHelper helper) {
        BlockPos dirtPos = new BlockPos(1, 2, 1);
        helper.setBlock(dirtPos, Blocks.DIRT);

        Player mockPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        mockPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL));

        BlockPos absolutePos = helper.absolutePos(dirtPos);
        BlockHitResult hitResult = new BlockHitResult(
                Vec3.atCenterOf(absolutePos), Direction.UP, absolutePos, false
        );

        // Bắn sự kiện mô phỏng hành vi nhấp chuột phải của người chơi
        InteractionResult result = UseBlockCallback.EVENT.invoker().interact(
                mockPlayer, helper.getLevel(), InteractionHand.MAIN_HAND, hitResult
        );

        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_BONE_MEAL_TWEAK) {
            // Nếu mod đang bật, hành vi này phải trả về SUCCESS để chặn Vanilla
            helper.assertTrue(result == InteractionResult.SUCCESS,
                    "Sự kiện Bột Xương thất bại: Không trả về SUCCESS khi tương tác với khối đất.");

            // Khối đất phải biến thành Khối cỏ (Do môi trường GameTest mặc định là Plains Biome)
            helper.assertBlockPresent(Blocks.GRASS_BLOCK, dirtPos);
        } else {
            // Nếu mod tắt, hành vi phải bị bỏ qua (PASS)
            helper.assertTrue(result == InteractionResult.PASS,
                    "Lỗi rò rỉ sự kiện: Mod đã tắt nhưng sự kiện Bột Xương vẫn chặn thao tác.");
        }

        helper.succeed();
    }

    @GameTest(maxTicks = 60)
    public void testSplashPotionIntersectionMath(GameTestHelper helper) {
        // 1. Tạo một mặt sàn Bột Bê Tông Đỏ 3x3
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.CONCRETE_POWDER.red());
            }
        }

        // Tọa độ tâm xác định là (1, 2, 1)
        BlockPos centerPos = new BlockPos(1, 2, 1);
        BlockPos absoluteCenter = helper.absolutePos(centerPos);

        // Khởi tạo bình Thuốc Ném Nước (Splash Potion of Water)
        ItemStack waterSplash = new ItemStack(Items.SPLASH_POTION);
        waterSplash.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(Potions.WATER));

        // 2. Sinh ra thực thể rơi tự do
        AbstractThrownPotion potionEntity = new AbstractThrownPotion(net.minecraft.world.entity.EntityTypes.SPLASH_POTION, helper.getLevel()) {
            @Override
            protected void onHitAsPotion(net.minecraft.server.level.ServerLevel level, ItemStack potionItem, HitResult hitResult) {
                // Để trống để bỏ qua hiệu ứng lan tỏa của Vanilla, chỉ tập trung kiểm thử Mixin của bạn
            }

            @Override
            protected net.minecraft.world.item.Item getDefaultItem() {
                // Fix lỗi NullPointerException: Phải trả về vật phẩm thực tế
                return Items.SPLASH_POTION;
            }
        };
        potionEntity.setItem(waterSplash);

        // THAY ĐỔI QUAN TRỌNG:
        // Đặt vị trí cực gần khối tâm (y + 1.2) và gia tốc đâm thẳng xuống (-2.0D)
        // để chai thuốc va chạm (Hit Result) ngay lập tức vào khối tâm điểm
        potionEntity.setPos(absoluteCenter.getX() + 0.5D, absoluteCenter.getY() + 1.2D, absoluteCenter.getZ() + 0.5D);
        potionEntity.setDeltaMovement(0, -2.0D, 0);

        helper.getLevel().addFreshEntity(potionEntity);

        // 3. Đợi thực thể chạm đất và kích hoạt vòng lặp đánh giá
        helper.succeedWhen(() -> {
            // Theo logic Mixin, khối bị ném trúng trực tiếp (Direct Hit) phải có tỷ lệ hóa cứng là 100%
            helper.assertTrue(helper.getBlockState(centerPos).is(Blocks.CONCRETE.red()),
                    "Thuật toán AABB lỗi: Khối tâm điểm (Direct Hit) không được hóa cứng 100%.");

            // Kiểm tra các khối xung quanh, do tỷ lệ lan truyền là 40% (0.4f), ít nhất phải có 1 khối bị hóa cứng
            int hardenedCount = 0;
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    if (helper.getBlockState(new BlockPos(x, 2, z)).is(Blocks.CONCRETE.red())) {
                        hardenedCount++;
                    }
                }
            }

            helper.assertTrue(hardenedCount >= 1,
                    "Thuật toán AABB lỗi: Không có khối Bê tông nào trong vùng 3x3x3 bị hóa cứng ngoài khối tâm.");
        });
    }

    @GameTest(maxTicks = 10)
    public void testSplashPotionSpreadProbability(GameTestHelper helper) {
        int iterations = 1000;
        int totalHardened = 0;
        // Mỗi lần va chạm có 8 khối lân cận xung quanh khối tâm điểm trên mặt phẳng 3x3
        int maxPossibleHardened = iterations * 8;

        BlockPos centerPos = new BlockPos(1, 2, 1);
        BlockPos absoluteCenter = helper.absolutePos(centerPos);

        ItemStack waterSplash = new ItemStack(Items.SPLASH_POTION);
        waterSplash.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));

        for (int i = 0; i < iterations; i++) {
            // 1. Khôi phục lại mảng Bột Bê Tông 3x3 cho vòng lặp mới
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.CONCRETE_POWDER.red());
                }
            }

            // 2. Khởi tạo một thực thể ảo mới
            AbstractThrownPotion dummyPotion = new AbstractThrownPotion(net.minecraft.world.entity.EntityTypes.SPLASH_POTION, helper.getLevel()) {
                @Override
                protected void onHitAsPotion(net.minecraft.server.level.ServerLevel level, ItemStack potionItem, net.minecraft.world.phys.HitResult hitResult) {}
                @Override
                protected net.minecraft.world.item.Item getDefaultItem() { return Items.SPLASH_POTION; }
            };
            dummyPotion.setItem(waterSplash);

            // Cấu hình vật lý ép va chạm tức thì
            dummyPotion.setPos(absoluteCenter.getX() + 0.5D, absoluteCenter.getY() + 1.2D, absoluteCenter.getZ() + 0.5D);
            dummyPotion.setDeltaMovement(0, -2.0D, 0);

            helper.getLevel().addFreshEntity(dummyPotion);

            // 3. Ép xung vật lý (Kích hoạt Raycast và gọi Mixin ngay trong mili-giây hiện tại)
            dummyPotion.tick();

            // 4. Kiểm kê số lượng khối bê tông đã được lan truyền
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    BlockPos currentPos = new BlockPos(x, 2, z);

                    // Bỏ qua khối tâm vì nó luôn đạt 100% hóa cứng, ta chỉ đếm xác suất lan truyền
                    if (!currentPos.equals(centerPos)) {
                        if (helper.getBlockState(currentPos).is(Blocks.CONCRETE.red())) {
                            totalHardened++;
                        }
                    }
                }
            }

            // 5. Quản lý rác: Xóa bỏ thực thể ngay lập tức để giải phóng RAM
            dummyPotion.discard();
        }

        // Tính toán tỷ lệ lan truyền thực tế
        double actualSpreadRate = (double) totalHardened / maxPossibleHardened;

        // Xác suất được cấu hình cứng trong Mixin là 40% (0.4f).
        // Cho phép dao động an toàn từ 37% đến 43% để tránh lỗi Flaky Test do hàm Random.
        helper.assertTrue(actualSpreadRate >= 0.37 && actualSpreadRate <= 0.43,
                "Lỗi Xác Suất (RNG): Tỷ lệ lan truyền kỳ vọng là ~40%, nhưng thực tế thu được là " + (actualSpreadRate * 100) + "%");

        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void testEndPhantomSpawnLogic(GameTestHelper helper) {
        // Ép cấu hình
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_END_PHANTOM = true;
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_MIN_COUNT = 2;
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_MOB_CAP = 8;

        // Tạo MockPlayer (Trả về Player)
        Player mockPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos playerSpawnPos = helper.absolutePos(new BlockPos(1, 2, 1));
        mockPlayer.setPos(playerSpawnPos.getX() + 0.5D, playerSpawnPos.getY(), playerSpawnPos.getZ() + 0.5D);

        // Gọi hàm logic với thời gian mất ngủ giả lập và ép vượt qua tỷ lệ roll
        SmallLogicTweaksEvents.executePhantomSpawnLogic(helper.getLevel(), mockPlayer, 240000, true);

        // Kiểm tra kết quả
        helper.succeedWhen(() -> {
            var spawnedPhantoms = helper.getLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.monster.Phantom.class,
                    helper.getBounds().inflate(12.0, 45.0, 12.0)
            );

            helper.assertTrue(!spawnedPhantoms.isEmpty(), "Không có Phantom nào được sinh ra.");
            helper.assertTrue(spawnedPhantoms.size() >= 2, "Số lượng Phantom sinh ra ít hơn mức MIN_COUNT.");
        });
    }

    @GameTest(maxTicks = 100)
    public void testHydroHardeningDirectClick(GameTestHelper helper) {
        BlockPos targetPos = new BlockPos(1, 2, 1);
        helper.setBlock(targetPos, Blocks.CONCRETE_POWDER.red());

        Player mockPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack waterPotion = new ItemStack(Items.POTION);
        waterPotion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(Potions.WATER));
        mockPlayer.setItemInHand(InteractionHand.MAIN_HAND, waterPotion);

        BlockPos absolutePos = helper.absolutePos(targetPos);
        BlockHitResult hitResult = new BlockHitResult(
                Vec3.atCenterOf(absolutePos), Direction.UP, absolutePos, false
        );

        // Kích hoạt sự kiện tương tác chuột phải
        InteractionResult result = UseBlockCallback.EVENT.invoker().interact(
                mockPlayer, helper.getLevel(), InteractionHand.MAIN_HAND, hitResult
        );

        helper.assertTrue(result == InteractionResult.SUCCESS, "Lỗi tương tác: Click chai nước không trả về SUCCESS.");
        helper.assertBlockPresent(Blocks.CONCRETE.red(), targetPos);
        helper.assertTrue(mockPlayer.getItemInHand(InteractionHand.MAIN_HAND).is(Items.GLASS_BOTTLE), "Lỗi: Không trả lại chai thủy tinh rỗng.");

        helper.succeed();
    }

    @GameTest(maxTicks = 10)
    public void testTimberSneakBehavior(GameTestHelper helper) {
        BlockPos basePos = new BlockPos(1, 2, 1);
        buildValidTree(helper, basePos);

        Player mockPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        equipTimberAxe(helper, mockPlayer, 1);
        mockPlayer.setShiftKeyDown(true); // Đè Shift

        BlockPos absolutePos = helper.absolutePos(basePos);
        var state = helper.getBlockState(basePos);

        boolean isVanillaBreakAllowed = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                helper.getLevel(), mockPlayer, absolutePos, state, null
        );

        helper.assertTrue(isVanillaBreakAllowed, "Lỗi Shift-Sneak: Timber vẫn chặn đập khối khi đè Shift.");
        helper.assertTrue(helper.getBlockState(basePos.above(1)).is(Blocks.OAK_LOG), "Lỗi: Khối gỗ phía trên bị phá hủy trái phép.");

        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void testTimberDurabilityLoss(GameTestHelper helper) {
        BlockPos basePos = new BlockPos(1, 2, 1);
        buildValidTree(helper, basePos); // Tạo cây có 3 khối gỗ

        Player mockPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        equipTimberAxe(helper, mockPlayer, 1);
        
        ItemStack axe = mockPlayer.getMainHandItem();
        int initialDamage = axe.getDamageValue();

        BlockPos absolutePos = helper.absolutePos(basePos);
        var state = helper.getBlockState(basePos);

        // Chặt cây
        PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                helper.getLevel(), mockPlayer, absolutePos, state, null
        );

        int finalDamage = axe.getDamageValue();
        helper.assertTrue(finalDamage - initialDamage == 3, 
                "Lỗi độ bền: Rìu phải mất 3 độ bền khi chặt 3 khối gỗ, thực tế mất: " + (finalDamage - initialDamage));

        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void testTimberLevelsLimits(GameTestHelper helper) {
        BlockPos basePos = new BlockPos(1, 2, 1);
        
        // Xây cột gỗ cao 20 khối liên tục
        for (int i = 0; i < 20; i++) {
            helper.setBlock(basePos.above(i), Blocks.OAK_LOG);
        }
        // Thêm lá xung quanh khối gỗ ở giữa (above(10)) để rìu cấp 1 quét trúng lá mà không làm đứt cột gỗ
        BlockPos middleLog = basePos.above(10);
        helper.setBlock(middleLog.north(), Blocks.OAK_LEAVES);
        helper.setBlock(middleLog.south(), Blocks.OAK_LEAVES);
        helper.setBlock(middleLog.east(), Blocks.OAK_LEAVES);
        helper.setBlock(middleLog.west(), Blocks.OAK_LEAVES);
        helper.setBlock(middleLog.north().east(), Blocks.OAK_LEAVES);

        Player mockPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        equipTimberAxe(helper, mockPlayer, 1); // Rìu Timber cấp 1 (Giới hạn tối đa 16 block gỗ)

        BlockPos absolutePos = helper.absolutePos(basePos);
        var state = helper.getBlockState(basePos);

        // Tiến hành chặt
        PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                helper.getLevel(), mockPlayer, absolutePos, state, null
        );

        // Khối gỗ thứ 15 (index 15, tức above(15)) phải bị biến mất (thuộc 16 khối đầu tiên tính cả gốc)
        helper.assertTrue(helper.getBlockState(basePos.above(15)).is(Blocks.AIR), "Khối thứ 16 phải bị phá hủy.");
        // Khối gỗ thứ 16 (index 16, tức above(16)) phải vẫn còn nguyên (do vượt quá giới hạn 16 block gỗ của cấp 1)
        helper.assertTrue(helper.getBlockState(basePos.above(16)).is(Blocks.OAK_LOG), "Khối thứ 17 đáng lẽ không được phép phá hủy.");

        helper.succeed();
    }

    @GameTest(maxTicks = 10)
    public void testPoisonousPotatoBrewingRecipe(GameTestHelper helper) {
        // MC 26.3: potionBrewing() đã bị xóa khỏi ServerLevel.
        // Brewing là data-driven recipe — kiểm tra qua RecipeManager với RecipeType.BREWING.
        var recipeManager = helper.getLevel().getServer().getRecipeManager();
        var allBrewingRecipes = recipeManager.getRecipes().stream()
                .filter(rh -> rh.value() instanceof net.minecraft.world.item.crafting.BrewingRecipe)
                .map(rh -> (net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.BrewingRecipe>) rh)
                .toList();

        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_POISONOUS_POTATO_BREWING) {
            // Tìm recipe dùng POISONOUS_POTATO làm reagent + AWKWARD làm input → POISON
            ItemStack awkwardPotion = new ItemStack(Items.POTION);
            awkwardPotion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.AWKWARD));
            var brewInput = new net.minecraft.world.item.crafting.BrewingInput(
                    awkwardPotion, new ItemStack(Items.POISONOUS_POTATO));

            var matchedHolder = allBrewingRecipes.stream()
                    .filter(rh -> rh.value().matches(brewInput, helper.getLevel()))
                    .findFirst();

            helper.assertTrue(matchedHolder.isPresent(),
                    "Lỗi công thức nấu: Không tìm thấy BrewingRecipe (POISONOUS_POTATO + AWKWARD → POISON) trong RecipeManager.");

            ItemStack result = matchedHolder.get().value().assemble(brewInput);
            var outContents = result.get(DataComponents.POTION_CONTENTS);
            helper.assertTrue(result.is(Items.POTION) && outContents != null && outContents.is(Potions.POISON),
                    "Lỗi kết quả nấu: Công thức Khoai tây độc phải tạo ra thuốc Độc (POISON), nhưng kết quả là: " + result);

            // Kiểm tra âm: Bình nước thường không được khớp công thức Awkward -> Poison
            ItemStack waterPotion = new ItemStack(Items.POTION);
            waterPotion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
            var wrongInput = new net.minecraft.world.item.crafting.BrewingInput(
                    waterPotion, new ItemStack(Items.POISONOUS_POTATO));
            helper.assertFalse(matchedHolder.get().value().matches(wrongInput, helper.getLevel()),
                    "Lỗi công thức nấu: Công thức Awkward + Poisonous Potato không được khớp với Water Bottle.");
        } else {
            // Khi tính năng tắt, recipe JSON không được nạp vào RecipeManager
            boolean foundRecipe = allBrewingRecipes.stream().anyMatch(rh -> {
                var recipe = rh.value();
                ItemStack awkwardPotion = new ItemStack(Items.POTION);
                awkwardPotion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                        new net.minecraft.world.item.alchemy.PotionContents(Potions.AWKWARD));
                var brewInput = new net.minecraft.world.item.crafting.BrewingInput(
                        awkwardPotion, new ItemStack(Items.POISONOUS_POTATO));
                return recipe.matches(brewInput, helper.getLevel());
            });
            helper.assertFalse(foundRecipe, "Lỗi công thức nấu: Cấu hình tắt nhưng BrewingRecipe vẫn tồn tại.");
            helper.succeed();
            return;
        }

        helper.succeed();
    }


    @GameTest(maxTicks = 100)
    public void testEndPhantomElytraThreshold(GameTestHelper helper) {
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_END_PHANTOM = true;
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_MIN_COUNT = 1;
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_MOB_CAP = 5;
        
        int originalPre = SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_THRESHOLD_PRE_ELYTRA;
        int originalPost = SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_THRESHOLD_POST_ELYTRA;
        
        try {
            // Cấu hình ngưỡng cao hơn 240k (mức giả lập của mock player) để không spawn khi không có Elytra
            SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_THRESHOLD_PRE_ELYTRA = 300000;
            // Cấu hình ngưỡng thấp hơn 240k để kích hoạt spawn khi có Elytra
            SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_THRESHOLD_POST_ELYTRA = 72000;
            
            // Tạo MockPlayer nằm trong phòng test hiện tại
            Player mockPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockPos playerSpawnPos = helper.absolutePos(new BlockPos(1, 2, 1));
            mockPlayer.setPos(playerSpawnPos.getX() + 0.5D, playerSpawnPos.getY(), playerSpawnPos.getZ() + 0.5D);
            
            // Dọn dẹp các Phantom cũ xung quanh để tránh làm tràn giới hạn (Mob Cap)
            for (var phantom : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Phantom.class, helper.getBounds().inflate(12.0, 45.0, 12.0))) {
                phantom.discard();
            }
            
            // 1. Không mang Elytra -> Không spawn (240k < 300k)
            SmallLogicTweaksEvents.executePhantomSpawnLogic(helper.getLevel(), mockPlayer, 240000, true);
            var phantomsBefore = helper.getLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.monster.Phantom.class,
                    helper.getBounds().inflate(12.0, 45.0, 12.0)
            );
            helper.assertTrue(phantomsBefore.isEmpty(), "Lỗi: Không được sinh Phantom khi chưa mang Elytra (240k < 300k).");
            
            // 2. Mang Elytra ở slot Chest -> Có spawn (240k >= 72k)
            mockPlayer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            SmallLogicTweaksEvents.executePhantomSpawnLogic(helper.getLevel(), mockPlayer, 240000, true);
            
            helper.succeedWhen(() -> {
                var phantomsAfter = helper.getLevel().getEntitiesOfClass(
                        net.minecraft.world.entity.monster.Phantom.class,
                        helper.getBounds().inflate(12.0, 45.0, 12.0)
                );
                helper.assertTrue(!phantomsAfter.isEmpty(), "Lỗi: Phải sinh Phantom khi mang Elytra (240k >= 72k).");
            });
        } finally {
            // Khôi phục cấu hình mặc định
            SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_THRESHOLD_PRE_ELYTRA = originalPre;
            SmallLogicTweaksConfig.ACTIVE_INSTANCE.PHANTOM_THRESHOLD_POST_ELYTRA = originalPost;
        }
    }

    @GameTest(maxTicks = 10)
    public void testEndPhantomStatReset(GameTestHelper helper) {
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_END_PHANTOM = true;
        
        // Khởi tạo ServerPlayer thật sự bằng constructor để tránh lỗi ép kiểu từ MockPlayer
        com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "FakePlayer");
        ServerPlayer serverPlayer = new ServerPlayer(
            helper.getLevel().getServer(), 
            helper.getLevel(), 
            profile, 
            net.minecraft.server.level.ClientInformation.createDefault()
        );
        
        // 1. Giả lập tích lũy thời gian mất ngủ trực tiếp vào Stats Tracker bộ nhớ
        serverPlayer.getStats().setValue(serverPlayer, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 10000);
        int statVal = serverPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        helper.assertTrue(statVal == 10000, "Lỗi gán stat TIME_SINCE_REST.");
        
        // 2. Đổi chiều không gian (từ End sang Overworld)
        ServerLevel endLevel = helper.getLevel().getServer().getLevel(Level.END);
        ServerLevel overworldLevel = helper.getLevel().getServer().getLevel(Level.OVERWORLD);
        
        if (endLevel != null && overworldLevel != null) {
            ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.invoker().afterChangeLevel(serverPlayer, endLevel, overworldLevel);
            int finalStatVal = serverPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
            helper.assertTrue(finalStatVal == 0, "Lỗi: Stat TIME_SINCE_REST không bị reset sau khi rời The End.");
        }
        
        // 3. Giả lập hồi sinh (AFTER_RESPAWN)
        serverPlayer.getStats().setValue(serverPlayer, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 5000);
        
        com.mojang.authlib.GameProfile newProfile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "FakePlayer2");
        ServerPlayer newServerPlayer = new ServerPlayer(
            helper.getLevel().getServer(), 
            helper.getLevel(), 
            newProfile, 
            net.minecraft.server.level.ClientInformation.createDefault()
        );
        
        ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(serverPlayer, newServerPlayer, false);
        int respawnStatVal = newServerPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        helper.assertTrue(respawnStatVal == 0, "Lỗi: Stat TIME_SINCE_REST không bị reset sau khi hồi sinh.");
        
        helper.succeed();
    }
    @GameTest(maxTicks = 350)
    public void testDryRoastingOnCoveredStove(GameTestHelper helper) {
        helper.getLevel().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, helper.getLevel().getServer());
        
        BlockPos magmaPos = new BlockPos(1, 1, 1);
        BlockPos trapdoorPos = magmaPos.above();
        
        helper.setBlock(magmaPos, Blocks.MAGMA_BLOCK);
        helper.setBlock(trapdoorPos, Blocks.IRON_TRAPDOOR);
        
        BlockPos absTrapdoorPos = helper.absolutePos(trapdoorPos);
        net.minecraft.world.entity.item.ItemEntity potato = new net.minecraft.world.entity.item.ItemEntity(
            helper.getLevel(),
            absTrapdoorPos.getX() + 0.5,
            absTrapdoorPos.getY() + 0.2,
            absTrapdoorPos.getZ() + 0.5,
            new ItemStack(Items.POTATO, 1)
        );
        potato.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(potato);
        
        helper.succeedWhen(() -> {
            helper.assertTrue(potato.getItem().is(Items.BAKED_POTATO), "Potato should turn into Baked Potato");
        });
    }

    @GameTest(maxTicks = 350)
    public void testStoveReservationBlock(GameTestHelper helper) {
        helper.getLevel().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, helper.getLevel().getServer());
        
        BlockPos magmaPos = new BlockPos(1, 1, 1);
        BlockPos trapdoorPos = magmaPos.above();
        
        helper.setBlock(magmaPos, Blocks.MAGMA_BLOCK);
        helper.setBlock(trapdoorPos, Blocks.IRON_TRAPDOOR);
        
        BlockPos absTrapdoorPos = helper.absolutePos(trapdoorPos);
        
        net.minecraft.world.entity.item.ItemEntity potato1 = new net.minecraft.world.entity.item.ItemEntity(
            helper.getLevel(),
            absTrapdoorPos.getX() + 0.5,
            absTrapdoorPos.getY() + 0.2,
            absTrapdoorPos.getZ() + 0.5,
            new ItemStack(Items.POTATO, 1)
        );
        potato1.setDeltaMovement(0, 0, 0);
        net.minecraft.world.entity.item.ItemEntity potato2 = new net.minecraft.world.entity.item.ItemEntity(
            helper.getLevel(),
            absTrapdoorPos.getX() + 0.5,
            absTrapdoorPos.getY() + 0.2,
            absTrapdoorPos.getZ() + 0.5,
            new ItemStack(Items.POTATO, 1)
        );
        potato2.setDeltaMovement(0, 0, 0);
        
        helper.getLevel().addFreshEntity(potato1);
        helper.getLevel().addFreshEntity(potato2);
        
        helper.succeedWhen(() -> {
            boolean isOneCooked = potato1.getItem().is(Items.BAKED_POTATO) || potato2.getItem().is(Items.BAKED_POTATO);
            boolean isBothCooked = potato1.getItem().is(Items.BAKED_POTATO) && potato2.getItem().is(Items.BAKED_POTATO);
            
            helper.assertTrue(isOneCooked, "At least one potato should be cooked");
            helper.assertFalse(isBothCooked, "Both potatoes should NOT be cooked because of stove reservation cache");
        });
    }

    @GameTest(maxTicks = 550)
    public void testCauldronSequentialBoiling(GameTestHelper helper) {
        helper.getLevel().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, helper.getLevel().getServer());
        
        BlockPos lavaPos = new BlockPos(1, 1, 1);
        BlockPos cauldronPos = lavaPos.above();
        
        helper.setBlock(lavaPos, Blocks.LAVA);
        helper.setBlock(cauldronPos, Blocks.WATER_CAULDRON);
        
        BlockPos absCauldronPos = helper.absolutePos(cauldronPos);
        
        net.minecraft.world.entity.item.ItemEntity beefStack = new net.minecraft.world.entity.item.ItemEntity(
            helper.getLevel(),
            absCauldronPos.getX() + 0.5,
            absCauldronPos.getY() + 0.5,
            absCauldronPos.getZ() + 0.5,
            new ItemStack(Items.BEEF, 5)
        );
        helper.getLevel().addFreshEntity(beefStack);
        
        helper.succeedWhen(() -> {
            int rawCount = beefStack.getItem().getCount();
            var cookedBeefs = helper.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(absCauldronPos).inflate(2.0),
                entity -> entity.getItem().is(Items.COOKED_BEEF)
            );
            
            helper.assertTrue(rawCount < 5, "Beef stack should decrease in size");
            helper.assertTrue(!cookedBeefs.isEmpty(), "Cooked beef should be spawned");
        });
    }

    @GameTest(maxTicks = 350)
    public void testCarpetHeatReduction(GameTestHelper helper) {
        helper.getLevel().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, helper.getLevel().getServer());
        
        BlockPos magmaPos = new BlockPos(1, 1, 1);
        BlockPos carpetPos = magmaPos.above();
        
        helper.setBlock(magmaPos, Blocks.MAGMA_BLOCK);
        helper.setBlock(carpetPos, (net.minecraft.world.level.block.Block) Blocks.CARPET.white());
        
        BlockPos absCarpetPos = helper.absolutePos(carpetPos);
        
        net.minecraft.world.entity.item.ItemEntity kelp = new net.minecraft.world.entity.item.ItemEntity(
            helper.getLevel(),
            absCarpetPos.getX() + 0.5,
            absCarpetPos.getY() + 0.2,
            absCarpetPos.getZ() + 0.5,
            new ItemStack(Items.KELP, 1)
        );
        kelp.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(kelp);
        
        helper.succeedWhen(() -> {
            long age = kelp.tickCount;
            if (age < 135) {
                helper.assertTrue(kelp.getItem().is(Items.KELP), "Kelp should still be raw before 135 ticks due to carpet insulation");
            }
            helper.assertTrue(kelp.getItem().is(Items.DRIED_KELP) || age < 135, "Kelp should eventually dry");
            if (kelp.getItem().is(Items.DRIED_KELP)) {
                helper.succeed();
            }
        });
    }

    @GameTest(maxTicks = 100)
    public void testHeatDecayWhenMoved(GameTestHelper helper) {
        helper.getLevel().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, helper.getLevel().getServer());
        
        BlockPos magmaPos = new BlockPos(1, 1, 1);
        BlockPos trapdoorPos = magmaPos.above();
        
        helper.setBlock(magmaPos, Blocks.MAGMA_BLOCK);
        helper.setBlock(trapdoorPos, Blocks.IRON_TRAPDOOR);
        
        BlockPos absTrapdoorPos = helper.absolutePos(trapdoorPos);
        
        net.minecraft.world.entity.item.ItemEntity potato = new net.minecraft.world.entity.item.ItemEntity(
            helper.getLevel(),
            absTrapdoorPos.getX() + 0.5,
            absTrapdoorPos.getY() + 0.2,
            absTrapdoorPos.getZ() + 0.5,
            new ItemStack(Items.POTATO, 1)
        );
        potato.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(potato);
        
        final int[] heatAt30 = new int[1];
        helper.runAtTickTime(30, () -> {
            try {
                var field = net.minecraft.world.entity.item.ItemEntity.class.getDeclaredField("slt$magmaCookTimer");
                field.setAccessible(true);
                heatAt30[0] = (int) field.get(potato);
                helper.assertTrue(heatAt30[0] > 0, "Cook timer should have increased after 30 ticks near magma");
                
                // Stay inside the test area: moving the item far away could drop it into a chunk that is not
                // entity-ticking, freezing the timer instead of letting it decay (flaky failure with heat == initial)
                potato.setPos(absTrapdoorPos.getX() + 3.5, absTrapdoorPos.getY() + 0.2, absTrapdoorPos.getZ() + 0.5);
            } catch (Exception e) {
                helper.fail("Reflection error: " + e.getMessage());
            }
        });
        
        helper.runAtTickTime(70, () -> {
            try {
                var field = net.minecraft.world.entity.item.ItemEntity.class.getDeclaredField("slt$magmaCookTimer");
                field.setAccessible(true);
                int heat = (int) field.get(potato);
                helper.assertTrue(heat < heatAt30[0], "Cook timer should have decayed after moving away from heat source. heat=" + heat + ", initial=" + heatAt30[0]);
                helper.succeed();
            } catch (Exception e) {
                helper.fail("Reflection error: " + e.getMessage());
            }
        });
    }

    @GameTest(maxTicks = 350)
    public void testDryRoastingOnBareMagma(GameTestHelper helper) {
        helper.getLevel().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, helper.getLevel().getServer());
        
        BlockPos magmaPos = new BlockPos(1, 1, 1);
        helper.setBlock(magmaPos, Blocks.MAGMA_BLOCK);
        
        BlockPos absMagmaPos = helper.absolutePos(magmaPos);
        net.minecraft.world.entity.item.ItemEntity potato = new net.minecraft.world.entity.item.ItemEntity(
            helper.getLevel(),
            absMagmaPos.getX() + 0.5,
            absMagmaPos.getY() + 1.2,
            absMagmaPos.getZ() + 0.5,
            new ItemStack(Items.POTATO, 1)
        );
        potato.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(potato);
        
        helper.succeedWhen(() -> {
            helper.assertTrue(potato.getItem().is(Items.BAKED_POTATO), "Potato directly on magma should cook");
        });
    }

    @GameTest(maxTicks = 350)
    public void testNonCookableItemNotRoasting(GameTestHelper helper) {
        helper.getLevel().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, helper.getLevel().getServer());
        
        BlockPos magmaPos = new BlockPos(1, 1, 1);
        helper.setBlock(magmaPos, Blocks.MAGMA_BLOCK);
        
        BlockPos absMagmaPos = helper.absolutePos(magmaPos);
        net.minecraft.world.entity.item.ItemEntity dirt = new net.minecraft.world.entity.item.ItemEntity(
            helper.getLevel(),
            absMagmaPos.getX() + 0.5,
            absMagmaPos.getY() + 1.2,
            absMagmaPos.getZ() + 0.5,
            new ItemStack(Items.DIRT, 1)
        );
        dirt.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(dirt);
        
        helper.runAtTickTime(100, () -> {
            try {
                var field = net.minecraft.world.entity.item.ItemEntity.class.getDeclaredField("slt$magmaCookTimer");
                field.setAccessible(true);
                int heat = (int) field.get(dirt);
                helper.assertTrue(heat == 0, "Non-cookable item should not accumulate cooking progress");
                helper.succeed();
            } catch (Exception e) {
                helper.fail("Reflection error: " + e.getMessage());
            }
        });
    }

    @GameTest(maxTicks = 350)
    public void testDryRoastingStackSizePrevention(GameTestHelper helper) {
        helper.getLevel().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, helper.getLevel().getServer());
        
        BlockPos magmaPos = new BlockPos(1, 1, 1);
        helper.setBlock(magmaPos, Blocks.MAGMA_BLOCK);
        
        BlockPos absMagmaPos = helper.absolutePos(magmaPos);
        net.minecraft.world.entity.item.ItemEntity potatoes = new net.minecraft.world.entity.item.ItemEntity(
            helper.getLevel(),
            absMagmaPos.getX() + 0.5,
            absMagmaPos.getY() + 1.2,
            absMagmaPos.getZ() + 0.5,
            new ItemStack(Items.POTATO, 2)
        );
        potatoes.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(potatoes);
        
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(potatoes.getItem().is(Items.POTATO) && potatoes.getItem().getCount() == 2, "Stack of 2 potatoes should not be dry roasted");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 550)
    public void testAestheticCookOverride(GameTestHelper helper) {
        helper.getLevel().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, helper.getLevel().getServer());
        
        SmallLogicTweaksConfig.ACTIVE_INSTANCE.aestheticCookResults.put("minecraft:porkchop", "minecraft:cooked_beef");
        
        BlockPos magmaPos = new BlockPos(1, 1, 1);
        BlockPos trapdoorPos = magmaPos.above();
        helper.setBlock(magmaPos, Blocks.MAGMA_BLOCK);
        helper.setBlock(trapdoorPos, Blocks.IRON_TRAPDOOR);
        
        BlockPos absTrapdoorPos = helper.absolutePos(trapdoorPos);
        net.minecraft.world.entity.item.ItemEntity porkchop = new net.minecraft.world.entity.item.ItemEntity(
            helper.getLevel(),
            absTrapdoorPos.getX() + 0.5,
            absTrapdoorPos.getY() + 0.2,
            absTrapdoorPos.getZ() + 0.5,
            new ItemStack(Items.PORKCHOP, 1)
        );
        porkchop.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(porkchop);
        
        helper.succeedWhen(() -> {
            helper.assertTrue(porkchop.getItem().is(Items.COOKED_BEEF), "Porkchop should turn into Cooked Beef due to config override");
        });
    }

    @GameTest(maxTicks = 10)
    public void testAestheticValidationRules(GameTestHelper helper) {
        boolean isPotatoCookable = net.enderirt.smalllogictweaks.util.KitchenHelper.isCookable(helper.getLevel(), new ItemStack(Items.POTATO));
        helper.assertTrue(isPotatoCookable, "Potato (default vanilla smeltable) should be cookable");

        boolean isDirtCookable = net.enderirt.smalllogictweaks.util.KitchenHelper.isCookable(helper.getLevel(), new ItemStack(Items.DIRT));
        helper.assertFalse(isDirtCookable, "Dirt (non-smeltable) should NOT be cookable");

        helper.succeed();
    }

    // ================================================================
    // PICKAXE DIRT REVERSION TESTS
    // ================================================================

    /**
     * Bài Test: Kiểm thử hoàn nguyên với tất cả các tier cúp Vanilla trên cả DIRT_PATH và FARMLAND.
     * Mỗi cúp phải kích hoạt thành công thao tác chuyển đổi khối.
     */
    @GameTest(maxTicks = 20)
    public void testPickaxeReversionAllTiers(GameTestHelper helper) {
        // Danh sách tất cả các loại cúp Vanilla
        Item[] pickaxes = {
            Items.WOODEN_PICKAXE,
            Items.STONE_PICKAXE,
            Items.IRON_PICKAXE,
            Items.GOLDEN_PICKAXE,
            Items.DIAMOND_PICKAXE,
            Items.NETHERITE_PICKAXE
        };

        int column = 1;
        for (Item pickaxeItem : pickaxes) {
            // Đặt 1 khối DIRT_PATH và 1 khối FARMLAND cho mỗi cúp
            BlockPos pathPos = new BlockPos(column, 2, 1);
            BlockPos farmPos = new BlockPos(column, 2, 3);

            helper.setBlock(pathPos, Blocks.DIRT_PATH);
            // Farmland cần đất bên dưới và nguồn nước để không sập ngay
            helper.setBlock(farmPos.below(), Blocks.DIRT);
            helper.setBlock(farmPos, Blocks.FARMLAND);

            Player mockPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
            mockPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(pickaxeItem, 1));

            // Tương tác chuột phải vào DIRT_PATH
            BlockPos absPath = helper.absolutePos(pathPos);
            BlockHitResult pathHit = new BlockHitResult(Vec3.atCenterOf(absPath), Direction.UP, absPath, false);
            InteractionResult pathResult = UseBlockCallback.EVENT.invoker().interact(
                    mockPlayer, helper.getLevel(), InteractionHand.MAIN_HAND, pathHit
            );

            if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_PICKAXE_DIRT_REVERSION) {
                helper.assertTrue(pathResult == InteractionResult.SUCCESS,
                        "Cúp " + pickaxeItem + " phải trả về SUCCESS khi xới DIRT_PATH");
                helper.assertBlockPresent(Blocks.DIRT, pathPos);
            }

            // Tương tác chuột phải vào FARMLAND
            BlockPos absFarm = helper.absolutePos(farmPos);
            BlockHitResult farmHit = new BlockHitResult(Vec3.atCenterOf(absFarm), Direction.UP, absFarm, false);

            // Đặt lại Farmland (có thể bị sập từ trước)
            helper.setBlock(farmPos, Blocks.FARMLAND);
            mockPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(pickaxeItem, 1));
            InteractionResult farmResult = UseBlockCallback.EVENT.invoker().interact(
                    mockPlayer, helper.getLevel(), InteractionHand.MAIN_HAND, farmHit
            );

            if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_PICKAXE_DIRT_REVERSION) {
                helper.assertTrue(farmResult == InteractionResult.SUCCESS,
                        "Cúp " + pickaxeItem + " phải trả về SUCCESS khi xới FARMLAND");
                helper.assertBlockPresent(Blocks.DIRT, farmPos);
            }

            column += 2;
        }

        helper.succeed();
    }

    /**
     * Bài Test: Cúp có các cường hóa Unbreaking III, Fortune III, Silk Touch, Efficiency V
     * vẫn phải kích hoạt đúng logic chuyển đổi khối.
     */
    @GameTest(maxTicks = 20)
    public void testPickaxeReversionWithEnchantments(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        // Danh sách cường hóa cần kiểm thử
        net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment>[] enchantKeys = new net.minecraft.resources.ResourceKey[]{
            net.minecraft.world.item.enchantment.Enchantments.UNBREAKING,
            net.minecraft.world.item.enchantment.Enchantments.FORTUNE,
            net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH,
            net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY
        };
        int[] enchantLevels = { 3, 3, 1, 5 };

        int column = 1;
        for (int i = 0; i < enchantKeys.length; i++) {
            BlockPos pathPos = new BlockPos(column, 2, 1);
            helper.setBlock(pathPos, Blocks.DIRT_PATH);

            ItemStack enchantedPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            var enchantHolder = registry.getOrThrow(enchantKeys[i]);
            enchantedPickaxe.enchant(enchantHolder, enchantLevels[i]);

            Player mockPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
            mockPlayer.setItemInHand(InteractionHand.MAIN_HAND, enchantedPickaxe);

            BlockPos absPath = helper.absolutePos(pathPos);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absPath), Direction.UP, absPath, false);
            InteractionResult result = UseBlockCallback.EVENT.invoker().interact(
                    mockPlayer, helper.getLevel(), InteractionHand.MAIN_HAND, hit
            );

            if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_PICKAXE_DIRT_REVERSION) {
                helper.assertTrue(result == InteractionResult.SUCCESS,
                        "Cúp có cường hóa " + enchantKeys[i] + " phải hoàn nguyên DIRT_PATH thành công");
                helper.assertBlockPresent(Blocks.DIRT, pathPos);
            }

            column += 2;
        }

        helper.succeed();
    }

    /**
     * Bài Test: Xác nhận cúp bị trừ đúng 1 điểm độ bền sau khi xới khối trong chế độ Survival.
     */
    @GameTest(maxTicks = 10)
    public void testPickaxeReversionDurabilityCost(GameTestHelper helper) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_PICKAXE_DIRT_REVERSION) {
            helper.succeed();
            return;
        }

        BlockPos pathPos = new BlockPos(1, 2, 1);
        helper.setBlock(pathPos, Blocks.DIRT_PATH);

        // Tạo cúp kim cương mới toanh (damage = 0)
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        int damageBeforeUse = pickaxe.getDamageValue(); // Phải là 0

        Player mockPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        mockPlayer.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);

        BlockPos absPath = helper.absolutePos(pathPos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absPath), Direction.UP, absPath, false);
        UseBlockCallback.EVENT.invoker().interact(mockPlayer, helper.getLevel(), InteractionHand.MAIN_HAND, hit);

        ItemStack afterStack = mockPlayer.getMainHandItem();

        // Khối phải đã biến thành DIRT
        helper.assertBlockPresent(Blocks.DIRT, pathPos);

        // Độ bền phải đã giảm ít nhất 1 (damage tăng ít nhất 1)
        helper.assertTrue(afterStack.getDamageValue() > damageBeforeUse,
                "Cúp phải bị trừ ít nhất 1 điểm độ bền sau khi xới khối. Trước: "
                        + damageBeforeUse + ", Sau: " + afterStack.getDamageValue());

        helper.succeed();
    }

    /**
     * Bài Test: Khi cúp chỉ còn đúng 1 điểm độ bền cuối cùng,
     * sau khi xới khối, khối phải thành DIRT và cúp phải gãy (isEmpty = true).
     */
    @GameTest(maxTicks = 10)
    public void testPickaxeReversionBreaksAtZeroDurability(GameTestHelper helper) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_PICKAXE_DIRT_REVERSION) {
            helper.succeed();
            return;
        }

        BlockPos pathPos = new BlockPos(1, 2, 1);
        helper.setBlock(pathPos, Blocks.DIRT_PATH);

        // Tạo cúp gỗ còn đúng 1 điểm độ bền (damage = maxDamage - 1)
        ItemStack pickaxe = new ItemStack(Items.WOODEN_PICKAXE);
        int maxDamage = pickaxe.getMaxDamage(); // Gỗ: 59
        pickaxe.setDamageValue(maxDamage - 1);   // Còn 1 HP

        Player mockPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        mockPlayer.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);

        BlockPos absPath = helper.absolutePos(pathPos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absPath), Direction.UP, absPath, false);
        UseBlockCallback.EVENT.invoker().interact(mockPlayer, helper.getLevel(), InteractionHand.MAIN_HAND, hit);

        // Khối phải đã thành DIRT
        helper.assertBlockPresent(Blocks.DIRT, pathPos);

        // Cúp phải đã gãy và biến mất khỏi tay
        ItemStack afterStack = mockPlayer.getMainHandItem();
        helper.assertTrue(afterStack.isEmpty(),
                "Cúp gỗ chỉ còn 1 HP phải bị gãy và trở thành ItemStack.EMPTY sau khi sử dụng");

        helper.succeed();
    }

    /**
     * Bài Test: Khi người chơi đứng trực tiếp trên khối DIRT_PATH (độ cao Y = 0.9375),
     * sau khi xới cúp thành DIRT (Full Block Y = 1.0), hệ thống phải tự động bù đắp
     * tọa độ Y của người chơi lên đúng 1.0 để tránh bị lọt chân hoặc văng ra rìa.
     */
    @GameTest(maxTicks = 10)
    public void testPickaxeReversionStepUpCompensation(GameTestHelper helper) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_PICKAXE_DIRT_REVERSION) {
            helper.succeed();
            return;
        }

        BlockPos pathPos = new BlockPos(1, 2, 1);
        helper.setBlock(pathPos, Blocks.DIRT_PATH);

        BlockPos absPath = helper.absolutePos(pathPos);

        Player mockPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        mockPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));

        // Đặt người chơi đứng trực tiếp trên bề mặt Dirt Path (độ cao y = absPath.getY() + 0.9375)
        mockPlayer.setPos(absPath.getX() + 0.5, absPath.getY() + 0.9375, absPath.getZ() + 0.5);

        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absPath), Direction.UP, absPath, false);
        UseBlockCallback.EVENT.invoker().interact(mockPlayer, helper.getLevel(), InteractionHand.MAIN_HAND, hit);

        // Khối phải đã biến thành DIRT
        helper.assertBlockPresent(Blocks.DIRT, pathPos);

        // Tọa độ Y của người chơi phải được nâng lên ít nhất absPath.getY() + 1.0
        double expectedY = absPath.getY() + 1.0;
        helper.assertTrue(mockPlayer.getY() >= expectedY - 0.001,
                "Tọa độ Y của người chơi phải được bù đắp lên đỉnh full block (ít nhất " + expectedY + "), thực tế là: " + mockPlayer.getY());

        helper.succeed();
    }

    /**
     * Bài Test Edge Case 1: Cầm cúp ở tay phụ (Offhand), tay chính cầm vật phẩm khác.
     * Khối phải chuyển thành DIRT và cúp tay phụ phải bị trừ độ bền chuẩn xác.
     */
    @GameTest(maxTicks = 10)
    public void testPickaxeReversionOffhand(GameTestHelper helper) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_PICKAXE_DIRT_REVERSION) {
            helper.succeed();
            return;
        }

        BlockPos pathPos = new BlockPos(1, 2, 1);
        helper.setBlock(pathPos, Blocks.DIRT_PATH);

        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        int damageBefore = pickaxe.getDamageValue();

        Player mockPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        // Tay chính cầm đuốc, tay phụ cầm cúp sắt
        mockPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
        mockPlayer.setItemInHand(InteractionHand.OFF_HAND, pickaxe);

        BlockPos absPath = helper.absolutePos(pathPos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absPath), Direction.UP, absPath, false);

        // Kích hoạt tương tác chuột phải bằng tay phụ
        InteractionResult result = UseBlockCallback.EVENT.invoker().interact(
                mockPlayer, helper.getLevel(), InteractionHand.OFF_HAND, hit
        );

        helper.assertTrue(result == InteractionResult.SUCCESS,
                "Cúp ở tay phụ phải kích hoạt thành công và trả về SUCCESS");
        helper.assertBlockPresent(Blocks.DIRT, pathPos);

        ItemStack afterPickaxe = mockPlayer.getItemInHand(InteractionHand.OFF_HAND);
        helper.assertTrue(afterPickaxe.getDamageValue() > damageBefore,
                "Cúp ở tay phụ phải bị trừ độ bền sau khi xới đất");

        helper.succeed();
    }

    /**
     * Bài Test Edge Case 2: Người chơi đứng ở rìa mép biên khối khi đang Shift (Sneaking).
     * Tâm tọa độ người chơi bị lệch ra ngoài ô vuông [X, X+1.0], nhưng Hitbox AABB vẫn giao nhau với mặt khối.
     * Phép giao tập hợp AABB phải phát hiện và bù đắp tọa độ Y lên 1.0 để triệt tiêu rung giật.
     */
    @GameTest(maxTicks = 10)
    public void testPickaxeReversionEdgeBoundaryAndShift(GameTestHelper helper) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_PICKAXE_DIRT_REVERSION) {
            helper.succeed();
            return;
        }

        BlockPos pathPos = new BlockPos(1, 2, 1);
        helper.setBlock(pathPos, Blocks.DIRT_PATH);

        BlockPos absPath = helper.absolutePos(pathPos);

        Player mockPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        mockPlayer.setShiftKeyDown(true); // Đang giữ Shift (Sneaking)
        mockPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));

        // Đặt người chơi đứng mấp mé mép ngoài: tâm X lệch ra ngoài 0.18 block (absPath.getX() - 0.18)
        // Hitbox của người chơi rộng 0.6 (bán kính 0.3), do đó mép hitbox vẫn đè lên khối 0.12 block
        mockPlayer.setPos(absPath.getX() - 0.18, absPath.getY() + 0.9375, absPath.getZ() + 0.5);

        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absPath), Direction.UP, absPath, false);
        UseBlockCallback.EVENT.invoker().interact(mockPlayer, helper.getLevel(), InteractionHand.MAIN_HAND, hit);

        // Khối phải đã biến thành DIRT
        helper.assertBlockPresent(Blocks.DIRT, pathPos);

        // Tọa độ Y phải được nâng lên đỉnh full block 1.0 nhờ phép giao tập hợp AABB
        double expectedY = absPath.getY() + 1.0;
        helper.assertTrue(mockPlayer.getY() >= expectedY - 0.001,
                "Người chơi dù đứng ở mép biên khi Shift vẫn phải được bù đắp Y lên 1.0 thông qua AABB intersection. Thực tế Y: " + mockPlayer.getY());

        helper.succeed();
    }

    /**
     * [VI] Bài Test: Sửa chữa công cụ bằng nguyên liệu thô bảo toàn Prior Work Penalty (không nhân đôi cộng 1).
     * [EN] Test: Repairing tool with raw materials preserves Prior Work Penalty (does not double + 1).
     */
    @GameTest(maxTicks = 10)
    public void testAnvilPreserveRepairDurabilityCost(GameTestHelper helper) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_ANVIL_PRESERVED) {
            helper.succeed();
            return;
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos anvilPos = new BlockPos(1, 2, 1);
        helper.setBlock(anvilPos, Blocks.ANVIL);
        AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(anvilPos)));

        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.setDamageValue(500);
        pickaxe.set(DataComponents.REPAIR_COST, 15);

        ItemStack diamond = new ItemStack(Items.DIAMOND);

        menu.getSlot(0).set(pickaxe);
        menu.getSlot(1).set(diamond);
        menu.createResult();

        ItemStack result = menu.getSlot(2).getItem();
        helper.assertFalse(result.isEmpty(), "Vật phẩm kết quả trên đe không được rỗng sau khi sửa bằng kim cương");
        int penalty = result.getOrDefault(DataComponents.REPAIR_COST, 0);
        helper.assertTrue(penalty == 15,
                "Điểm phạt Prior Work Penalty phải được giữ nguyên ở mức 15 thay vì nhảy lên 31 theo vanilla. Thực tế: " + penalty);

        helper.succeed();
    }

    /**
     * [VI] Bài Test: Ghép 2 công cụ cùng loại không có bùa nâng cấp bảo toàn điểm phạt lớn nhất giữa 2 vật phẩm.
     * [EN] Test: Combining two like items without upgrades preserves max penalty between them.
     */
    @GameTest(maxTicks = 10)
    public void testAnvilPreserveRepairLikeItemsNoUpgrades(GameTestHelper helper) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_ANVIL_PRESERVED) {
            helper.succeed();
            return;
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos anvilPos = new BlockPos(1, 2, 1);
        helper.setBlock(anvilPos, Blocks.ANVIL);
        AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(anvilPos)));

        ItemStack pickaxe1 = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe1.setDamageValue(500);
        pickaxe1.set(DataComponents.REPAIR_COST, 15);

        ItemStack pickaxe2 = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe2.setDamageValue(300);
        pickaxe2.set(DataComponents.REPAIR_COST, 7);

        menu.getSlot(0).set(pickaxe1);
        menu.getSlot(1).set(pickaxe2);
        menu.createResult();

        ItemStack result = menu.getSlot(2).getItem();
        helper.assertFalse(result.isEmpty(), "Vật phẩm kết quả trên đe không được rỗng khi ghép 2 cúp");
        int penalty = result.getOrDefault(DataComponents.REPAIR_COST, 0);
        helper.assertTrue(penalty == 15,
                "Điểm phạt phải là max(15, 7) = 15 mà không bị nhân đôi phạt. Thực tế: " + penalty);

        helper.succeed();
    }

    /**
     * [VI] Bài Test: Nâng cấp cấp độ bùa phép phải tăng điểm phạt Prior Work Penalty theo vanilla.
     * [EN] Test: Upgrading enchantment level must scale Prior Work Penalty according to vanilla.
     */
    @GameTest(maxTicks = 10)
    public void testAnvilEnchantUpgradeScalesRepairCost(GameTestHelper helper) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_ANVIL_PRESERVED) {
            helper.succeed();
            return;
        }

        var registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var unbreakingHolder = registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos anvilPos = new BlockPos(1, 2, 1);
        helper.setBlock(anvilPos, Blocks.ANVIL);
        AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(anvilPos)));

        ItemStack pickaxe1 = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe1.enchant(unbreakingHolder, 1);
        pickaxe1.set(DataComponents.REPAIR_COST, 15);

        ItemStack pickaxe2 = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe2.enchant(unbreakingHolder, 2);
        pickaxe2.set(DataComponents.REPAIR_COST, 0);

        menu.getSlot(0).set(pickaxe1);
        menu.getSlot(1).set(pickaxe2);
        menu.createResult();

        ItemStack result = menu.getSlot(2).getItem();
        helper.assertFalse(result.isEmpty(), "Vật phẩm kết quả không được rỗng khi ghép nâng cấp bùa");
        int penalty = result.getOrDefault(DataComponents.REPAIR_COST, 0);
        helper.assertTrue(penalty == 31,
                "Khi có bùa phép được nâng cấp bậc, điểm phạt phải tăng lên 31 (15 * 2 + 1). Thực tế: " + penalty);

        helper.succeed();
    }

    /**
     * [VI] Bài Test: Đổi tên công cụ bảo toàn Prior Work Penalty.
     * [EN] Test: Renaming tool preserves Prior Work Penalty.
     */
    @GameTest(maxTicks = 10)
    public void testAnvilRenamingPreservesRepairCost(GameTestHelper helper) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_ANVIL_PRESERVED) {
            helper.succeed();
            return;
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos anvilPos = new BlockPos(1, 2, 1);
        helper.setBlock(anvilPos, Blocks.ANVIL);
        AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(anvilPos)));

        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.set(DataComponents.REPAIR_COST, 15);

        menu.getSlot(0).set(pickaxe);
        menu.setItemName("Excalibur");
        menu.createResult();

        ItemStack result = menu.getSlot(2).getItem();
        helper.assertFalse(result.isEmpty(), "Vật phẩm kết quả không được rỗng khi đổi tên");
        int penalty = result.getOrDefault(DataComponents.REPAIR_COST, 0);
        helper.assertTrue(penalty == 15,
                "Đổi tên công cụ phải giữ nguyên điểm phạt là 15. Thực tế: " + penalty);

        helper.succeed();
    }

    /**
     * [VI] Bài Test: Khi tắt cấu hình ENABLE_ANVIL_PRESERVED, hành vi đe quay về nhân đôi điểm phạt chuẩn vanilla.
     * [EN] Test: When ENABLE_ANVIL_PRESERVED is disabled, anvil penalty behavior falls back to vanilla doubling.
     */
    @GameTest(maxTicks = 10)
    public void testAnvilDisabledConfigScalesPenalty(GameTestHelper helper) {
        boolean originalConfig = SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_ANVIL_PRESERVED;
        try {
            SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_ANVIL_PRESERVED = false;

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockPos anvilPos = new BlockPos(1, 2, 1);
            helper.setBlock(anvilPos, Blocks.ANVIL);
            AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(anvilPos)));

            ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            pickaxe.setDamageValue(500);
            pickaxe.set(DataComponents.REPAIR_COST, 15);

            menu.getSlot(0).set(pickaxe);
            menu.getSlot(1).set(new ItemStack(Items.DIAMOND));
            menu.createResult();

            ItemStack result = menu.getSlot(2).getItem();
            helper.assertFalse(result.isEmpty(), "Vật phẩm kết quả không được rỗng");
            int penalty = result.getOrDefault(DataComponents.REPAIR_COST, 0);
            helper.assertTrue(penalty == 31,
                    "Khi tắt tính năng, điểm phạt phải tăng lên 31 theo chuẩn vanilla. Thực tế: " + penalty);
        } finally {
            SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_ANVIL_PRESERVED = originalConfig;
        }

        helper.succeed();
    }

    /**
     * [VI] Bài Test: Sinh vật thuộc danh sách nhẹ cân (Allay, Bat) không làm suy giảm hoặc phá vỡ trứng rùa khi bước lên.
     * [EN] Test: Weightless entities (Allay, Bat) do not degrade or destroy turtle eggs when stepping on them.
     */
    @GameTest(maxTicks = 10)
    public void testWeightlessMobsDoNotDestroyTurtleEggs(GameTestHelper helper) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_WEIGHTLESS_MOBS) {
            helper.succeed();
            return;
        }

        BlockPos eggPos = new BlockPos(1, 2, 1);
        BlockState eggState = Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, 4);
        helper.setBlock(eggPos, eggState);

        var allay = helper.spawn(net.minecraft.world.entity.EntityTypes.ALLAY, new BlockPos(1, 2, 1));
        var bat = helper.spawn(net.minecraft.world.entity.EntityTypes.BAT, new BlockPos(1, 2, 1));

        BlockPos absEggPos = helper.absolutePos(eggPos);

        // Mô phỏng Allay và Bat bước lên khối trứng nhiều lần
        for (int i = 0; i < 20; i++) {
            Blocks.TURTLE_EGG.stepOn(helper.getLevel(), absEggPos, helper.getBlockState(eggPos), allay);
            Blocks.TURTLE_EGG.stepOn(helper.getLevel(), absEggPos, helper.getBlockState(eggPos), bat);
        }

        BlockState stateAfter = helper.getBlockState(eggPos);
        helper.assertTrue(stateAfter.is(Blocks.TURTLE_EGG), "Khối phải vẫn là trứng rùa");
        int eggsCount = stateAfter.getValue(TurtleEggBlock.EGGS);
        helper.assertTrue(eggsCount == 4,
                "Số lượng trứng rùa phải được bảo toàn nguyên vẹn là 4 quả sau khi các sinh vật nhẹ cân dẫm lên. Thực tế: " + eggsCount);

        helper.succeed();
    }

    /**
     * [VI] Bài Test: Xác nhận an toàn khi truyền thực thể rỗng (null) và khi tắt cấu hình ENABLE_WEIGHTLESS_MOBS.
     * [EN] Test: Confirm null-safety and proper behavior when ENABLE_WEIGHTLESS_MOBS is disabled.
     */
    @GameTest(maxTicks = 10)
    public void testWeightlessMobsDisabledSafetyAndNullCheck(GameTestHelper helper) {
        BlockPos eggPos = new BlockPos(1, 2, 1);
        BlockState eggState = Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, 4);
        helper.setBlock(eggPos, eggState);
        BlockPos absEggPos = helper.absolutePos(eggPos);

        // 1. Kiểm tra an toàn khi entity là null đối với logic canDestroyEgg
        try {
            java.lang.reflect.Method canDestroyMethod = TurtleEggBlock.class.getDeclaredMethod(
                    "canDestroyEgg", ServerLevel.class, BlockPos.class, net.minecraft.world.entity.Entity.class
            );
            canDestroyMethod.setAccessible(true);
            boolean result = (boolean) canDestroyMethod.invoke(Blocks.TURTLE_EGG, helper.getLevel(), absEggPos, null);
            helper.assertFalse(result, "canDestroyEgg với entity rỗng (null) không được phép phá vỡ trứng");
        } catch (Exception ex) {
            helper.fail("canDestroyEgg không được ném ngoại lệ khi entity là null: " + ex.getMessage());
        }

        // 2. Kiểm tra khi tắt cấu hình
        boolean original = SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_WEIGHTLESS_MOBS;
        try {
            SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_WEIGHTLESS_MOBS = false;
            var bat = helper.spawn(net.minecraft.world.entity.EntityTypes.BAT, new BlockPos(1, 2, 1));
            // Đảm bảo không ném ngoại lệ khi tắt cấu hình
            Blocks.TURTLE_EGG.stepOn(helper.getLevel(), absEggPos, helper.getBlockState(eggPos), bat);
        } finally {
            SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_WEIGHTLESS_MOBS = original;
        }

        helper.succeed();
    }

    /**
     * [VI] Bài Test: Đối chứng tích cực - Zombie (thực thể không thuộc danh sách nhẹ cân)
     *      dẫm lên trứng rùa phải làm suy giảm hoặc phá vỡ trứng rùa theo cơ chế vanilla.
     * [EN] Test: Positive control - Zombie (non-weightless entity) stepping on turtle eggs
     *      must degrade or destroy turtle eggs according to vanilla mechanics.
     */
    @GameTest(maxTicks = 20)
    public void testZombieStillBreaksTurtleEgg(GameTestHelper helper) {
        BlockPos eggPos = new BlockPos(1, 2, 1);
        BlockState eggState = Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, 4);
        helper.setBlock(eggPos, eggState);

        var zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
        BlockPos absEggPos = helper.absolutePos(eggPos);

        // Trong vanilla TurtleEggBlock.stepOn, có xác suất 1/100 mỗi lần dẫm làm giảm trứng.
        // Mô phỏng 600 lần bước chân của zombie để đảm bảo tính xác định (xác suất không vỡ < 0.2%).
        for (int i = 0; i < 600; i++) {
            BlockState current = helper.getBlockState(eggPos);
            if (!current.is(Blocks.TURTLE_EGG)) {
                // Khối trứng đã vỡ hoàn toàn thành AIR
                break;
            }
            Blocks.TURTLE_EGG.stepOn(helper.getLevel(), absEggPos, current, zombie);
        }

        BlockState stateAfter = helper.getBlockState(eggPos);
        boolean eggsDegradedOrBroken = !stateAfter.is(Blocks.TURTLE_EGG) || stateAfter.getValue(TurtleEggBlock.EGGS) < 4;
        helper.assertTrue(eggsDegradedOrBroken,
                "Zombie dẫm lên trứng rùa phải làm giảm số lượng trứng hoặc phá vỡ khối trứng theo đúng cơ chế gốc của game.");

        helper.succeed();
    }

    /**
     * [VI] Bài Test: Kiểm tra đầy đủ 5 loài sinh vật nhẹ cân còn lại trong danh sách tag
     *      (ong, phantom, ghast, vẹt, vex) không làm vỡ hoặc suy giảm trứng rùa.
     * [EN] Test: Verify all remaining 5 weightless mobs in the tag
     *      (bee, phantom, ghast, parrot, vex) do not break or degrade turtle eggs.
     */
    @GameTest(maxTicks = 15)
    public void testAllRemainingWeightlessMobsDoNotDestroyTurtleEggs(GameTestHelper helper) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_WEIGHTLESS_MOBS) {
            helper.succeed();
            return;
        }

        BlockPos eggPos = new BlockPos(1, 2, 1);
        BlockState eggState = Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, 4);
        helper.setBlock(eggPos, eggState);
        BlockPos absEggPos = helper.absolutePos(eggPos);

        var bee = helper.spawn(net.minecraft.world.entity.EntityTypes.BEE, new BlockPos(1, 2, 1));
        var phantom = helper.spawn(net.minecraft.world.entity.EntityTypes.PHANTOM, new BlockPos(1, 2, 1));
        var ghast = helper.spawn(net.minecraft.world.entity.EntityTypes.GHAST, new BlockPos(1, 2, 1));
        var parrot = helper.spawn(net.minecraft.world.entity.EntityTypes.PARROT, new BlockPos(1, 2, 1));
        var vex = helper.spawn(net.minecraft.world.entity.EntityTypes.VEX, new BlockPos(1, 2, 1));

        // Mô phỏng cả 5 sinh vật cùng bay lượn và đáp lên trứng rùa
        for (int i = 0; i < 20; i++) {
            Blocks.TURTLE_EGG.stepOn(helper.getLevel(), absEggPos, helper.getBlockState(eggPos), bee);
            Blocks.TURTLE_EGG.stepOn(helper.getLevel(), absEggPos, helper.getBlockState(eggPos), phantom);
            Blocks.TURTLE_EGG.stepOn(helper.getLevel(), absEggPos, helper.getBlockState(eggPos), ghast);
            Blocks.TURTLE_EGG.stepOn(helper.getLevel(), absEggPos, helper.getBlockState(eggPos), parrot);
            Blocks.TURTLE_EGG.stepOn(helper.getLevel(), absEggPos, helper.getBlockState(eggPos), vex);
        }

        BlockState stateAfter = helper.getBlockState(eggPos);
        helper.assertTrue(stateAfter.is(Blocks.TURTLE_EGG), "Khối phải vẫn là trứng rùa");
        int eggsCount = stateAfter.getValue(TurtleEggBlock.EGGS);
        helper.assertTrue(eggsCount == 4,
                "Toàn bộ 5 loài sinh vật nhẹ cân còn lại dẫm lên không được làm vỡ trứng (phải còn nguyên 4 quả). Thực tế: " + eggsCount);

        helper.succeed();
    }

    /**
     * [VI] Bài Test: Ghép 2 vật phẩm cùng chứa bùa chú đã đạt cấp tối đa (ví dụ Silk Touch I)
     *      không có nâng cấp bùa phép nên bắt buộc phải bảo toàn điểm phạt lớn nhất.
     * [EN] Test: Combining two items with max-level enchantment (e.g. Silk Touch I)
     *      has no upgrade, so it must preserve the maximum prior work penalty.
     */
    @GameTest(maxTicks = 10)
    public void testAnvilMaxLevelEnchantPreservesPenalty(GameTestHelper helper) {
        if (!SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_ANVIL_PRESERVED) {
            helper.succeed();
            return;
        }

        var registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var silkTouchHolder = registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos anvilPos = new BlockPos(1, 2, 1);
        helper.setBlock(anvilPos, Blocks.ANVIL);
        AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(anvilPos)));

        ItemStack pickaxe1 = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe1.enchant(silkTouchHolder, 1); // Cấp tối đa của Silk Touch là 1
        pickaxe1.setDamageValue(400);
        pickaxe1.set(DataComponents.REPAIR_COST, 15);

        ItemStack pickaxe2 = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe2.enchant(silkTouchHolder, 1);
        pickaxe2.setDamageValue(200);
        pickaxe2.set(DataComponents.REPAIR_COST, 5);

        menu.getSlot(0).set(pickaxe1);
        menu.getSlot(1).set(pickaxe2);
        menu.createResult();

        ItemStack result = menu.getSlot(2).getItem();
        helper.assertFalse(result.isEmpty(), "Vật phẩm kết quả không được rỗng khi ghép 2 cúp có Silk Touch I");
        int penalty = result.getOrDefault(DataComponents.REPAIR_COST, 0);
        helper.assertTrue(penalty == 15,
                "Ghép 2 bùa cùng cấp đã đạt trần tối đa không được tăng điểm phạt (phải giữ 15 thay vì nhảy lên 31). Thực tế: " + penalty);

        helper.succeed();
    }

    /**
     * [VI] Bài Test: Kiểm thử cờ REQUIRE_NEIGHBOR_SOURCE của BoneMealHandler.
     *      Khi bật cờ, nếu không có khối cỏ lân cận thì bột xương không thể biến đổi đất.
     *      Khi có khối cỏ lân cận thì đất được biến đổi thành khối cỏ thành công.
     * [EN] Test: Test REQUIRE_NEIGHBOR_SOURCE flag of BoneMealHandler.
     *      When enabled, bone meal cannot convert dirt without neighboring grass blocks.
     *      When a neighboring grass block exists, conversion succeeds.
     */
    @GameTest(maxTicks = 10)
    public void testBoneMealSpreadingSourceRequirement(GameTestHelper helper) {
        boolean originalRequire = SmallLogicTweaksConfig.ACTIVE_INSTANCE.REQUIRE_NEIGHBOR_SOURCE;
        try {
            SmallLogicTweaksConfig.ACTIVE_INSTANCE.REQUIRE_NEIGHBOR_SOURCE = true;

            BlockPos isolatedDirt = new BlockPos(1, 2, 1);
            helper.setBlock(isolatedDirt, Blocks.DIRT);

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL));

            BlockPos absDirt = helper.absolutePos(isolatedDirt);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absDirt), Direction.UP, absDirt, false);

            // 1. Trường hợp cô lập (không có cỏ xung quanh) -> Phải thất bại (PASS)
            InteractionResult result1 = net.enderirt.smalllogictweaks.handler.BoneMealHandler.handleBoneMeal(
                    helper.getLevel(), player, InteractionHand.MAIN_HAND, hit
            );
            helper.assertTrue(result1 == InteractionResult.PASS,
                    "Khi REQUIRE_NEIGHBOR_SOURCE bật, đất cô lập không có cỏ lân cận không được phép biến đổi");
            helper.assertBlockPresent(Blocks.DIRT, isolatedDirt);

            // 2. Đặt một khối cỏ lân cận (offset 1, 0, 0)
            BlockPos neighborGrass = new BlockPos(2, 2, 1);
            helper.setBlock(neighborGrass, Blocks.GRASS_BLOCK);

            // Tương tác lại -> Phải thành công (SUCCESS) và biến thành GRASS_BLOCK
            InteractionResult result2 = net.enderirt.smalllogictweaks.handler.BoneMealHandler.handleBoneMeal(
                    helper.getLevel(), player, InteractionHand.MAIN_HAND, hit
            );
            helper.assertTrue(result2 == InteractionResult.SUCCESS,
                    "Khi có khối cỏ lân cận, đất phải được biến đổi thành công");
            helper.assertBlockPresent(Blocks.GRASS_BLOCK, isolatedDirt);

        } finally {
            SmallLogicTweaksConfig.ACTIVE_INSTANCE.REQUIRE_NEIGHBOR_SOURCE = originalRequire;
        }

        helper.succeed();
    }

    @GameTest(maxTicks = 10)
    public void testAdventureModeBlockedOnRightClickTweaks(GameTestHelper helper) {
        Player adventurePlayer = helper.makeMockPlayer(GameType.ADVENTURE);
        GameType.ADVENTURE.updatePlayerAbilities(adventurePlayer.getAbilities());

        // 1. Bone meal trên dirt phải bị chặn (PASS)
        BlockPos dirtPos = new BlockPos(1, 2, 1);
        helper.setBlock(dirtPos, Blocks.DIRT);
        adventurePlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL));
        BlockPos absDirt = helper.absolutePos(dirtPos);
        InteractionResult boneMealRes = net.enderirt.smalllogictweaks.handler.BoneMealHandler.handleBoneMeal(
                helper.getLevel(), adventurePlayer, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absDirt), Direction.UP, absDirt, false)
        );
        helper.assertTrue(boneMealRes == InteractionResult.PASS, "Adventure player must not convert dirt with bone meal.");
        helper.assertBlockPresent(Blocks.DIRT, dirtPos);

        // 2. Water bottle trên bột bê tông phải bị chặn (PASS)
        Block whitePowder = Blocks.CONCRETE_POWDER.pick(net.minecraft.world.item.DyeColor.WHITE);
        BlockPos powderPos = new BlockPos(2, 2, 1);
        helper.setBlock(powderPos, whitePowder);
        adventurePlayer.setItemInHand(InteractionHand.MAIN_HAND,
                net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER));
        BlockPos absPowder = helper.absolutePos(powderPos);
        InteractionResult waterRes = net.enderirt.smalllogictweaks.handler.ConcreteHardeningHandler.handleWaterBottle(
                helper.getLevel(), adventurePlayer, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absPowder), Direction.UP, absPowder, false)
        );
        helper.assertTrue(waterRes == InteractionResult.PASS, "Adventure player must not harden concrete powder.");
        helper.assertBlockPresent(whitePowder, powderPos);

        // 3. Cúp trên farmland phải bị chặn (PASS)
        BlockPos farmPos = new BlockPos(3, 2, 1);
        helper.setBlock(farmPos, Blocks.FARMLAND);
        adventurePlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        BlockPos absFarm = helper.absolutePos(farmPos);
        InteractionResult pickRes = net.enderirt.smalllogictweaks.handler.DirtReversionHandler.handlePickaxeReversion(
                helper.getLevel(), adventurePlayer, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absFarm), Direction.UP, absFarm, false)
        );
        helper.assertTrue(pickRes == InteractionResult.PASS, "Adventure player must not revert farmland with a pickaxe.");
        helper.assertBlockPresent(Blocks.FARMLAND, farmPos);

        helper.succeed();
    }

    @GameTest(maxTicks = 10)
    public void testDisabledTweaksRespectConfigFlags(GameTestHelper helper) {
        var cfg = SmallLogicTweaksConfig.ACTIVE_INSTANCE;
        boolean origBoneMeal = cfg.ENABLE_BONE_MEAL_TWEAK;
        boolean origHydro = cfg.ENABLE_HYDRO_HARDENING;
        boolean origRevert = cfg.ENABLE_PICKAXE_DIRT_REVERSION;
        boolean origPhantom = cfg.ENABLE_END_PHANTOM;

        try {
            cfg.ENABLE_BONE_MEAL_TWEAK = false;
            cfg.ENABLE_HYDRO_HARDENING = false;
            cfg.ENABLE_PICKAXE_DIRT_REVERSION = false;
            cfg.ENABLE_END_PHANTOM = false;

            Player survivalPlayer = helper.makeMockPlayer(GameType.SURVIVAL);

            BlockPos dirtPos = new BlockPos(1, 2, 1);
            helper.setBlock(dirtPos, Blocks.DIRT);
            survivalPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL));
            BlockPos absDirt = helper.absolutePos(dirtPos);
            helper.assertTrue(
                    net.enderirt.smalllogictweaks.handler.BoneMealHandler.handleBoneMeal(
                            helper.getLevel(), survivalPlayer, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(absDirt), Direction.UP, absDirt, false)
                    ) == InteractionResult.PASS,
                    "BoneMealHandler must PASS when ENABLE_BONE_MEAL_TWEAK is false."
            );
            helper.assertBlockPresent(Blocks.DIRT, dirtPos);

            Block whitePowder = Blocks.CONCRETE_POWDER.pick(net.minecraft.world.item.DyeColor.WHITE);
            BlockPos powderPos = new BlockPos(2, 2, 1);
            helper.setBlock(powderPos, whitePowder);
            survivalPlayer.setItemInHand(InteractionHand.MAIN_HAND,
                    net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER));
            BlockPos absPowder = helper.absolutePos(powderPos);
            helper.assertTrue(
                    net.enderirt.smalllogictweaks.handler.ConcreteHardeningHandler.handleWaterBottle(
                            helper.getLevel(), survivalPlayer, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(absPowder), Direction.UP, absPowder, false)
                    ) == InteractionResult.PASS,
                    "ConcreteHardeningHandler must PASS when ENABLE_HYDRO_HARDENING is false."
            );
            helper.assertBlockPresent(whitePowder, powderPos);

            BlockPos farmPos = new BlockPos(3, 2, 1);
            helper.setBlock(farmPos, Blocks.FARMLAND);
            survivalPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            BlockPos absFarm = helper.absolutePos(farmPos);
            helper.assertTrue(
                    net.enderirt.smalllogictweaks.handler.DirtReversionHandler.handlePickaxeReversion(
                            helper.getLevel(), survivalPlayer, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(absFarm), Direction.UP, absFarm, false)
                    ) == InteractionResult.PASS,
                    "DirtReversionHandler must PASS when ENABLE_PICKAXE_DIRT_REVERSION is false."
            );
            helper.assertBlockPresent(Blocks.FARMLAND, farmPos);

            com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "DisabledPhantomTester");
            ServerPlayer sp = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, net.minecraft.server.level.ClientInformation.createDefault());
            sp.getStats().setValue(sp, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 80000);
            net.enderirt.smalllogictweaks.handler.EndPhantomHandler.resetInsomniaIfOutsideEnd(sp);
            helper.assertTrue(
                    sp.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) == 80000,
                    "TIME_SINCE_REST must not be reset when ENABLE_END_PHANTOM is false."
            );
        } finally {
            cfg.ENABLE_BONE_MEAL_TWEAK = origBoneMeal;
            cfg.ENABLE_HYDRO_HARDENING = origHydro;
            cfg.ENABLE_PICKAXE_DIRT_REVERSION = origRevert;
            cfg.ENABLE_END_PHANTOM = origPhantom;
        }

        helper.succeed();
    }

    @GameTest(maxTicks = 10)
    public void testEndPhantomOverworldLeftoverStatReset(GameTestHelper helper) {
        boolean orig = SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_END_PHANTOM;
        try {
            SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_END_PHANTOM = true;
            com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "OverworldPhantomTester");
            ServerPlayer sp = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, net.minecraft.server.level.ClientInformation.createDefault());
            sp.getStats().setValue(sp, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 80000);
            net.enderirt.smalllogictweaks.handler.EndPhantomHandler.resetInsomniaIfOutsideEnd(sp);
            helper.assertTrue(
                    sp.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) == 0,
                    "Leftover TIME_SINCE_REST outside The End must be reset to 0 when ENABLE_END_PHANTOM is true."
            );
        } finally {
            SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_END_PHANTOM = orig;
        }
        helper.succeed();
    }

    private static ServerPlayer createConnectedServerPlayer(ServerLevel level, String name) {
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name);
        ServerPlayer sp = new ServerPlayer(level.getServer(), level, profile, net.minecraft.server.level.ClientInformation.createDefault());
        var conn = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        sp.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(
                level.getServer(), conn, sp, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)
        );
        return sp;
    }

    @GameTest(maxTicks = 20)
    public void testTimberAdvancedLevelsRadiusPersistentLeavesAndRealServerPlayer(GameTestHelper helper) {
        var cfg = SmallLogicTweaksConfig.ACTIVE_INSTANCE;
        int origRadius = cfg.MAX_LOG_HORIZONTAL_RADIUS;
        int origLeafDist = cfg.MAX_LEAF_DISTANCE;
        boolean origDecay = cfg.ENABLE_AUTO_LEAVES_DECAY;
        int origDecayThresh = cfg.DECAY_THRESHOLD;
        ServerLevel level = helper.getLevel();

        try {
            cfg.MAX_LOG_HORIZONTAL_RADIUS = 1;
            cfg.MAX_LEAF_DISTANCE = 4;
            cfg.ENABLE_AUTO_LEAVES_DECAY = true;
            cfg.DECAY_THRESHOLD = 4;

            // 1. Timber II (0.9f), Timber III (0.8f), cache hit, và gọi trực tiếp BlockState.getDestroyProgress (TimberMiningSpeedMixin)
            BlockPos tree1 = new BlockPos(1, 2, 1);
            buildValidTree(helper, tree1); // 3 logs + 4 natural leaves
            BlockPos absTree1 = helper.absolutePos(tree1);

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            equipTimberAxe(helper, player, 2);
            float factorLv2 = SmallLogicTweaksEvents.getTimberSpeedFactor(player, absTree1);
            float cachedLv2 = SmallLogicTweaksEvents.getTimberSpeedFactor(player, absTree1);
            helper.assertTrue(Math.abs(factorLv2 - 2.7f) < 0.01f && cachedLv2 == factorLv2,
                    "Timber II trên 3 gỗ phải trả hệ số 2.7f và hit cache ổn định.");

            equipTimberAxe(helper, player, 3);
            float factorLv3 = SmallLogicTweaksEvents.getTimberSpeedFactor(player, absTree1);
            helper.assertTrue(Math.abs(factorLv3 - 2.4f) < 0.01f,
                    "Timber III trên 3 gỗ phải trả hệ số 2.4f, nhận: " + factorLv3);

            // Mixin TimberMiningSpeedMixin trên gỗ vs đá vs Shift vs rìu thường
            BlockState logState = level.getBlockState(absTree1);
            float timberProgress = logState.getDestroyProgress(player, level, absTree1);
            player.setShiftKeyDown(true);
            float shiftProgress = logState.getDestroyProgress(player, level, absTree1);
            player.setShiftKeyDown(false);
            helper.assertTrue(timberProgress > 0.0f && shiftProgress > timberProgress,
                    "TimberMiningSpeedMixin phải giảm tốc độ đào khi không Shift so với khi giữ Shift.");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
            helper.assertTrue(SmallLogicTweaksEvents.getTimberSpeedFactor(player, absTree1) == 1.0f,
                    "Rìu không có phù phép Timber phải trả về 1.0f.");

            BlockPos stonePos = helper.absolutePos(new BlockPos(0, 2, 0));
            helper.setBlock(new BlockPos(0, 2, 0), Blocks.STONE);
            equipTimberAxe(helper, player, 1);
            helper.assertTrue(SmallLogicTweaksEvents.getTimberSpeedFactor(player, stonePos) == 1.0f,
                    "Khối không phải gỗ phải trả về hệ số 1.0f.");
            level.getBlockState(stonePos).getDestroyProgress(player, level, stonePos);

            // 2. Lá PERSISTENT = true (đặt tay) không kích hoạt Timber
            BlockPos fakeTree = new BlockPos(4, 2, 1);
            for (int y = 0; y < 3; y++) helper.setBlock(fakeTree.above(y), Blocks.OAK_LOG);
            BlockState persistentLeaf = Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
            helper.setBlock(fakeTree.above(3), persistentLeaf);
            helper.setBlock(fakeTree.above(2).east(), persistentLeaf);
            helper.setBlock(fakeTree.above(2).west(), persistentLeaf);
            helper.setBlock(fakeTree.above(2).north(), persistentLeaf);
            BlockPos absFake = helper.absolutePos(fakeTree);
            helper.assertTrue(net.enderirt.smalllogictweaks.handler.TimberHandler.handleTimberChop(
                    level, player, absFake, level.getBlockState(absFake)),
                    "Cây chỉ có lá PERSISTENT=true không được kích hoạt Timber.");

            // 3. Bán kính ngang MAX_LOG_HORIZONTAL_RADIUS=1 và lá cạnh thân gỗ lân cận được giữ lại (DISTANCE=1)
            BlockPos outOfRadiusLog = new BlockPos(3, 2, 1); // cách tree1 X=+2 (> MAX_LOG_HORIZONTAL_RADIUS=1)
            helper.setBlock(new BlockPos(2, 2, 1), Blocks.OAK_LOG); // cầu nối X=+1
            helper.setBlock(outOfRadiusLog, Blocks.OAK_LOG);
            // Lá sát outOfRadiusLog vẫn dính với cây mới (tree1 nối tới X+1, outOfRadiusLog là X+2 còn nguyên)
            helper.setBlock(new BlockPos(2, 3, 1), Blocks.OAK_LEAVES); // gần tree1, sẽ chịu processAutoLeavesDecay

            // 4. Thực thi với ServerPlayer thật (đi qua sp.gameMode.destroyBlock) và rìu gần hỏng
            ServerPlayer sp = createConnectedServerPlayer(level, "TimberServerPlayer");
            sp.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            sp.setPos(absTree1.getX() + 0.5, absTree1.getY(), absTree1.getZ() + 0.5);
            equipTimberAxe(helper, sp, 1);
            ItemStack realAxe = sp.getMainHandItem();
            realAxe.setDamageValue(realAxe.getMaxDamage() - 2); // Chỉ đủ phá tối đa 2 khối rồi ngừng giữa chừng

            boolean chopAllowed = net.enderirt.smalllogictweaks.handler.TimberHandler.handleTimberChop(
                    level, sp, absTree1, level.getBlockState(absTree1));
            helper.assertFalse(chopAllowed, "Timber với ServerPlayer thật phải trả false để hủy single-break.");
            helper.assertBlockPresent(Blocks.OAK_LOG, outOfRadiusLog);
            helper.assertTrue(realAxe.isEmpty() || realAxe.getDamageValue() >= realAxe.getMaxDamage(),
                    "Rìu phải dừng chặt khi hết độ bền.");

            // 5. Không có phù phép Timber hoặc khi tắt ENABLE_TIMBER_TWEAK -> analyzeTimber trả shouldChop=false
            BlockPos plainTree = new BlockPos(6, 2, 5);
            buildValidTree(helper, plainTree);
            BlockPos absPlain = helper.absolutePos(plainTree);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
            helper.assertFalse(net.enderirt.smalllogictweaks.handler.TimberHandler.analyzeTimber(
                    level, player, absPlain, player.getMainHandItem()).shouldChop(),
                    "Rìu không có Timber phải trả shouldChop=false.");
            boolean origTimber = cfg.ENABLE_TIMBER_TWEAK;
            try {
                cfg.ENABLE_TIMBER_TWEAK = false;
                equipTimberAxe(helper, player, 1);
                helper.assertFalse(net.enderirt.smalllogictweaks.handler.TimberHandler.analyzeTimber(
                        level, player, absPlain, player.getMainHandItem()).shouldChop(),
                        "Khi tắt ENABLE_TIMBER_TWEAK phải trả shouldChop=false.");
            } finally {
                cfg.ENABLE_TIMBER_TWEAK = origTimber;
            }
        } finally {
            cfg.MAX_LOG_HORIZONTAL_RADIUS = origRadius;
            cfg.MAX_LEAF_DISTANCE = origLeafDist;
            cfg.ENABLE_AUTO_LEAVES_DECAY = origDecay;
            cfg.DECAY_THRESHOLD = origDecayThresh;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void testKitchenHeatSourcesCoverPositionsAndMergePrevention(GameTestHelper helper) {
        var cfg = SmallLogicTweaksConfig.ACTIVE_INSTANCE;
        boolean origKitchen = cfg.ENABLE_AESTHETIC_KITCHEN;
        boolean origDry = cfg.ENABLE_DRY_ROASTING;
        boolean origCauldron = cfg.ENABLE_CAULDRON_BOILING;
        var origMap = new java.util.LinkedHashMap<>(cfg.magmaCookTimes);
        var origOverrides = new java.util.LinkedHashMap<>(cfg.aestheticCookResults);
        ServerLevel level = helper.getLevel();
        level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, level.getServer());

        try {
            // 1. Kiểm tra getHeatMultiplier & isHeatSource cho MAGMA, FIRE, SOUL_FIRE, LAVA, STONE
            helper.assertTrue(net.enderirt.smalllogictweaks.util.KitchenHelper.getHeatMultiplier(Blocks.SOUL_FIRE.defaultBlockState()) == 1.2f,
                    "Soul Fire heat multiplier phải là 1.2f.");
            helper.assertTrue(net.enderirt.smalllogictweaks.util.KitchenHelper.getHeatMultiplier(Blocks.LAVA.defaultBlockState()) == 1.5f,
                    "Lava heat multiplier phải là 1.5f.");
            helper.assertTrue(net.enderirt.smalllogictweaks.util.KitchenHelper.isHeatSource(Blocks.FIRE.defaultBlockState()),
                    "FIRE phải được nhận diện là nguồn nhiệt.");
            helper.assertFalse(net.enderirt.smalllogictweaks.util.KitchenHelper.isHeatSource(Blocks.STONE.defaultBlockState()),
                    "STONE không phải nguồn nhiệt.");

            // 2. Bếp phủ ở entityPos.below() (đặt slab dưới chân entity, magma ở 2 khối dưới)
            BlockPos magmaLow = new BlockPos(1, 1, 1);
            BlockPos slabCover = new BlockPos(1, 2, 1);
            helper.setBlock(magmaLow, Blocks.MAGMA_BLOCK);
            helper.setBlock(slabCover, Blocks.STONE_SLAB); // TOP/BOTTOM default -> entity đứng ở y=3 (below = y=2 cover, below(2)=y=1 magma)
            BlockPos absAboveSlab = helper.absolutePos(new BlockPos(1, 3, 1));
            var beefOnSlab1 = new net.minecraft.world.entity.item.ItemEntity(
                    level, absAboveSlab.getX() + 0.5, absAboveSlab.getY() + 0.05, absAboveSlab.getZ() + 0.5,
                    new ItemStack(Items.BEEF, 1));
            var beefOnSlab2 = new net.minecraft.world.entity.item.ItemEntity(
                    level, absAboveSlab.getX() + 0.5, absAboveSlab.getY() + 0.05, absAboveSlab.getZ() + 0.5,
                    new ItemStack(Items.BEEF, 1));
            beefOnSlab1.setDeltaMovement(0, 0, 0);
            beefOnSlab2.setDeltaMovement(0, 0, 0);
            beefOnSlab1.setNoPickUpDelay();
            beefOnSlab2.setNoPickUpDelay();
            level.addFreshEntity(beefOnSlab1);
            level.addFreshEntity(beefOnSlab2);

            helper.assertTrue(net.enderirt.smalllogictweaks.util.KitchenHelper.isOnActiveStove(beefOnSlab1),
                    "Vật phẩm đứng trên lớp phủ ở entityPos.below() phải kích hoạt isOnActiveStove.");

            // Tick nhiều lần để xác nhận ItemEntityMergeMixin ngăn gộp stack khi đang trên bếp
            for (int t = 0; t < 10; t++) {
                beefOnSlab1.tick();
                beefOnSlab2.tick();
            }
            helper.assertTrue(beefOnSlab1.isAlive() && beefOnSlab2.isAlive(),
                    "ItemEntityMergeMixin phải chặn 2 món đồ đang nướng gộp thành 1 stack.");

            // 3. Nguồn nhiệt ngay tại entityPos (đặt item ngay trên khối magma/fire)
            BlockPos directMagma = new BlockPos(4, 2, 1);
            helper.setBlock(directMagma, Blocks.MAGMA_BLOCK);
            BlockPos absDirect = helper.absolutePos(directMagma);
            var porkInside = new net.minecraft.world.entity.item.ItemEntity(
                    level, absDirect.getX() + 0.5, absDirect.getY() + 0.2, absDirect.getZ() + 0.5,
                    new ItemStack(Items.PORKCHOP, 1));
            int timerInside = net.enderirt.smalllogictweaks.handler.AestheticKitchenHandler.tickCooking(porkInside, 0);
            helper.assertTrue(timerInside == 1, "Nguồn nhiệt tại entityPos phải tăng timer cooking lên 1.");

            // 4. Tắt ENABLE_DRY_ROASTING hoặc ENABLE_AESTHETIC_KITCHEN -> giảm nhiệt timer
            cfg.ENABLE_DRY_ROASTING = false;
            helper.assertTrue(net.enderirt.smalllogictweaks.handler.AestheticKitchenHandler.tickCooking(porkInside, 10) == 9,
                    "Khi tắt ENABLE_DRY_ROASTING, timer đang nấu phải giảm dần.");
            cfg.ENABLE_AESTHETIC_KITCHEN = false;
            helper.assertTrue(net.enderirt.smalllogictweaks.handler.AestheticKitchenHandler.tickCooking(porkInside, 5) == 4,
                    "Khi tắt ENABLE_AESTHETIC_KITCHEN, timer phải giảm dần.");

            // 5. getCookTime edge cases: item không nằm trong map, ngoài biên trong map, magmaCookTimes=null
            cfg.magmaCookTimes.put("minecraft:diamond", 999999);
            helper.assertTrue(cfg.getCookTime(Items.DIAMOND) == cfg.DEFAULT_COOK_TIME,
                    "Giá trị ngoài biên trong magmaCookTimes phải rơi về DEFAULT_COOK_TIME.");
            cfg.magmaCookTimes = null;
            helper.assertTrue(cfg.getCookTime(Items.BEEF) == cfg.DEFAULT_COOK_TIME,
                    "Khi magmaCookTimes null, getCookTime phải trả DEFAULT_COOK_TIME.");

            // 6. getCookedResult với override sai/AIR và fallback smelting recipe vanilla
            cfg.aestheticCookResults.put("minecraft:beef", "minecraft:air");
            net.enderirt.smalllogictweaks.util.KitchenHelper.clearCookableCache();
            ItemStack cookedFallback = SmallLogicTweaksConfig.getCookedResult(level, new ItemStack(Items.BEEF));
            helper.assertTrue(cookedFallback.is(Items.COOKED_BEEF),
                    "Override trỏ tới AIR phải fallback về công thức smelting vanilla ra COOKED_BEEF.");
            helper.assertTrue(net.enderirt.smalllogictweaks.util.KitchenHelper.getCookedResult(level, ItemStack.EMPTY).isEmpty(),
                    "getCookedResult với ItemStack.EMPTY phải trả EMPTY.");

            // isOnActiveStove trả false khi nằm trên khối thường (không có nguồn nhiệt)
            BlockPos plainStone = new BlockPos(6, 2, 1);
            helper.setBlock(plainStone, Blocks.STONE);
            BlockPos absPlainStone = helper.absolutePos(plainStone);
            var coldBeef = new net.minecraft.world.entity.item.ItemEntity(
                    level, absPlainStone.getX() + 0.5, absPlainStone.getY() + 1.1, absPlainStone.getZ() + 0.5,
                    new ItemStack(Items.BEEF, 1));
            helper.assertFalse(net.enderirt.smalllogictweaks.util.KitchenHelper.isOnActiveStove(coldBeef),
                    "Vật phẩm không ở trên bếp nhiệt phải trả isOnActiveStove=false.");
        } finally {
            cfg.ENABLE_AESTHETIC_KITCHEN = origKitchen;
            cfg.ENABLE_DRY_ROASTING = origDry;
            cfg.ENABLE_CAULDRON_BOILING = origCauldron;
            cfg.magmaCookTimes = origMap;
            cfg.aestheticCookResults = origOverrides;
            net.enderirt.smalllogictweaks.util.KitchenHelper.clearCookableCache();
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 10)
    public void testBoneMealEdgeCasesAndMyceliumSource(GameTestHelper helper) {
        var cfg = SmallLogicTweaksConfig.ACTIVE_INSTANCE;
        boolean origAllDirt = cfg.ALLOW_ALL_DIRT_TYPES;
        boolean origRequire = cfg.REQUIRE_NEIGHBOR_SOURCE;
        ServerLevel level = helper.getLevel();

        try {
            Player survival = helper.makeMockPlayer(GameType.SURVIVAL);

            // 1. Chặn phía trên bằng khối đặc hoặc nước -> PASS
            BlockPos blockedDirt = new BlockPos(1, 2, 1);
            helper.setBlock(blockedDirt, Blocks.DIRT);
            helper.setBlock(blockedDirt.above(), Blocks.STONE);
            survival.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL, 8));
            BlockPos absBlocked = helper.absolutePos(blockedDirt);
            helper.assertTrue(net.enderirt.smalllogictweaks.handler.BoneMealHandler.handleBoneMeal(
                    level, survival, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absBlocked), Direction.UP, absBlocked, false)) == InteractionResult.PASS,
                    "Đất bị khối đặc che phía trên không được phép bón bột xương.");

            BlockPos floodedDirt = new BlockPos(1, 2, 3);
            helper.setBlock(floodedDirt, Blocks.DIRT);
            helper.setBlock(floodedDirt.above(), Blocks.WATER);
            BlockPos absFlooded = helper.absolutePos(floodedDirt);
            helper.assertTrue(net.enderirt.smalllogictweaks.handler.BoneMealHandler.handleBoneMeal(
                    level, survival, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absFlooded), Direction.UP, absFlooded, false)) == InteractionResult.PASS,
                    "Đất có nước phía trên không được phép bón bột xương.");

            // 2. ALLOW_ALL_DIRT_TYPES = true + REQUIRE_NEIGHBOR_SOURCE = false trên Coarse Dirt
            cfg.ALLOW_ALL_DIRT_TYPES = true;
            cfg.REQUIRE_NEIGHBOR_SOURCE = false;
            BlockPos coarsePos = new BlockPos(3, 2, 1);
            helper.setBlock(coarsePos, Blocks.COARSE_DIRT);
            BlockPos absCoarse = helper.absolutePos(coarsePos);
            helper.assertTrue(net.enderirt.smalllogictweaks.handler.BoneMealHandler.handleBoneMeal(
                    level, survival, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absCoarse), Direction.UP, absCoarse, false)) == InteractionResult.SUCCESS,
                    "COARSE_DIRT phải chuyển hóa thành công khi ALLOW_ALL_DIRT_TYPES=true.");
            helper.assertBlockPresent(Blocks.GRASS_BLOCK, coarsePos);

            // 3. REQUIRE_NEIGHBOR_SOURCE = true với lân cận MYCELIUM (đặt cao trên nền đá để không chạm cỏ sàn) -> biến thành MYCELIUM
            cfg.REQUIRE_NEIGHBOR_SOURCE = true;
            helper.setBlock(new BlockPos(6, 2, 3), Blocks.STONE);
            helper.setBlock(new BlockPos(7, 2, 3), Blocks.STONE);
            BlockPos dirtNearMycelium = new BlockPos(6, 3, 3);
            helper.setBlock(dirtNearMycelium, Blocks.DIRT);
            helper.setBlock(new BlockPos(7, 3, 3), Blocks.MYCELIUM);
            BlockPos absMyc = helper.absolutePos(dirtNearMycelium);
            helper.assertTrue(net.enderirt.smalllogictweaks.handler.BoneMealHandler.handleBoneMeal(
                    level, survival, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absMyc), Direction.UP, absMyc, false)) == InteractionResult.SUCCESS,
                    "Đất cạnh Mycelium phải biến thành Mycelium.");
            helper.assertBlockPresent(Blocks.MYCELIUM, dirtNearMycelium);

            // 4. Spectator bị chặn
            Player spectator = helper.makeMockPlayer(GameType.SPECTATOR);
            spectator.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL));
            helper.setBlock(dirtNearMycelium, Blocks.DIRT);
            helper.assertTrue(net.enderirt.smalllogictweaks.handler.BoneMealHandler.handleBoneMeal(
                    level, spectator, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absMyc), Direction.UP, absMyc, false)) == InteractionResult.PASS,
                    "Spectator không được phép dùng BoneMealHandler.");
        } finally {
            cfg.ALLOW_ALL_DIRT_TYPES = origAllDirt;
            cfg.REQUIRE_NEIGHBOR_SOURCE = origRequire;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void testConcreteSplashNonWaterAndInventoryFullDrop(GameTestHelper helper) {
        var cfg = SmallLogicTweaksConfig.ACTIVE_INSTANCE;
        boolean origSplash = cfg.ENABLE_SPLASH_HARDENING;
        ServerLevel level = helper.getLevel();

        try {
            Block yellowPowder = Blocks.CONCRETE_POWDER.pick(net.minecraft.world.item.DyeColor.YELLOW);
            BlockPos powderPos = new BlockPos(1, 2, 1);
            helper.setBlock(powderPos, yellowPowder);
            BlockPos absPowder = helper.absolutePos(powderPos);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absPowder), Direction.UP, absPowder, false);

            // 1. Awkward potion (không phải WATER) và Spectator đều phải trả PASS
            Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
            survival.setItemInHand(InteractionHand.MAIN_HAND,
                    PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
            helper.assertTrue(net.enderirt.smalllogictweaks.handler.ConcreteHardeningHandler.handleWaterBottle(
                    level, survival, InteractionHand.MAIN_HAND, hit) == InteractionResult.PASS,
                    "Chai Awkward Potion không được làm cứng bột bê tông.");

            Player spectator = helper.makeMockPlayer(GameType.SPECTATOR);
            spectator.setItemInHand(InteractionHand.MAIN_HAND,
                    PotionContents.createItemStack(Items.POTION, Potions.WATER));
            helper.assertTrue(net.enderirt.smalllogictweaks.handler.ConcreteHardeningHandler.handleWaterBottle(
                    level, spectator, InteractionHand.MAIN_HAND, hit) == InteractionResult.PASS,
                    "Spectator không được phép hóa cứng bột bê tông.");

            // 2. Thùng đồ đầy + stack gồm 2 bình nước -> rơi chai thủy tinh rỗng ra thế giới
            ServerPlayer sp = createConnectedServerPlayer(level, "BottleDropTester");
            sp.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            sp.setPos(absPowder.getX() + 0.5, absPowder.getY() + 1.0, absPowder.getZ() + 0.5);
            for (int slot = 1; slot < sp.getInventory().getContainerSize(); slot++) {
                sp.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            }
            ItemStack multiWater = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            multiWater.setCount(2);
            sp.setItemInHand(InteractionHand.MAIN_HAND, multiWater);
            helper.assertTrue(net.enderirt.smalllogictweaks.handler.ConcreteHardeningHandler.handleWaterBottle(
                    level, sp, InteractionHand.MAIN_HAND, hit) == InteractionResult.SUCCESS,
                    "Hóa cứng khi đầy túi đồ phải trả SUCCESS.");
            helper.assertTrue(sp.getMainHandItem().getCount() == 1,
                    "Stack 2 bình nước phải giảm xuống còn 1 bình.");
            var droppedBottles = level.getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(absPowder).inflate(3.0),
                    e -> e.getItem().is(Items.GLASS_BOTTLE));
            helper.assertFalse(droppedBottles.isEmpty(),
                    "Chai thủy tinh rỗng phải được rơi ra đất khi túi đồ đầy.");

            // 3. ThrownPotionMixin: Splash Poison Potion KHÔNG được hóa cứng bột bê tông
            BlockPos splashTarget = new BlockPos(4, 2, 1);
            helper.setBlock(splashTarget, yellowPowder);
            BlockPos absSplash = helper.absolutePos(splashTarget);
            AbstractThrownPotion poisonPotion = new AbstractThrownPotion(net.minecraft.world.entity.EntityTypes.SPLASH_POTION, level) {
                @Override
                protected void onHitAsPotion(ServerLevel sLevel, ItemStack item, HitResult result) {}
                @Override
                protected Item getDefaultItem() { return Items.SPLASH_POTION; }
            };
            poisonPotion.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.POISON));
            poisonPotion.setPos(absSplash.getX() + 0.5, absSplash.getY() + 1.0, absSplash.getZ() + 0.5);
            poisonPotion.setDeltaMovement(0, -0.5, 0);
            level.addFreshEntity(poisonPotion);
            for (int t = 0; t < 20 && poisonPotion.isAlive(); t++) {
                poisonPotion.tick();
            }

            // 4. Khi tắt ENABLE_SPLASH_HARDENING, Splash Water Potion cũng không được hóa cứng
            BlockPos disabledTarget = new BlockPos(6, 2, 1);
            helper.setBlock(disabledTarget, yellowPowder);
            BlockPos absDisabled = helper.absolutePos(disabledTarget);
            cfg.ENABLE_SPLASH_HARDENING = false;
            AbstractThrownPotion disabledWaterPotion = new AbstractThrownPotion(net.minecraft.world.entity.EntityTypes.SPLASH_POTION, level) {
                @Override
                protected void onHitAsPotion(ServerLevel sLevel, ItemStack item, HitResult result) {}
                @Override
                protected Item getDefaultItem() { return Items.SPLASH_POTION; }
            };
            disabledWaterPotion.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER));
            disabledWaterPotion.setPos(absDisabled.getX() + 0.5, absDisabled.getY() + 1.0, absDisabled.getZ() + 0.5);
            disabledWaterPotion.setDeltaMovement(0, -0.5, 0);
            level.addFreshEntity(disabledWaterPotion);
            for (int t = 0; t < 20 && disabledWaterPotion.isAlive(); t++) {
                disabledWaterPotion.tick();
            }

            helper.assertBlockPresent(yellowPowder, splashTarget);
            helper.assertBlockPresent(yellowPowder, disabledTarget);
        } finally {
            cfg.ENABLE_SPLASH_HARDENING = origSplash;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 10)
    public void testDirtReversionGuardsAndBystanderEntityLift(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos farmPos = new BlockPos(2, 2, 2);
        helper.setBlock(farmPos, Blocks.FARMLAND);
        BlockPos absFarm = helper.absolutePos(farmPos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absFarm), Direction.UP, absFarm, false);

        // 1. Công cụ không phải cúp, khối không hợp lệ, Spectator đều trả PASS
        Player spectator = helper.makeMockPlayer(GameType.SPECTATOR);
        spectator.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(net.enderirt.smalllogictweaks.handler.DirtReversionHandler.handlePickaxeReversion(
                level, spectator, InteractionHand.MAIN_HAND, hit) == InteractionResult.PASS,
                "Spectator phải trả PASS.");

        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        survival.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
        helper.assertTrue(net.enderirt.smalllogictweaks.handler.DirtReversionHandler.handlePickaxeReversion(
                level, survival, InteractionHand.MAIN_HAND, hit) == InteractionResult.PASS,
                "Xẻng không phải cúp phải trả PASS.");

        BlockPos stonePos = new BlockPos(4, 2, 2);
        helper.setBlock(stonePos, Blocks.STONE);
        BlockPos absStone = helper.absolutePos(stonePos);
        survival.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(net.enderirt.smalllogictweaks.handler.DirtReversionHandler.handlePickaxeReversion(
                level, survival, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absStone), Direction.UP, absStone, false)) == InteractionResult.PASS,
                "Đá không thuộc #pickaxe_reversion phải trả PASS.");

        // 2. Thực thể đứng lún trên Farmland (y = absFarm.getY() + 0.90) phải được nâng lên y = absFarm.getY() + 1.0
        var sheep = net.minecraft.world.entity.EntityTypes.SHEEP.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        helper.assertTrue(sheep != null, "Không tạo được Sheep test.");
        sheep.setPos(absFarm.getX() + 0.5, absFarm.getY() + 0.90, absFarm.getZ() + 0.5);
        sheep.setDeltaMovement(0, -0.2, 0);
        level.addFreshEntity(sheep);

        helper.assertTrue(net.enderirt.smalllogictweaks.handler.DirtReversionHandler.handlePickaxeReversion(
                level, survival, InteractionHand.MAIN_HAND, hit) == InteractionResult.SUCCESS,
                "Cúp chuyển Farmland thành Dirt phải trả SUCCESS.");
        helper.assertBlockPresent(Blocks.DIRT, farmPos);
        helper.assertTrue(sheep.getY() >= absFarm.getY() + 1.0 - 0.001,
                "Thực thể lân cận trên ô đất phải được bù tọa độ Y lên >= targetY.");

        helper.succeed();
    }

    @GameTest(maxTicks = 10)
    public void testEndPhantomMixinsAndTickGuards(GameTestHelper helper) throws Exception {
        var cfg = SmallLogicTweaksConfig.ACTIVE_INSTANCE;
        boolean origPhantom = cfg.ENABLE_END_PHANTOM;
        int origMin = cfg.PHANTOM_MIN_COUNT;
        int origMax = cfg.PHANTOM_MAX_COUNT;
        ServerLevel overworld = helper.getLevel();
        ServerLevel endLevel = overworld.getServer().getLevel(Level.END);
        boolean origSpawnRule = overworld.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_PHANTOMS);

        try {
            cfg.ENABLE_END_PHANTOM = true;

            // 1. ServerPlayerMixin: chặn awardStat TIME_SINCE_REST ở Overworld, nhưng cho phép ở The End
            com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "MixinPhantomTester");
            ServerPlayer overworldPlayer = new ServerPlayer(overworld.getServer(), overworld, profile, net.minecraft.server.level.ClientInformation.createDefault());
            overworldPlayer.awardStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 500);
            helper.assertTrue(overworldPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) == 0,
                    "ServerPlayerMixin phải chặn TIME_SINCE_REST tăng ở Overworld khi bật ENABLE_END_PHANTOM.");

            if (endLevel != null) {
                ServerPlayer endPlayer = new ServerPlayer(overworld.getServer(), endLevel,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "EndStatTester"),
                        net.minecraft.server.level.ClientInformation.createDefault());
                endPlayer.awardStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 500);
                helper.assertTrue(endPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) == 500,
                        "ServerPlayerMixin phải cho phép TIME_SINCE_REST tăng trong The End.");

                // handleEndTick khi SPAWN_PHANTOMS=false và khi player Creative
                endLevel.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_PHANTOMS, false, overworld.getServer());
                net.enderirt.smalllogictweaks.handler.EndPhantomHandler.handleEndTick(endLevel);
                endLevel.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_PHANTOMS, true, overworld.getServer());
                net.enderirt.smalllogictweaks.handler.EndPhantomHandler.handleEndTick(endLevel);

                // Overload 2 tham số executePhantomSpawnLogic(level, player)
                net.enderirt.smalllogictweaks.handler.EndPhantomHandler.executePhantomSpawnLogic(endLevel, endPlayer);
            }

            // 2. PhantomSpawnerMixin: khi ENABLE_END_PHANTOM=true ở Overworld, tick bị cancel ở HEAD (nextTick vẫn = 0)
            overworld.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_PHANTOMS, true, overworld.getServer());
            var spawner = new net.minecraft.world.level.levelgen.PhantomSpawner();
            var nextTickField = net.minecraft.world.level.levelgen.PhantomSpawner.class.getDeclaredField("nextTick");
            nextTickField.setAccessible(true);
            spawner.tick(overworld, true);
            helper.assertTrue(nextTickField.getInt(spawner) == 0,
                    "PhantomSpawnerMixin phải hủy tick ở Overworld khi ENABLE_END_PHANTOM=true (nextTick vẫn = 0).");

            // Khi tắt ENABLE_END_PHANTOM, PhantomSpawner.tick chạy bình thường và đặt nextTick > 0
            cfg.ENABLE_END_PHANTOM = false;
            spawner.tick(overworld, true);
            helper.assertTrue(nextTickField.getInt(spawner) > 0,
                    "Khi tắt ENABLE_END_PHANTOM, PhantomSpawner.tick phải chạy bình thường và cập nhật nextTick > 0.");

            // ServerPlayerMixin khi tắt ENABLE_END_PHANTOM cũng cho phép tăng stat bình thường
            overworldPlayer.awardStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 250);
            helper.assertTrue(overworldPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) == 250,
                    "Khi tắt ENABLE_END_PHANTOM, TIME_SINCE_REST phải tăng bình thường ở Overworld.");
        } finally {
            cfg.ENABLE_END_PHANTOM = origPhantom;
            cfg.PHANTOM_MIN_COUNT = origMin;
            cfg.PHANTOM_MAX_COUNT = origMax;
            overworld.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_PHANTOMS, origSpawnRule, overworld.getServer());
            if (endLevel != null) {
                endLevel.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_PHANTOMS, origSpawnRule, overworld.getServer());
            }
        }
        helper.succeed();
    }
}