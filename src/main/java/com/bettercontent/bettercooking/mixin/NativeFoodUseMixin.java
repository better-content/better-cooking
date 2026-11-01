package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.placed.NativeFoodServings;
import com.github.ysbbbbbb.kaleidoscopecookery.block.food.FoodBiteBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Both client-probe and server call sites in the actual base use method are covered. */
@Mixin(value = FoodBiteBlock.class, remap = false)
public abstract class NativeFoodUseMixin {
    @Redirect(method = {"use", "m_6227_"}, at = @At(value = "INVOKE", target = "Lcom/github/ysbbbbbb/kaleidoscopecookery/block/food/FoodBiteBlock;eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/InteractionResult;"), require = 2, remap = false)
    private InteractionResult cooking$nativeBite(FoodBiteBlock food, Level level, BlockPos pos, BlockState state, Player player) {
        return NativeFoodServings.eat(food, level, pos, state, player);
    }
}
