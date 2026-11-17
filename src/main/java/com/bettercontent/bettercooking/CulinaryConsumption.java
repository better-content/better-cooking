package com.bettercontent.bettercooking;

import com.bettercontent.betterdrinkingwater.WaterSources;
import com.bettercontent.bettercooking.drinks.NativeDrinkProvenance;
import com.bettercontent.betterdrinkingwater.api.ServingHydration;
import com.bettercontent.bettersurvivalphysiology.api.CulinaryNutritionApi;
import com.bettercontent.bettersurvivalphysiology.nutrition.ServingVectors;
import com.bettercontent.betterspicyfarmers.api.SpiceApi;
import com.bettercontent.betterspicyfarmers.runtime.SpiceRuntime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** One dispatcher after real native serving completion. Beverage paid fractions retain their own owner. */
public final class CulinaryConsumption {
    private static final String SEQUENCE="better_cooking:completion_sequence";
    // Callback identity is session-local. Persist only Water's increasing sequence; a
    // cancelled or preview use never receives a committed-use token or an award.
    private static final Map<UUID, CompletionTokenLedger> COMMITTED = new HashMap<>();
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
        CulinaryNutritionApi.register(owner,(player,stack)->nutritionManages(stack)?CookingRuntime.resolve(stack).map(s->new CulinaryNutritionApi.Serving(new ServingVectors.CanonicalServing(s.baseNutrients()),
                new ServingVectors.CanonicalServing(s.nutrients()),s.sugar(),s.alcohol())):Optional.empty());
        ServingHydration.register(owner,(player,stack)->hydration(stack));
        SpiceApi.registerDoseProvider(owner,(player,stack)-> {
            if(!spiceManages(stack))return OptionalDouble.empty();
            if(NativeDrinkProvenance.hasReceipt(stack))return CookingRuntime.capturedDrinkSpice(stack);
            if(stack.hasTag()&&stack.getTag().getCompound(CookingRuntime.SERVING).contains("spice"))return OptionalDouble.of(CookingRuntime.metadataSpice(stack));
            return CookingRuntime.nativeSpice(stack);
        });
        SpiceRuntime.registerExternalCompletionOwner(owner,CulinaryConsumption::spiceManages);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,CulinaryConsumption::finished);
        MinecraftForge.EVENT_BUS.addListener(CulinaryConsumption::loggedOut);
        MinecraftForge.EVENT_BUS.addListener(CulinaryConsumption::serverStopped);
    }
    private static void finished(LivingEntityUseItemEvent.Finish event) {
        // The actual native completion event is this use's identity; repeated delivery
        // of this SAME event cannot mint a second hydration sequence or food/spice award.
        if(event.getEntity() instanceof ServerPlayer player&&!(player instanceof FakePlayer)&&player.connection!=null)
            complete(player,event.getItem().copy(),event);
    }
    /** Caller must supply the actual committed-use identity, allocated once AFTER native
     * Finish or a verified placed-bite debit and reused by any duplicate callbacks. */
    public static boolean complete(ServerPlayer player,ItemStack capturedServing,Object committedUse) {
        if(player==null||player instanceof FakePlayer||player.connection==null||player.hasDisconnected()||!manages(capturedServing))return false;
        long previous=player.getPersistentData().getLong(SEQUENCE);
        if(previous<0||previous==Long.MAX_VALUE)throw new IllegalStateException("Invalid culinary completion sequence");
        if(!COMMITTED.computeIfAbsent(player.getUUID(), ignored->new CompletionTokenLedger()).claim(committedUse))return false;
        long sequence=previous+1;player.getPersistentData().putLong(SEQUENCE,sequence);
        CulinaryNutritionApi.complete(player,capturedServing);
        ServingHydration.completeFood(player,sequence,capturedServing);
        if(spiceManages(capturedServing))SpiceRuntime.applyCompletedUnits(player,SpiceApi.resolveUnits(player,capturedServing));
        return true;
    }
    private static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) { COMMITTED.remove(event.getEntity().getUUID()); }
    private static void serverStopped(ServerStoppedEvent event) { COMMITTED.clear(); }
}
