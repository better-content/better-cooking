package com.bettercontent.bettercooking.recipe;

import com.bettercontent.bettercooking.CookingRuntime;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.common.crafting.CraftingHelper;

/** Vanilla furnace-compatible finish of actual paid raw preparation, never raw-to-premium shortcut. */
public final class PreparedBakingRecipe extends AbstractCookingRecipe {
    public PreparedBakingRecipe(ResourceLocation id,Ingredient input,ItemStack result,float experience,int ticks) {
        super(RecipeType.SMELTING,id,"",CookingBookCategory.FOOD,input,result,experience,ticks);
        if(!(input instanceof PreparedBatchIngredient)||result.isEmpty()||result.getCount()>64
                ||!Float.isFinite(experience)||experience<0||ticks<1)
            throw new IllegalArgumentException("Baking requires an explicit paid-batch ingredient and bounded output: "+id);
    }
    @Override public ItemStack assemble(Container container,RegistryAccess registry) {
        return CookingRuntime.finishFromPrepared(container.getItem(0),result.copy());
    }
    @Override public RecipeSerializer<?> getSerializer() { return CookingRecipes.BAKING_SERIALIZER.get(); }
    public static final class Serializer implements RecipeSerializer<PreparedBakingRecipe> {
        @Override public PreparedBakingRecipe fromJson(ResourceLocation id,JsonObject value) {
            return new PreparedBakingRecipe(id,Ingredient.fromJson(value.get("ingredient")),
                    CraftingHelper.getItemStack(value.getAsJsonObject("result"),true,true),GsonHelper.getAsFloat(value,"experience",0),GsonHelper.getAsInt(value,"cookingtime"));
        }
        @Override public PreparedBakingRecipe fromNetwork(ResourceLocation id,FriendlyByteBuf buffer) {
            return new PreparedBakingRecipe(id,Ingredient.fromNetwork(buffer),buffer.readItem(),buffer.readFloat(),buffer.readVarInt());
        }
        @Override public void toNetwork(FriendlyByteBuf buffer,PreparedBakingRecipe recipe) {
            recipe.ingredient.toNetwork(buffer);buffer.writeItem(recipe.result);buffer.writeFloat(recipe.experience);buffer.writeVarInt(recipe.cookingTime);
        }
    }
}
