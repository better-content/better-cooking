package com.bettercontent.bettercooking;

import com.bettercontent.bettercooking.kitchen.KitchenContent;
import com.bettercontent.bettercooking.kitchen.KitchenEvents;
import com.bettercontent.bettercooking.kitchen.NativeKitchenHooks;
import com.bettercontent.bettercooking.policy.PolicyRepository;
import com.bettercontent.bettercooking.recipe.*;
import com.bettercontent.betterindustrialheat.api.CookingAgeApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import java.util.List;

@Mod(CookingMod.MOD_ID)
public final class CookingMod {
    public static final String MOD_ID="better_cooking";
    private static final boolean DISCOVERY=Boolean.getBoolean("better_cooking.discoveryOnly");
    private static volatile boolean incompleteCatalogue;
    // Existing hooks already use this pass-through guard. An incomplete bundled catalogue
    // must not alter native food, recipes, drinks or crop acquisition just to load the mod.
    public static boolean discoveryOnly() { return DISCOVERY || incompleteCatalogue; }
    public static boolean incompleteCatalogue() { return incompleteCatalogue; }
    public CookingMod() {
        PolicyRepository.loadBundled(DISCOVERY);
        incompleteCatalogue = !PolicyRepository.current().qualified();
        com.bettercontent.bettercooking.acquisition.KaleidoscopeCropAuthority.installAliases();
        var bus=FMLJavaModLoadingContext.get().getModEventBus();
        CookingItems.register(bus);CookingRecipes.register(bus);KitchenContent.register(bus);KitchenEvents.register();
        com.bettercontent.bettercooking.drinks.placed.NativeDrinkEntities.register(bus);
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event)->event.enqueueWork(com.bettercontent.bettercooking.placed.NativeFoodServings::register));
        CraftingHelper.register(PreparedBatchIngredient.ID,PreparedBatchIngredient.SERIALIZER);
        if(!DISCOVERY)com.bettercontent.spiritcommerce.api.RenewableAcquisitionPolicy.register(new ResourceLocation(MOD_ID,"meal_production"),
                stack->{var food=CookingRuntime.profile(stack);return food==null||!food.protectedMeal();});
        if(!DISCOVERY) CookingAgeApi.registerCarrier(new ResourceLocation(MOD_ID,"preparations"),new CookingAgeApi.Carrier() {
            public boolean handles(ItemStack stack) { return CookingItems.PREPARED_BATCH.isPresent()&&CookingRuntime.ageCarrier(stack); }
            public List<ItemStack> read(ItemStack stack) { return CookingRuntime.preparedInputs(stack); }
            public void write(ItemStack stack,List<ItemStack> inputs) { CookingRuntime.writePreparedInputs(stack,inputs); }
        });
        // Register every nonedible carrier before any serving lookup can freeze the Heat registry.
        if(!DISCOVERY)com.bettercontent.bettercooking.drinks.NativeDrinkProvenance.registerAgeCarrier();
        CulinaryConsumption.register();CulinaryEffects.register();
        NativeKitchenHooks.install();
    }
}
