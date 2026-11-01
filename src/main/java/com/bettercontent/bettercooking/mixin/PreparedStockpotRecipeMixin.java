package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.recipe.PreparedStockpotRecipe;
import com.bettercontent.bettercooking.recipe.PreparedStockpotSerializer;
import com.bettercontent.bettercooking.stockpot.PreparedStockpotAccess;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.container.StockpotContainer;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=StockpotRecipe.class,remap=false)
public abstract class PreparedStockpotRecipeMixin implements PreparedStockpotAccess {
    @Unique private PreparedStockpotRecipe cooking$descriptor;
    @Override public PreparedStockpotRecipe cooking$preparedStockpot() { return cooking$descriptor; }
    @Override public void cooking$attachPreparedStockpot(PreparedStockpotRecipe descriptor) {
        if(descriptor==null||cooking$descriptor!=null) throw new IllegalStateException("Prepared stockpot descriptor must attach exactly once");
        cooking$descriptor=descriptor;
    }
    // Typed matches is NOT obfuscated in pinned raw record; only its Container bridge is m_5818_.
    @Inject(method="matches(Lcom/github/ysbbbbbb/kaleidoscopecookery/crafting/container/StockpotContainer;Lnet/minecraft/world/level/Level;)Z",
        at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void cooking$matches(StockpotContainer inputs, Level level, CallbackInfoReturnable<Boolean> cir) {
        if(cooking$descriptor!=null) cir.setReturnValue(cooking$descriptor.matches(inputs,level));
    }
    @Inject(method={"getSerializer()Lnet/minecraft/world/item/crafting/RecipeSerializer;","m_7707_()Lnet/minecraft/world/item/crafting/RecipeSerializer;"},
        at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void cooking$serializer(CallbackInfoReturnable<RecipeSerializer<?>> cir) {
        if(cooking$descriptor!=null) cir.setReturnValue(PreparedStockpotSerializer.INSTANCE);
    }
}
