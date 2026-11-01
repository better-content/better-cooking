package com.bettercontent.bettercooking.recipe;

import com.bettercontent.bettercooking.stockpot.PreparedStockpotAccess;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;
import java.util.Set;

/** Root registers INSTANCE as better_cooking:prepared_stockpot. No default water/item/carrier cost. */
public final class PreparedStockpotSerializer implements RecipeSerializer<StockpotRecipe> {
    public static final PreparedStockpotSerializer INSTANCE=new PreparedStockpotSerializer();
    private static final Set<String> KEYS=Set.of("type","route","result","time","carrier","empty_carrier");
    @Override public StockpotRecipe fromJson(ResourceLocation id, JsonObject json) {
        for(String key:json.keySet()) if(!KEYS.contains(key)) throw new IllegalArgumentException("Unknown prepared stockpot field: "+key);
        boolean empty=json.has("empty_carrier"), explicit=json.has("carrier");
        if(empty==explicit || empty&&!GsonHelper.getAsBoolean(json,"empty_carrier"))
            throw new IllegalArgumentException("Declare exactly one explicit carrier or empty_carrier:true; no native bowl default");
        Ingredient carrier=empty?Ingredient.EMPTY:Ingredient.fromJson(json.get("carrier"));
        if(explicit&&carrier.isEmpty()) throw new IllegalArgumentException("Empty explicit carrier; use empty_carrier:true");
        JsonObject result=GsonHelper.getAsJsonObject(json,"result");
        int count=exactInt(result,"count"), ticks=exactInt(json,"time");
        ItemStack output=CraftingHelper.getItemStack(result,true,true);
        if(output.getCount()!=count) throw new IllegalArgumentException("Result count changed while decoding");
        return new PreparedStockpotRecipe(new ResourceLocation(GsonHelper.getAsString(json,"route")),output,
            ticks,carrier).nativeRecipe(id);
    }
    private static int exactInt(JsonObject json,String key) {
        if(!json.has(key)||!json.get(key).isJsonPrimitive()||!json.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("Explicit integer required: "+key);
        try { return json.get(key).getAsBigDecimal().intValueExact(); }
        catch(ArithmeticException ex) { throw new IllegalArgumentException("Exact integer required: "+key,ex); }
    }
    @Override public StockpotRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
        ResourceLocation route=buffer.readResourceLocation(); ItemStack result=buffer.readItem(); int ticks=buffer.readVarInt();
        Ingredient carrier=buffer.readBoolean()?Ingredient.EMPTY:Ingredient.fromNetwork(buffer);
        return new PreparedStockpotRecipe(route,result,ticks,carrier).nativeRecipe(id);
    }
    @Override public void toNetwork(FriendlyByteBuf buffer, StockpotRecipe nativeRecipe) {
        PreparedStockpotRecipe descriptor=((PreparedStockpotAccess)(Object)nativeRecipe).cooking$preparedStockpot();
        if(descriptor==null) throw new IllegalArgumentException("Not our prepared stockpot recipe");
        buffer.writeResourceLocation(descriptor.route()); buffer.writeItem(descriptor.output()); buffer.writeVarInt(descriptor.ticks());
        buffer.writeBoolean(descriptor.carrier().isEmpty());
        if(!descriptor.carrier().isEmpty()) descriptor.carrier().toNetwork(buffer);
    }
}
