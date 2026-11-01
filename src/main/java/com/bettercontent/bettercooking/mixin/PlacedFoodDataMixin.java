package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.*;
import com.bettercontent.betterspicyfarmers.seasoning.ServingContext;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native placed bites use eat(int,float), bypassing the stack-based Forge food-properties view. */
@Mixin(FoodData.class)
public abstract class PlacedFoodDataMixin {
    @Shadow private int foodLevel;
    @Shadow private float saturationLevel;
    @Inject(method="eat(IF)V",at=@At("HEAD"),cancellable=true)
    private void cooking$actualPortion(int hunger,float saturation,CallbackInfo ci){
        if(CookingMod.discoveryOnly())return;
        ItemStack serving=CulinaryEffects.currentSource();if(serving.isEmpty())serving=ServingContext.currentOr(ItemStack.EMPTY);
        if(!CulinaryConsumption.manages(serving))return;
        var resolved=CookingRuntime.resolve(serving);if(resolved.isEmpty())return;var profile=resolved.get();
        if(hunger==profile.hunger()&&Float.compare(saturation,profile.saturationModifier())==0)return;
        foodLevel=Math.min(foodLevel+profile.hunger(),20);
        saturationLevel=Math.min(saturationLevel+profile.hunger()*profile.saturationModifier()*2,foodLevel);
        ci.cancel();
    }
}
