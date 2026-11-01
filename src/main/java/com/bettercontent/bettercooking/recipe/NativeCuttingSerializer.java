package com.bettercontent.bettercooking.recipe;

import com.bettercontent.bettercooking.cutting.*;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Root registers INSTANCE under better_cooking:cutting; runtime RecipeType stays KC ChoppingBoard. */
public final class NativeCuttingSerializer implements RecipeSerializer<NativeCuttingRecipe> {
    public static final NativeCuttingSerializer INSTANCE = new NativeCuttingSerializer();
    private NativeCuttingSerializer() {}
    @Override public NativeCuttingRecipe fromJson(ResourceLocation id, JsonObject json) { return new NativeCuttingRecipe(id, CuttingPlan.parse(json)); }
    @Override public NativeCuttingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) { return new NativeCuttingRecipe(id, CuttingPlan.read(buffer)); }
    @Override public void toNetwork(FriendlyByteBuf buffer, NativeCuttingRecipe recipe) { recipe.plan().write(buffer); }
}
