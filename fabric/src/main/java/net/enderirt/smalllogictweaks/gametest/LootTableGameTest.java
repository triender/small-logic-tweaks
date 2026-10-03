package net.enderirt.smalllogictweaks.gametest;

import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class LootTableGameTest {

    /**
     * Giả lập hành vi phá hủy 2000 khối lá rừng bằng cách gọi thẳng API trích xuất Loot.
     * Phương pháp này không tạo thực thể (ItemEntity), không gây lag ánh sáng và cho kết quả tức thì.
     */
    @GameTest(maxTicks = 10)
    public void testJungleLeavesDropRate(GameTestHelper helper) {
        ServerLevel serverLevel = helper.getLevel();
        BlockPos absolutePos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState jungleLeavesState = Blocks.JUNGLE_LEAVES.defaultBlockState();

        int iterations = 100000;
        int saplingCount = 0;

        for (int i = 0; i < iterations; i++) {
            // Xây dựng ngữ cảnh rơi đồ: Giả lập việc khối bị phá tại tọa độ, đập bằng tay không
            LootParams.Builder builder = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(absolutePos))
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY);

            // Truy xuất trực tiếp danh sách vật phẩm rơi ra từ RAM
            List<ItemStack> drops = jungleLeavesState.getDrops(builder);

            for (ItemStack stack : drops) {
                if (stack.is(Items.JUNGLE_SAPLING)) {
                    saplingCount += stack.getCount();
                }
            }
        }

        // Tính toán tỷ lệ rơi vật phẩm thực tế
        double actualDropRate = (double) saplingCount / iterations;

        if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_JUNGLE_SUSTAINABILITY) {
            // Tỷ lệ kỳ vọng là 5% (0.05). Cho phép sai số dao động an toàn từ 4% đến 6%.
            helper.assertTrue(actualDropRate >= 0.04 && actualDropRate <= 0.06,
                    "Loot Table Error: Drop rate should be ~5% when enabled, but was " + (actualDropRate * 100) + "%. " +
                            "This indicates the modded loot table failed to load.");
        } else {
            // Tỷ lệ Vanilla kỳ vọng là 2.5% (0.025). Cho phép sai số dao động an toàn từ 1.5% đến 3.5%.
            helper.assertTrue(actualDropRate >= 0.015 && actualDropRate <= 0.035,
                    "Loot Table Error: Drop rate should be ~2.5% (Vanilla) when disabled, but was " + (actualDropRate * 100) + "%. " +
                            "This indicates the modded loot table was incorrectly loaded.");
        }

        helper.succeed();
    }
}