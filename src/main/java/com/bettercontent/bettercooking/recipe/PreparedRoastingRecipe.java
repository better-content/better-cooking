package com.bettercontent.bettercooking.recipe;

import com.bettercontent.bettercooking.CookingRuntime;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;

/** The actual native campfire subclass required by campfires and compatible roasting apparatus. */
public final class PreparedRoastingRecipe extends CampfireCookingRecipe {
    public PreparedRoastingRecipe(ResourceLocation id,Ingredient input,ItemStack output,float experience,int ticks) {
        super(id,"",CookingBookCategory.FOOD,input,output,experience,ticks);
        if(!(input instanceof PreparedBatchIngredient)||output.isEmpty()||output.getCount()>64
                ||!Float.isFinite(experience)||experience<0||ticks<1)
            throw new IllegalArgumentException("Roasting requires an explicit paid-batch ingredient and bounded output: "+id);
    }
    @Override public ItemStack assemble(Container input,RegistryAccess registry) {
        return CookingRuntime.finishFromPrepared(input.getItem(0),result.copy());
    }
    @Override public RecipeSerializer<?> getSerializer() { return CookingRecipes.ROASTING_SERIALIZER.get(); }
    public static final class Serializer implements RecipeSerializer<PreparedRoastingRecipe> {
        @Override public PreparedRoastingRecipe fromJson(ResourceLocation id,JsonObject value) {
            return new PreparedRoastingRecipe(id,Ingredient.fromJson(value.get("ingredient")),
                CraftingHelper.getItemStack(value.getAsJsonObject("result"),true,true),
                GsonHelper.getAsFloat(value,"experience",0),GsonHelper.getAsInt(value,"cookingtime"));
        }
        @Override public PreparedRoastingRecipe fromNetwork(ResourceLocation id,FriendlyByteBuf buffer) {
            return new PreparedRoastingRecipe(id,Ingredient.fromNetwork(buffer),buffer.readItem(),buffer.readFloat(),buffer.readVarInt());
        }
        @Override public void toNetwork(FriendlyByteBuf buffer,PreparedRoastingRecipe recipe) {
            recipe.ingredient.toNetwork(buffer);buffer.writeItem(recipe.result);buffer.writeFloat(recipe.experience);buffer.writeVarInt(recipe.cookingTime);
        }
    }
}
