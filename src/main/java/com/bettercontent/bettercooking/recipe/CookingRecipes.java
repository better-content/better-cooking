package com.bettercontent.bettercooking.recipe;

import com.bettercontent.bettercooking.CookingMod;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class CookingRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS,CookingMod.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> TYPES=DeferredRegister.create(ForgeRegistries.RECIPE_TYPES,CookingMod.MOD_ID);
    public static final RegistryObject<RecipeType<PreparationRecipe>> PREPARATION_TYPE=TYPES.register("preparation",()->new RecipeType<PreparationRecipe>() { public String toString(){return "better_cooking:preparation";} });
    public static final RegistryObject<PreparationSerializer> PREPARATION_SERIALIZER=SERIALIZERS.register("preparation",PreparationSerializer::new);
    public static final RegistryObject<PreparedBakingRecipe.Serializer> BAKING_SERIALIZER=SERIALIZERS.register("baking",PreparedBakingRecipe.Serializer::new);
    public static final RegistryObject<PreparedRoastingRecipe.Serializer> ROASTING_SERIALIZER=SERIALIZERS.register("roasting",PreparedRoastingRecipe.Serializer::new);
    public static final RegistryObject<NativeCuttingSerializer> CUTTING_SERIALIZER=SERIALIZERS.register("cutting",()->NativeCuttingSerializer.INSTANCE);
    public static final RegistryObject<PreparedStockpotSerializer> STOCKPOT_SERIALIZER=SERIALIZERS.register("prepared_stockpot",()->PreparedStockpotSerializer.INSTANCE);
    private CookingRecipes() {}
    public static void register(IEventBus bus) { SERIALIZERS.register(bus);TYPES.register(bus); }
}
