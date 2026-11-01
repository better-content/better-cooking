package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.CookingItems;
import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.stockpot.PreparedStockpotStateAccess;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.item.RecipeItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RecipeItem otherwise debits by ITEM identity before its void addAllIngredients callback! */
@Mixin(value=RecipeItem.class,remap=false)
public abstract class PreparedStockpotRecipeItemMixin {
    @Inject(method="onPutRecipe(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/InteractionResult;",
        at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void cooking$beforeRecipeItemDebit(BlockEntity owner, Player player, ItemStack card, CallbackInfoReturnable<InteractionResult> cir) {
        if(!(owner instanceof StockpotBlockEntity)) return;
        if(((PreparedStockpotStateAccess)owner).cooking$isPreparedStockpotSealed()) {
            cir.setReturnValue(InteractionResult.FAIL);return;
        }
        if(CookingMod.discoveryOnly()) return;
        RecipeItem.RecipeRecord record=RecipeItem.getRecipe(card);
        if(record!=null&&record.type().equals(RecipeItem.STOCKPOT)
            &&record.input().stream().anyMatch(s->s.is(CookingItems.PREPARED_BATCH.get()))) cir.setReturnValue(InteractionResult.FAIL);
    }
}
