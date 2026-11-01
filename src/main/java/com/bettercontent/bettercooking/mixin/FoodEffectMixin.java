package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.CulinaryEffects;
import com.bettercontent.bettercooking.CulinaryFoodProperties;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class FoodEffectMixin {
    @Inject(method="addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",at=@At("HEAD"),cancellable=true)
    private void cooking$suppress(MobEffectInstance effect,Entity source,CallbackInfoReturnable<Boolean> cir){
        if(CulinaryEffects.active()&&CulinaryFoodProperties.allowed(effect)==null)cir.setReturnValue(false);
    }
    @ModifyVariable(method="addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private MobEffectInstance cooking$cap(MobEffectInstance effect){
        if(!CulinaryEffects.active())return effect;
        MobEffectInstance capped=CulinaryFoodProperties.allowed(effect);return capped==null?effect:capped;
    }
}
