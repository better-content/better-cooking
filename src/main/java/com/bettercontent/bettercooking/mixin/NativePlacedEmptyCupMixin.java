package com.bettercontent.bettercooking.mixin;
import com.bettercontent.bettercooking.drinks.placed.PlacedDrinks;
import com.github.ysbbbbbb.kaleidoscopecookery.block.drink.EmptyCupBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Validation only: direct native empty-cup calls cannot consume a managed teapot and silently lose its receipt. */
@Mixin(EmptyCupBlock.class)
public abstract class NativePlacedEmptyCupMixin {
    @Inject(method="use(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",at=@At("HEAD"),require=1)
    private void cooking$requireActualUseScope(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit,CallbackInfoReturnable<InteractionResult> callback){PlacedDrinks.requireUseScope(state,level,pos,player,hand);}
}
