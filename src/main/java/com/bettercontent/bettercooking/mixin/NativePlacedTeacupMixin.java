package com.bettercontent.bettercooking.mixin;
import com.bettercontent.bettercooking.drinks.placed.PlacedDrinks;
import com.github.ysbbbbbb.kaleidoscopecookery.block.drink.TeacupBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.util.ItemUtils;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TeacupBlock.class)
public abstract class NativePlacedTeacupMixin {
    @Inject(method="use(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",at=@At("HEAD"),require=1)
    private void cooking$requireExactUse(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit,CallbackInfoReturnable<InteractionResult> callback){PlacedDrinks.requireUseScope(state,level,pos,player,hand);}
    @Redirect(method="use(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
        at=@At(value="INVOKE",target="Lcom/github/ysbbbbbb/kaleidoscopecookery/util/ItemUtils;getItemToLivingEntity(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",remap=false),require=2)
    private void cooking$actualTeaPickup(LivingEntity entity,ItemStack candidate,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){ItemUtils.getItemToLivingEntity(entity,PlacedDrinks.pickup(level,pos,state,player,candidate));}
    @Redirect(method="use(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;popResourceFromFace(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/item/ItemStack;)V"),require=1)
    private void cooking$actualTeaFaceDrop(Level level,BlockPos pos,Direction face,ItemStack candidate,BlockState state,Level targetLevel,BlockPos targetPos,Player player,InteractionHand hand,BlockHitResult hit){Block.popResourceFromFace(level,pos,face,PlacedDrinks.pickup(level,pos,state,player,candidate));}
    @Inject(method="getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;",at=@At("RETURN"),cancellable=true,require=1)
    private void cooking$actualFilledDrops(BlockState state,LootParams.Builder params,CallbackInfoReturnable<List<ItemStack>> callback){callback.setReturnValue(PlacedDrinks.drops(state,params,callback.getReturnValue()));}
}
