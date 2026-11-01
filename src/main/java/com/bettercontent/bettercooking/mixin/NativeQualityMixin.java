package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.CookingMod;
import com.github.ysbbbbbb.kaleidoscopecookery.item.quality.Quality;
import com.github.ysbbbbbb.kaleidoscopecookery.item.quality.QualityUtils;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stop upstream absolute/squared scaling and chance filtering; the final stack resolver owns scaling. */
@Mixin(value=QualityUtils.class,remap=false)
public abstract class NativeQualityMixin {
    @Inject(method="modifyFoodProperties",at=@At("HEAD"),cancellable=true)
    private static void cooking$base(FoodProperties raw,Quality quality,CallbackInfoReturnable<FoodProperties> cir) {
        if(!CookingMod.discoveryOnly())cir.setReturnValue(raw);
    }
    @Inject(method="modifyEffects",at=@At("HEAD"),cancellable=true)
    private static void cooking$effects(List<MobEffectInstance> effects,Quality quality,CallbackInfoReturnable<List<MobEffectInstance>> cir) {
        if(!CookingMod.discoveryOnly())cir.setReturnValue(effects.stream().map(MobEffectInstance::new).toList());
    }
}
