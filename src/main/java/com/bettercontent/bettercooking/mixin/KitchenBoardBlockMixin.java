package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.kitchen.NativeKitchenHooks;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.ChoppingBoardBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** KC 1.6.0 and Block inherit onRemove from BlockBehaviour; inject its actual declaration only. */
@Mixin(BlockBehaviour.class)
public abstract class KitchenBoardBlockMixin {
    @Inject(method = "onRemove(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V", at = @At("HEAD"), require = 1)
    private void betterCooking$recoverBoardOnReplacement(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving, CallbackInfo ci) {
        if ((Object) this instanceof ChoppingBoardBlock) NativeKitchenHooks.recoverOnRemove(state, level, pos, replacement);
    }
}
