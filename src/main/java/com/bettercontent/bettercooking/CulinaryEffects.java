package com.bettercontent.bettercooking;

import com.bettercontent.betterdrinkingwater.WaterSources;
import com.bettercontent.betterdrinkingwater.api.ServingEffects;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;

/** Bounded synchronous source context, never a buff or consumption tracker. */
public final class CulinaryEffects {
    private static final ThreadLocal<ItemStack> CURRENT=new ThreadLocal<>();
    private static final ResourceLocation SHIELD=new ResourceLocation("kaleidoscope_cookery","satiated_shield");
    private CulinaryEffects() {}
    public static ItemStack currentSource(){ItemStack value=CURRENT.get();return value==null?ItemStack.EMPTY:value;}
    public static boolean active(){return !currentSource().isEmpty();}
    public static <T>T call(ItemStack source,Supplier<T> action){
        if(CookingMod.discoveryOnly()||source.isEmpty()||!com.bettercontent.bettercooking.policy.PolicyRepository.loaded())return action.get();
        var food=CookingRuntime.profile(source);
        if(!source.isEdible()&&WaterSources.nativeHydration(source).isEmpty()&&(food==null||!food.resolved()))return action.get();
        ItemStack previous=CURRENT.get();CURRENT.set(source.copy());
        try{return action.get();}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
    public static void register(){
        if(CookingMod.discoveryOnly())return;
        ServingEffects.register(new ResourceLocation(CookingMod.MOD_ID,"food_effects"),(source,selected)->{
            var effect=ForgeRegistries.MOB_EFFECTS.getValue(selected.id());
            if(effect==null)throw new IllegalStateException("Missing pinned beverage effect "+selected.id());
            if(!effect.isBeneficial())return ServingEffects.Bounds.IDENTITY;
            if(CulinaryFoodProperties.suppressed(selected.id()))return ServingEffects.Bounds.SUPPRESS;
            return new ServingEffects.Bounds(false,1200,0);
        });
        MinecraftForge.EVENT_BUS.addListener(CulinaryEffects::applicable);
        MinecraftForge.EVENT_BUS.addListener(CulinaryEffects::login);
    }
    private static void applicable(MobEffectEvent.Applicable event){
        if(SHIELD.equals(ForgeRegistries.MOB_EFFECTS.getKey(event.getEffectInstance().getEffect())))event.setResult(Event.Result.DENY);
    }
    private static void login(PlayerEvent.PlayerLoggedInEvent event){
        var effect=ForgeRegistries.MOB_EFFECTS.getValue(SHIELD);if(effect==null)throw new IllegalStateException("Pinned Satiated Shield effect disappeared");
        event.getEntity().removeEffect(effect);
    }
}
