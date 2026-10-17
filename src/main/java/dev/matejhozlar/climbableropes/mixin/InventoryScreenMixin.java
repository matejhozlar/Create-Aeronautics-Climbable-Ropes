package dev.matejhozlar.climbableropes.mixin;

import dev.matejhozlar.climbableropes.client.rig.ClimbRigs;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The inventory preview fakes the body yaw and has no rope to hold, so it gets the vanilla pose.
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {

    @Inject(method = "renderEntityInInventory", at = @At("HEAD"))
    private static void climbableRopes$beginPreview(CallbackInfo ci) {
        ClimbRigs.pushSuppression();
    }

    @Inject(method = "renderEntityInInventory", at = @At("RETURN"))
    private static void climbableRopes$endPreview(CallbackInfo ci) {
        ClimbRigs.popSuppression();
    }
}
