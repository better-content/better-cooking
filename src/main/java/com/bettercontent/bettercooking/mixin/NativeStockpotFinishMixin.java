package com.bettercontent.bettercooking.mixin;
import com.bettercontent.bettercooking.finishing.NativeFinishing;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.container.StockpotContainer;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.serializer.StockpotRecipeSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StockpotBlockEntity.class)
public abstract class NativeStockpotFinishMixin {
    @Shadow(remap=false) private ItemStack result;
    @Shadow(remap=false) private ResourceLocation recipeId;
    @Shadow(remap=false) public abstract StockpotContainer getContainer();
    /** Quality is already final; paid inputs have NOT yet been cleared. */
    @Inject(method="tick(Lnet/minecraft/world/level/Level;)V",remap=false,
        at=@At(value="INVOKE",target="Lnet/minecraft/core/NonNullList;clear()V",remap=false),require=1)
    private void betterCooking$carryFinalStockpot(Level level,CallbackInfo ci) {
        result=NativeFinishing.stockpot((StockpotBlockEntity)(Object)this,getContainer(),result,
            StockpotRecipeSerializer.EMPTY_ID.equals(recipeId)&&result.is(Items.SUSPICIOUS_STEW)&&result.getCount()==1,level.getGameTime());
    }
}
