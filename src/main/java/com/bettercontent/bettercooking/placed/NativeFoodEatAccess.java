package com.bettercontent.bettercooking.placed;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Implemented by the base FoodBite mixin; a typed forwarder to its real protected eat method. */
public interface NativeFoodEatAccess {
    InteractionResult cooking$eatOriginal(Level level, BlockPos pos, BlockState state, Player player);
}
