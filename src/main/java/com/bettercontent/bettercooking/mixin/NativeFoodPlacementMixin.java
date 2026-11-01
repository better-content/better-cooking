package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.placed.NativeFoodServings;
import com.github.ysbbbbbb.kaleidoscopecookery.block.food.FoodBiteOneByTwoBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned 1x2 omits super; 3x3 calls super and must not get a second initialization hook. */
@Mixin(value = FoodBiteOneByTwoBlock.class, remap = false)
public abstract class NativeFoodPlacementMixin {
    @Inject(method = {"setPlacedBy", "m_6402_"}, at = @At("RETURN"), require = 1, remap = false)
    private void cooking$placedPair(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        NativeFoodServings.placedOneByTwo(level, pos, state, stack);
    }
}
