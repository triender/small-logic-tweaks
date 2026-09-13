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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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
        // Kiểm tra xem Khoai tây độc đã được nạp thành công vào bộ nhớ của Thùng ủ phân chưa
        boolean isRegistered = ComposterBlock.COMPOSTABLES.containsKey(Items.POISONOUS_POTATO);

        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_POISONOUS_POTATO_COMPOST) {
            // Xác thực trạng thái tồn tại
            helper.assertTrue(isRegistered,
                    "Lỗi Registry: Cấu hình ENABLE_POISONOUS_POTATO_COMPOST đang bật nhưng vật phẩm không được đăng ký vào Thùng ủ phân.");

            // Xác thực tính toàn vẹn của logic thiết kế
            float compostChance = ComposterBlock.COMPOSTABLES.getFloat(Items.POISONOUS_POTATO);
            helper.assertTrue(compostChance == 0.65F,
                    "Lỗi Logic: Tỷ lệ ủ phân bị sai lệch. Kỳ vọng 0.65, thực tế là: " + compostChance);
        } else {
            // Xác thực quá trình tắt tính năng
            helper.assertFalse(isRegistered,
                    "Lỗi Registry: Cấu hình đã tắt nhưng Khoai tây độc vẫn bị nạp trái phép vào hệ thống ủ phân.");
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

        // Gọi hàm logic (Bây giờ đã chấp nhận tham số Player)
        SmallLogicTweaksEvents.executePhantomSpawnLogic(helper.getLevel(), mockPlayer);

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
        var potionBrewing = helper.getLevel().potionBrewing();
        ItemStack inputStack = new ItemStack(Items.POTION);
        inputStack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(Potions.AWKWARD));
        
        // Mix thử nguyên liệu Khoai tây độc với thuốc Awkward
        ItemStack resultStack = potionBrewing.mix(new ItemStack(Items.POISONOUS_POTATO), inputStack);
        var resultContents = resultStack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        
        helper.assertTrue(resultContents != null && resultContents.is(Potions.POISON),
                "Lỗi công thức nấu: Khoai tây độc nấu với Awkward Potion không sinh ra Poison Potion.");
        
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
            SmallLogicTweaksEvents.executePhantomSpawnLogic(helper.getLevel(), mockPlayer);
            var phantomsBefore = helper.getLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.monster.Phantom.class,
                    helper.getBounds().inflate(12.0, 45.0, 12.0)
            );
            helper.assertTrue(phantomsBefore.isEmpty(), "Lỗi: Không được sinh Phantom khi chưa mang Elytra (240k < 300k).");
            
            // 2. Mang Elytra ở slot Chest -> Có spawn (240k >= 72k)
            mockPlayer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            SmallLogicTweaksEvents.executePhantomSpawnLogic(helper.getLevel(), mockPlayer);
            
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
                
                potato.setPos(absTrapdoorPos.getX() + 10.0, absTrapdoorPos.getY() + 5.0, absTrapdoorPos.getZ() + 10.0);
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
}