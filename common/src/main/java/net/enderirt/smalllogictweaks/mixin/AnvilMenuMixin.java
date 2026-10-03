package net.enderirt.smalllogictweaks.mixin;

import net.enderirt.smalllogictweaks.SmallLogicTweaksConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * [VI] Tweak Anvil Preserved: Sửa chữa độ bền và đổi tên không bị phạt tăng Prior Work Penalty.
 *      Chỉ khi thực sự có bùa phép mới hoặc nâng cấp cấp độ bùa thì chi phí phạt mới tăng.
 * [EN] Anvil Preserved Tweak: Durability repair and renaming do not increase prior work penalty.
 *      Only actual new enchantments or upgraded enchantment levels incur an increased penalty.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {

    public AnvilMenuMixin(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(null, containerId, playerInventory, access, null);
    }

    // NeoForge moves vanilla's createResult() body into createResultInternal() and turns createResult() into a
    // wrapper that fires its anvil event; whichever selector does not exist on a loader is simply not matched.
    @Redirect(
            method = {"createResult", "createResultInternal"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    private <T> Object slt$preserveRepairCostUnlessUpgraded(ItemStack result, net.minecraft.core.component.DataComponentType<T> type, T value) {
        if (type == DataComponents.REPAIR_COST && SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_ANVIL_PRESERVED) {
            ItemStack input = this.inputSlots.getItem(0);
            ItemStack addition = this.inputSlots.getItem(1);

            if (!input.isEmpty() && !addition.isEmpty()) {
                // Kiểm tra xem có bùa phép nào thực sự được thêm mới hoặc tăng cấp hay không
                if (!slt$hasEnchantmentUpgraded(input, addition)) {
                    // Không có nâng cấp bùa phép (chỉ sửa độ bền hoặc đổi tên):
                    // Giữ nguyên mức repair cost cơ sở lớn nhất giữa 2 vật phẩm, không nhân đôi phạt
                    int baseCost = input.getOrDefault(DataComponents.REPAIR_COST, 0);
                    int additionCost = addition.getOrDefault(DataComponents.REPAIR_COST, 0);
                    int preservedCost = Math.max(baseCost, additionCost);

                    return result.set(DataComponents.REPAIR_COST, preservedCost);
                }
            }
        }
        return result.set(type, value);
    }

    private boolean slt$hasEnchantmentUpgraded(ItemStack input, ItemStack addition) {
        ItemEnchantments inputEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(input);
        ItemEnchantments additionEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(addition);

        if (additionEnchantments.isEmpty()) {
            return false;
        }

        for (var entry : additionEnchantments.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int additionLevel = entry.getIntValue();
            int currentLevel = inputEnchantments.getLevel(enchantment);

            if (currentLevel == 0) {
                // Bùa phép mới hoàn toàn được đưa vào
                if (enchantment.value().canEnchant(input) || input.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) {
                    return true;
                }
            } else if (additionLevel > currentLevel) {
                // Cấp độ bùa phép được nâng cao hơn
                return true;
            } else if (additionLevel == currentLevel && currentLevel < enchantment.value().getMaxLevel()) {
                // Hai bùa cùng cấp kết hợp lại để lên cấp cao hơn (ví dụ Sharpness III + Sharpness III -> Sharpness IV)
                return true;
            }
        }

        return false;
    }
}
