package net.enderirt.smalllogictweaks.mixin;

import net.enderirt.smalllogictweaks.util.KitchenHelper;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMergeMixin {

    @Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true)
    private void onTryToMerge(ItemEntity other, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (KitchenHelper.isOnActiveStove(self) || KitchenHelper.isOnActiveStove(other)) {
            ci.cancel();
        }
    }
}
