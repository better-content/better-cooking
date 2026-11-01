package com.bettercontent.bettercooking.finishing;

import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.CookingItems;
import com.bettercontent.bettercooking.CookingRuntime;
import com.bettercontent.bettercooking.drinks.NativeDrinkProvenance;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Actual native final-commit bridge. No quality rolls, recipe prototypes, new clocks or container debit. */
public final class NativeFinishing {
    private NativeFinishing() {}
    public static boolean prepared(ItemStack input) { return input.is(CookingItems.PREPARED_BATCH.get()); }
    public static List<ItemStack> actualInputs(Container container) {
        List<ItemStack> result=new ArrayList<>();
        for(int slot=0;slot<container.getContainerSize();slot++) {
            ItemStack input=container.getItem(slot);
            if(!input.isEmpty())result.add(input.copy());
        }
        return List.copyOf(result);
    }
    private static boolean containsPrepared(Container container) {
        for(int slot=0;slot<container.getContainerSize();slot++)if(prepared(container.getItem(slot)))return true;
        return false;
    }
    public static ItemStack pot(Container inputs, ItemStack output, boolean actualFailedStir, long gameTime) {
        if(CookingMod.discoveryOnly()||!containsPrepared(inputs))return output;
        return actualFailedStir ? CookingRuntime.carryFailedFromPrepared(actualInputs(inputs),output,output.getCount(),gameTime)
                : CookingRuntime.carryFromContainer(inputs,output);
    }
    public static ItemStack stockpot(StockpotBlockEntity owner,Container inputs,ItemStack output,boolean actualSuspiciousFallback,long gameTime) {
        if(CookingMod.discoveryOnly())return output;
        if(!containsPrepared(inputs))return NativeDrinkProvenance.decorateStockpot(owner,output,actualInputs(inputs));
        return actualSuspiciousFallback ? CookingRuntime.carryFailedFromPrepared(actualInputs(inputs),output,output.getCount(),gameTime)
                : CookingRuntime.carryFromContainer(inputs,output);
    }
    public static ItemStack steamer(ItemStack input,ItemStack output) {
        // Native Optional.orElse(stack) on a missing/deleted recipe is NOT a completed transformation.
        if(CookingMod.discoveryOnly()||input==output||!prepared(input))return output;
        return CookingRuntime.finishFromPrepared(input,output);
    }
    public static ItemStack tea(TeapotBlockEntity owner,ItemStack input,ItemStack output,boolean actualRecipeMiss,long gameTime) {
        if(CookingMod.discoveryOnly())return output;
        if(!prepared(input))return NativeDrinkProvenance.decorateTeapot(owner,output,input);
        return actualRecipeMiss ? CookingRuntime.carryFailedFromPrepared(List.of(input.copy()),output,output.getCount(),gameTime)
                : CookingRuntime.finishFromPrepared(input,output);
    }
}
