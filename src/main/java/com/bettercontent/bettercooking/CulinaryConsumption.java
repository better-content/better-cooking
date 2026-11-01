package com.bettercontent.bettercooking;

import com.bettercontent.betterdrinkingwater.WaterSources;
import com.bettercontent.bettercooking.drinks.NativeDrinkProvenance;
import com.bettercontent.betterdrinkingwater.api.ServingHydration;
import com.bettercontent.bettersurvivalphysiology.api.CulinaryNutritionApi;
import com.bettercontent.betterspicyfarmers.api.SpiceApi;
import com.bettercontent.betterspicyfarmers.runtime.SpiceRuntime;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** One dispatcher after real native serving completion. Beverage paid fractions retain their own owner. */
public final class CulinaryConsumption {
    private static final String SEQUENCE="better_cooking:completion_sequence";
    private CulinaryConsumption() {}
    public static boolean manages(ItemStack stack) {
        if(CookingMod.discoveryOnly()||stack.isEmpty()||!WaterSources.nativeHydration(stack).isEmpty())return false;
        var profile=CookingRuntime.profile(stack);
        if(profile==null||!profile.resolved())return false;
        // A tagged unpaid/partial/invalid new cup is never an untagged legacy default.
        return !NativeDrinkProvenance.hasReceipt(stack)||NativeDrinkProvenance.hydration(stack).isPresent();
    }
    private static boolean nutritionManages(ItemStack stack) {
        if(!manages(stack))return false;var profile=CookingRuntime.profile(stack);
        if(NativeDrinkProvenance.hasReceipt(stack)&&NativeDrinkProvenance.resolve(stack).isEmpty())return false;
        // Preserve signed native specialist intake in its original owner rather than inventing zero nutrients.
        return profile.hunger()>=0&&profile.saturationModifier()>=0;
    }
    private static boolean spiceManages(ItemStack stack) {
        return manages(stack)&&(!NativeDrinkProvenance.hasReceipt(stack)||NativeDrinkProvenance.resolve(stack).isPresent());
    }
    private static Optional<ServingHydration.Profile> hydration(ItemStack stack) {
        if(!manages(stack))return Optional.empty();
        if(NativeDrinkProvenance.hasReceipt(stack))return NativeDrinkProvenance.hydration(stack);
        return CookingRuntime.resolve(stack).map(s->new ServingHydration.Profile(s.hydration(),s.quenching()));
    }
    public static void register() {
        if(CookingMod.discoveryOnly())return;
        ResourceLocation owner=new ResourceLocation(CookingMod.MOD_ID,"servings");
        CulinaryNutritionApi.register(owner,(player,stack)->nutritionManages(stack)?CookingRuntime.resolve(stack).map(s->new CulinaryNutritionApi.Serving(s.baseNutrients(),s.nutrients(),s.sugar(),s.alcohol())):Optional.empty());
        ServingHydration.register(owner,(player,stack)->hydration(stack));
        SpiceApi.registerDoseProvider(owner,(player,stack)-> {
            if(!spiceManages(stack))return OptionalDouble.empty();
            if(NativeDrinkProvenance.hasReceipt(stack))return CookingRuntime.capturedDrinkSpice(stack);
            if(stack.hasTag()&&stack.getTag().getCompound(CookingRuntime.SERVING).contains("spice"))return OptionalDouble.of(CookingRuntime.metadataSpice(stack));
            return CookingRuntime.nativeSpice(stack);
        });
        SpiceRuntime.registerExternalCompletionOwner(owner,CulinaryConsumption::spiceManages);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,CulinaryConsumption::finished);
    }
    private static void finished(LivingEntityUseItemEvent.Finish event) {
        if(event.getEntity() instanceof ServerPlayer player&&!(player instanceof FakePlayer)&&player.connection!=null)complete(player,event.getItem().copy());
    }
    public static boolean complete(ServerPlayer player,ItemStack capturedServing) {
        if(player==null||player instanceof FakePlayer||player.connection==null||player.hasDisconnected()||!manages(capturedServing))return false;
        long previous=player.getPersistentData().getLong(SEQUENCE);
        if(previous<0||previous==Long.MAX_VALUE)throw new IllegalStateException("Invalid culinary completion sequence");
        long sequence=previous+1;player.getPersistentData().putLong(SEQUENCE,sequence);
        CulinaryNutritionApi.complete(player,capturedServing);
        ServingHydration.completeFood(player,sequence,capturedServing);
        if(spiceManages(capturedServing))SpiceRuntime.applyCompletedUnits(player,SpiceApi.resolveUnits(player,capturedServing));
        return true;
    }
}
