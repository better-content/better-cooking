package com.bettercontent.bettercooking.mixin;
import com.bettercontent.bettercooking.drinks.placed.PlacedDrinks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Typed whole-call wrapper, never HEAD/RETURN ThreadLocal lifetime. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class NativePlacedDrinkUseScopeMixin {
    @Redirect(method="use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;use(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"),require=1)
    private InteractionResult cooking$scopedNativeCupUse(Block block,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        return PlacedDrinks.use(state,level,pos,player,hand,()->block.use(state,level,pos,player,hand,hit));
    }
}
