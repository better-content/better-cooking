package com.bettercontent.bettercooking;

import java.util.Set;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** All managed item views and native item consumption use the same single-portion resolver. */
public final class CulinaryFoodProperties {
    private static final Set<String> SUPPRESSED=Set.of("kaleidoscope_cookery:satiated_shield","kaleidoscope_cookery:vigor","kaleidoscope_cookery:vitality",
            "kaleidoscope_cookery:preservation","kaleidoscope_cookery:projectile_dodge","kaleidoscope_cookery:hinder","kaleidoscope_cookery:instant_smelting","kaleidoscope_cookery:warmth");
    private CulinaryFoodProperties() {}
    public static FoodProperties apply(ItemStack stack,FoodProperties original) {
        if(original==null||CookingMod.discoveryOnly()||!com.bettercontent.bettercooking.policy.PolicyRepository.loaded()) return original;
        var authored=CookingRuntime.profile(stack);if(authored!=null&&authored.role().equals("drink"))return original;
        var resolved=CookingRuntime.resolve(stack);if(resolved.isEmpty()) return original;
        var serving=resolved.get();FoodProperties.Builder result=new FoodProperties.Builder().nutrition(serving.hunger()).saturationMod(serving.saturationModifier());
        if(original.isMeat())result.meat();if(original.isFastFood())result.fast();if(original.canAlwaysEat())result.alwaysEat();
        original.getEffects().forEach(pair->{MobEffectInstance effect=allowed(pair.getFirst());if(effect!=null) result.effect(()->new MobEffectInstance(effect),pair.getSecond());});
        return result.build();
    }
    public static boolean suppressed(net.minecraft.resources.ResourceLocation id) { return SUPPRESSED.contains(id.toString()); }
    public static MobEffectInstance allowed(MobEffectInstance effect) {
        if(!effect.getEffect().isBeneficial())return new MobEffectInstance(effect);
        var key=ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect());if(key!=null&&suppressed(key))return null;
        return new MobEffectInstance(effect.getEffect(),Math.min(1200,effect.getDuration()),0,effect.isAmbient(),effect.isVisible(),effect.showIcon());
    }
}
