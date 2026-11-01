package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.placed.*;
import com.github.ysbbbbbb.kaleidoscopecookery.block.food.FoodBiteBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.item.quality.Quality;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = FoodBiteBlock.class, remap = false)
public abstract class NativeFoodBiteMixin implements NativeFoodEatAccess {
    @Shadow(remap = false) protected abstract InteractionResult eat(Level level, BlockPos pos, BlockState state, Player player);
    @Unique public InteractionResult cooking$eatOriginal(Level level, BlockPos pos, BlockState state, Player player) {
        return eat(level, pos, state, player);
    }
    @Redirect(method = "eat", at = @At(value = "INVOKE", target = "Lcom/github/ysbbbbbb/kaleidoscopecookery/item/quality/Quality;getRatio()D"), require = 1, remap = false)
    private double cooking$oneNativeQualityMultiplier(Quality quality) {
        return NativeFoodServings.inBiteScope() ? 1D : quality.getRatio();
    }
}
