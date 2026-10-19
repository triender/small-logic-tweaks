package net.enderirt.smalllogictweaks.mixin;

import net.enderirt.smalllogictweaks.handler.AestheticKitchenHandler;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    // Trường được truy cập qua reflection trong GameTest (testHeatDecayWhenMoved, testNonCookableItemNotRoasting)
    private int slt$magmaCookTimer = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ItemEntity entity = (ItemEntity) (Object) this;
        this.slt$magmaCookTimer = AestheticKitchenHandler.tickCooking(entity, this.slt$magmaCookTimer);
    }
}
