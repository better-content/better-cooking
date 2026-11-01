package com.bettercontent.bettercooking.mixin;
import com.bettercontent.bettercooking.drinks.placed.PlacedDrinks;
import com.github.ysbbbbbb.kaleidoscopecookery.block.drink.ClayPotMilkTeaBlock;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Milk inherits this actual declaring loot method. Never clear the detached old BE before vanilla loot reads it. */
@Mixin(BlockBehaviour.class)
public abstract class NativePlacedDrinkWorldMixin {
    @Inject(method="getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;",at=@At("RETURN"),cancellable=true,require=1)
    private void cooking$actualMilkDrops(BlockState state,LootParams.Builder params,CallbackInfoReturnable<List<ItemStack>> callback){
        if(state.getBlock() instanceof ClayPotMilkTeaBlock)callback.setReturnValue(PlacedDrinks.drops(state,params,callback.getReturnValue()));
    }
}
