package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.kitchen.KitchenHooks;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChoppingBoardBlockEntity.class)
public abstract class KitchenBoardMixin {
    @Inject(method = {"onPutItem", "onCutItem"}, at = @At("HEAD"), cancellable = true, remap = false, require = 2, expect = 2)
    private void betterCooking$assemblyExcludesNativeCutting(Level level, LivingEntity actor, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (KitchenHooks.occupied((BlockEntity) (Object) this)) cir.setReturnValue(false);
    }
}
