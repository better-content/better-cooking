package com.bettercontent.bettercooking.recipe;

import com.bettercontent.bettercooking.policy.CulinaryRoute;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;
import java.util.ArrayList;
import java.util.List;

public final class PreparationSerializer implements RecipeSerializer<PreparationRecipe> {
    @Override public PreparationRecipe fromJson(ResourceLocation id, JsonObject json) {
        List<PreparationRecipe.Input> inputs = new ArrayList<>();
        for (var raw : GsonHelper.getAsJsonArray(json, "ingredients")) {
            JsonObject value = raw.getAsJsonObject();
            inputs.add(new PreparationRecipe.Input(Ingredient.fromJson(value.has("ingredient") ? value.get("ingredient") : value), GsonHelper.getAsInt(value, "count", 1)));
        }
        List<CulinaryRoute.Stage> stages = new ArrayList<>();
        for (var raw : GsonHelper.getAsJsonArray(json, "stages")) {
            JsonObject value = raw.getAsJsonObject();
            stages.add(new CulinaryRoute.Stage(GsonHelper.getAsString(value,"action"), GsonHelper.getAsInt(value,"duration_ticks"),
                    GsonHelper.getAsInt(value,"repetitions",1),GsonHelper.getAsString(value,"tool_tag","")));
        }
        return new PreparationRecipe(id,inputs,CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json,"result"),true,true),
                GsonHelper.getAsString(json,"surface","board"),GsonHelper.getAsString(json,"finish","board"),
                GsonHelper.getAsInt(json,"servings",1),GsonHelper.getAsString(json,"work_class","straightforward"),stages,GsonHelper.getAsInt(json,"cook_ticks",0),
                json.has("native_quality") ? FlexibleFormulation.fromJson(GsonHelper.getAsJsonObject(json,"native_quality")) : FlexibleFormulation.fixed());
    }
    @Override public PreparationRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
        int count=buffer.readVarInt(); if(count<1||count>9) throw new IllegalArgumentException("Invalid kitchen input count");
        List<PreparationRecipe.Input> inputs=new ArrayList<>();
        for(int index=0;index<count;index++) inputs.add(new PreparationRecipe.Input(Ingredient.fromNetwork(buffer),buffer.readVarInt()));
        var output=buffer.readItem(); String surface=buffer.readUtf(64),finish=buffer.readUtf(64); int servings=buffer.readVarInt(); String work=buffer.readUtf(64);
        int size=buffer.readVarInt(); if(size<1||size>64) throw new IllegalArgumentException("Invalid kitchen stages");
        List<CulinaryRoute.Stage> stages=new ArrayList<>();
        for(int index=0;index<size;index++) stages.add(new CulinaryRoute.Stage(buffer.readUtf(32),buffer.readVarInt(),buffer.readVarInt(),buffer.readUtf(256)));
        int cookTicks = buffer.readVarInt();
        FlexibleFormulation quality = FlexibleFormulation.parse(buffer.readUtf(8192));
        return new PreparationRecipe(id,inputs,output,surface,finish,servings,work,stages,cookTicks,quality);
    }
    @Override public void toNetwork(FriendlyByteBuf buffer, PreparationRecipe recipe) {
        buffer.writeVarInt(recipe.inputs().size());
        recipe.inputs().forEach(input->{input.ingredient().toNetwork(buffer);buffer.writeVarInt(input.count());});
        buffer.writeItem(recipe.getResultItem(null));buffer.writeUtf(recipe.surface(),64);buffer.writeUtf(recipe.finish(),64);buffer.writeVarInt(recipe.servings());buffer.writeUtf(recipe.workClass(),64);
        buffer.writeVarInt(recipe.stages().size());
        recipe.stages().forEach(stage->{buffer.writeUtf(stage.action(),32);buffer.writeVarInt(stage.durationTicks());buffer.writeVarInt(stage.repetitions());buffer.writeUtf(stage.toolTag(),256);});
        buffer.writeVarInt(recipe.cookTicks());
        buffer.writeUtf(recipe.nativeQuality().fingerprint(),8192);
    }
}
