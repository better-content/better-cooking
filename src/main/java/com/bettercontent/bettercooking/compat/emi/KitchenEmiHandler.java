package com.bettercontent.bettercooking.compat.emi;

import com.bettercontent.bettercooking.kitchen.KitchenMenu;
import com.bettercontent.bettercooking.kitchen.KitchenNetwork;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import java.util.ArrayList;

/** Transfers one unworked batch of real inputs; never invokes begin, pulse, or output extraction. */
public final class KitchenEmiHandler implements EmiRecipeHandler<KitchenMenu> {
    @Override public EmiPlayerInventory getInventory(AbstractContainerScreen<KitchenMenu> screen) {
        var menu = screen.getMenu(); var stacks = new ArrayList<EmiStack>();
        for (int i = 0; i < 9; i++) stacks.add(EmiStack.of(menu.slots.get(i).getItem()));
        for (int i = 10; i < 46; i++) stacks.add(EmiStack.of(menu.slots.get(i).getItem()));
        return new EmiPlayerInventory(stacks);
    }
    @Override public boolean supportsRecipe(EmiRecipe recipe) { return recipe instanceof PreparationEmiRecipe; }
    @Override public boolean canCraft(EmiRecipe recipe, EmiCraftContext<KitchenMenu> context) {
        return recipe instanceof PreparationEmiRecipe preparation && context.getScreenHandler().transferPlan(preparation.recipe()).isPresent();
    }
    @Override public boolean craft(EmiRecipe recipe, EmiCraftContext<KitchenMenu> context) {
        if (!(recipe instanceof PreparationEmiRecipe preparation) || !canCraft(recipe, context)) return false;
        KitchenNetwork.fill(context.getScreenHandler().containerId, preparation.recipe().getId()); return true;
    }
}
