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

public class Gametest {

    @GameTest(maxTicks = 100)
    public void testConcreteHardening(GameTestHelper helper) {
        BlockPos targetPos = new BlockPos(1, 2, 1);
        helper.setBlock(targetPos, Blocks.RED_CONCRETE_POWDER);

        boolean hasHardened = SmallLogicTweaksEvents.tryHardenConcrete(helper.getLevel(), helper.absolutePos(targetPos));

        helper.assertTrue(hasHardened, "Hàm tryHardenConcrete phải trả về true");
        helper.assertBlockPresent(Blocks.RED_CONCRETE, targetPos);

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
                helper.setBlock(new BlockPos(x, 2, z), Blocks.RED_CONCRETE_POWDER);
            }
        }

        // Tọa độ tâm xác định là (1, 2, 1)
        BlockPos centerPos = new BlockPos(1, 2, 1);
        BlockPos absoluteCenter = helper.absolutePos(centerPos);

        // Khởi tạo bình Thuốc Ném Nước (Splash Potion of Water)
        ItemStack waterSplash = new ItemStack(Items.SPLASH_POTION);
        waterSplash.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(Potions.WATER));

        // 2. Sinh ra thực thể rơi tự do
        AbstractThrownPotion potionEntity = new AbstractThrownPotion(EntityType.SPLASH_POTION, helper.getLevel()) {
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
            helper.assertTrue(helper.getBlockState(centerPos).is(Blocks.RED_CONCRETE),
                    "Thuật toán AABB lỗi: Khối tâm điểm (Direct Hit) không được hóa cứng 100%.");

            // Kiểm tra các khối xung quanh, do tỷ lệ lan truyền là 40% (0.4f), ít nhất phải có 1 khối bị hóa cứng
            int hardenedCount = 0;
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    if (helper.getBlockState(new BlockPos(x, 2, z)).is(Blocks.RED_CONCRETE)) {
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
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.RED_CONCRETE_POWDER);
                }
            }

            // 2. Khởi tạo một thực thể ảo mới
            AbstractThrownPotion dummyPotion = new AbstractThrownPotion(EntityType.SPLASH_POTION, helper.getLevel()) {
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
                        if (helper.getBlockState(currentPos).is(Blocks.RED_CONCRETE)) {
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

        // Gọi hàm logic (Bây giờ đã chấp nhận tham số Player)
        SmallLogicTweaksEvents.executePhantomSpawnLogic(helper.getLevel(), mockPlayer);

        // Kiểm tra kết quả
        helper.succeedWhen(() -> {
            var spawnedPhantoms = helper.getLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.monster.Phantom.class,
                    mockPlayer.getBoundingBox().inflate(50.0, 50.0, 50.0)
            );

            helper.assertTrue(!spawnedPhantoms.isEmpty(), "Không có Phantom nào được sinh ra.");
            helper.assertTrue(spawnedPhantoms.size() >= 2, "Số lượng Phantom sinh ra ít hơn mức MIN_COUNT.");
        });
    }
}