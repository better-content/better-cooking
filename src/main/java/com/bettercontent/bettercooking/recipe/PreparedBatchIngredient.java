package com.bettercontent.bettercooking.recipe;

import com.bettercontent.bettercooking.CookingItems;
import com.bettercontent.bettercooking.CookingRuntime;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;

/** Preview stacks never carry the committed-preparation authorization bit. */
public final class PreparedBatchIngredient extends AbstractIngredient {
    public static final ResourceLocation ID=new ResourceLocation("better_cooking","prepared_batch");
    public static final Serializer SERIALIZER=new Serializer();
    private final ResourceLocation route;
    public PreparedBatchIngredient(ResourceLocation route) { this.route=route; }
    @Override public boolean test(ItemStack stack) { return stack!=null&&CookingRuntime.isPreparedFor(stack,route); }
    @Override public ItemStack[] getItems() {
        ItemStack preview=new ItemStack(CookingItems.PREPARED_BATCH.get());
        preview.getOrCreateTag().putString("better_cooking:preview_route",route.toString());
        return new ItemStack[]{preview};
    }
    @Override public boolean isEmpty() { return false; }
    @Override public boolean isSimple() { return false; }
    @Override public IIngredientSerializer<?> getSerializer() { return SERIALIZER; }
    @Override public JsonElement toJson() { JsonObject value=new JsonObject();value.addProperty("type",ID.toString());value.addProperty("route",route.toString());return value; }
    public static final class Serializer implements IIngredientSerializer<PreparedBatchIngredient> {
        @Override public PreparedBatchIngredient parse(FriendlyByteBuf buffer) { return new PreparedBatchIngredient(buffer.readResourceLocation()); }
        @Override public PreparedBatchIngredient parse(JsonObject value) { return new PreparedBatchIngredient(new ResourceLocation(value.get("route").getAsString())); }
        @Override public void write(FriendlyByteBuf buffer,PreparedBatchIngredient ingredient) { buffer.writeResourceLocation(ingredient.route); }
    }
}
