package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.CookingMod;
import com.github.ysbbbbbb.kaleidoscopecookery.item.RawDoughItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shaping is an authored mat preparation, not one held-use action for an arbitrary stack. */
@Mixin(value=RawDoughItem.class,remap=false)
public abstract class RawDoughMixin {
    @Inject(method="releaseUsing",at=@At("HEAD"),cancellable=true,remap=true)
    private void cooking$noWholeStack(ItemStack stack,Level level,LivingEntity actor,int remaining,CallbackInfo ci) {
        if(!CookingMod.discoveryOnly())ci.cancel();
    }
}
