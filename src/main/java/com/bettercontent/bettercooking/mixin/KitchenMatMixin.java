package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.kitchen.KitchenHooks;
import com.bettercontent.bettercooking.kitchen.NativeKitchenHooks;
import com.chefmooon.ubesdelight.common.block.entity.forge.BakingMatBlockEntityImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BakingMatBlockEntityImpl.class)
public abstract class KitchenMatMixin {
    @Inject(method = "addItem", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void betterCooking$assemblyExcludesNativeIngredients(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (KitchenHooks.occupied((BlockEntity) (Object) this)) cir.setReturnValue(false);
    }
    @Inject(method = "processItemUsingTool", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void betterCooking$assemblyExcludesNativeProcessing(ItemStack stack, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (KitchenHooks.occupied((BlockEntity) (Object) this)) cir.setReturnValue(false);
    }
    @Inject(method = "createHandler", at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private void betterCooking$guardCachedNativeInventory(CallbackInfoReturnable<ItemStackHandler> cir) {
        cir.setReturnValue(NativeKitchenHooks.guardNativeInventory((BlockEntity) (Object) this, cir.getReturnValue()));
    }
}
