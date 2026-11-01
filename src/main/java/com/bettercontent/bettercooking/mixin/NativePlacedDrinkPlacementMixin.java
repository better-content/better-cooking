package com.bettercontent.bettercooking.mixin;
import com.bettercontent.bettercooking.drinks.placed.PlacedDrinks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;

@Mixin(BlockItem.class)
public abstract class NativePlacedDrinkPlacementMixin {
    @Inject(method="place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",at=@At("HEAD"),require=1)
    private void cooking$validateBeforeWorldPlacement(BlockPlaceContext context,CallbackInfoReturnable<InteractionResult> callback){PlacedDrinks.validatePlacement(context.getLevel(),context.getItemInHand());}
    @Redirect(method="place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V"),require=1)
    private void cooking$actualPlacedOriginal(Block block,Level level,BlockPos pos,BlockState state,LivingEntity actor,ItemStack input){
        ItemStack original=PlacedDrinks.known(state)?input.copy():ItemStack.EMPTY;
        try{block.setPlacedBy(level,pos,state,actor,input);}finally{if(!original.isEmpty())PlacedDrinks.placed(level,pos,state,original);}
    }
}
