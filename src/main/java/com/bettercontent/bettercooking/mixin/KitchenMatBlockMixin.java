package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.kitchen.NativeKitchenHooks;
import com.chefmooon.ubesdelight.common.block.forge.BakingMatBlockImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BakingMatBlockImpl.class)
public abstract class KitchenMatBlockMixin {
    @Inject(method = "onRemove", at = @At("HEAD"), require = 1)
    private void betterCooking$recoverMatOnReplacement(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving, CallbackInfo ci) {
        NativeKitchenHooks.recoverOnRemove(state, level, pos, replacement);
    }
}
