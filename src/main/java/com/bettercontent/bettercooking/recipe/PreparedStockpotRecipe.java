package com.bettercontent.bettercooking.recipe;

import com.bettercontent.bettercooking.CookingRuntime;
import com.bettercontent.bettercooking.stockpot.PreparedStockpotAccess;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.container.StockpotContainer;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotVisuals;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModSoupBases;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.Objects;

/** Typed descriptor: StockpotRecipe itself is a final record in KC 1.6.0. */
public final class PreparedStockpotRecipe {
    private final ResourceLocation route;
    private final ItemStack output;
    private final int ticks;
    private final Ingredient carrier;
    public PreparedStockpotRecipe(ResourceLocation route, ItemStack output, int ticks, Ingredient carrier) {
        this.route=Objects.requireNonNull(route); this.carrier=Objects.requireNonNull(carrier);
        if(output.isEmpty() || output.getCount()<1 || output.getCount()>StockpotBlockEntity.MAX_TAKEOUT_COUNT || ticks<1)
            throw new IllegalArgumentException("Prepared stockpot requires exact yield 1..9 and positive explicit time");
        this.output=output.copy(); this.ticks=ticks;
    }
    public ResourceLocation route() { return route; }
    public ItemStack output() { return output.copy(); }
    public int ticks() { return ticks; }
    public Ingredient carrier() { return carrier; }
    /** Own finishing contract only; Root remains sole authority for paid-work fingerprints. */
    public String binding() {
        return route+"|"+output.save(new net.minecraft.nbt.CompoundTag())+"|"+ticks+"|"+carrier.toJson();
    }
    public StockpotRecipe nativeRecipe(ResourceLocation id) {
        StockpotRecipe nativeRecipe=new StockpotRecipe(id,List.of(new PreparedBatchIngredient(route)),
            ModSoupBases.WATER,output.copy(),ticks,carrier,StockpotVisuals.DEFAULT);
        ((PreparedStockpotAccess)(Object)nativeRecipe).cooking$attachPreparedStockpot(this);
        return nativeRecipe;
    }
    public boolean accepts(ItemStack batch, Level level) {
        if(level==null || !CookingRuntime.isPreparedFor(batch,route)) return false;
        var current=level.getRecipeManager().byKey(route).orElse(null);
        if(!(current instanceof PreparationRecipe preparation)
            || !preparation.finish().equals("stockpot") || preparation.cookTicks()!=ticks
            || !sameOutput(preparation.getResultItem(level.registryAccess()),output)
            || !CookingRuntime.isPreparedForCurrentRecipe(batch,preparation)) return false;
        return !CookingRuntime.finishFromPrepared(batch,output.copy()).isEmpty();
    }
    public boolean matches(StockpotContainer container, Level level) {
        ItemStack batch=ItemStack.EMPTY;
        for(int slot=0;slot<container.getContainerSize();slot++) {
            ItemStack input=container.getItem(slot);
            if(input.isEmpty()) continue;
            if(!batch.isEmpty() || input.getCount()!=1) return false;
            batch=input;
        }
        return !batch.isEmpty() && accepts(batch,level);
    }
    private static boolean sameOutput(ItemStack a, ItemStack b) {
        return a.getCount()==b.getCount() && ItemStack.isSameItemSameTags(a,b);
    }
}
