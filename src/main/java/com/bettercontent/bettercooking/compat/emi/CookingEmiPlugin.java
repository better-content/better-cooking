package com.bettercontent.bettercooking.compat.emi;

import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.recipe.CookingRecipes;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** Recipe display plus real-input transfer; EMI cannot invoke protected preparation work. */
@EmiEntrypoint
public final class CookingEmiPlugin implements EmiPlugin {
    public static final EmiRecipeCategory PREPARATION = new EmiRecipeCategory(new ResourceLocation(CookingMod.MOD_ID, "preparation"), EmiStack.of(Items.IRON_AXE));
    public static final EmiRecipeCategory CUTTING = new EmiRecipeCategory(new ResourceLocation(CookingMod.MOD_ID, "native_cutting"), EmiStack.of(Items.IRON_SWORD));
    @Override public void register(EmiRegistry registry) {
        registry.addCategory(PREPARATION);
        registry.addRecipeHandler(com.bettercontent.bettercooking.kitchen.KitchenContent.MENU.get(), new KitchenEmiHandler());
        for (String id : new String[]{"kaleidoscope_cookery:chopping_board", "ubesdelight:baking_mat_bamboo", "ubesdelight:baking_mat_kelp"}) {
            ResourceLocation location = new ResourceLocation(id);
            if (ForgeRegistries.ITEMS.containsKey(location)) registry.addWorkstation(PREPARATION, EmiStack.of(ForgeRegistries.ITEMS.getValue(location)));
        }
        for (var recipe : registry.getRecipeManager().getAllRecipesFor(CookingRecipes.PREPARATION_TYPE.get())) registry.addRecipe(new PreparationEmiRecipe(recipe));
        registry.addCategory(CUTTING);
        var board = new ResourceLocation("kaleidoscope_cookery:chopping_board");
        if (ForgeRegistries.ITEMS.containsKey(board)) registry.addWorkstation(CUTTING, EmiStack.of(ForgeRegistries.ITEMS.getValue(board)));
        var cuts = registry.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.CHOPPING_BOARD_RECIPE);
        var ids = cuts.stream().map(com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe::getId).collect(java.util.stream.Collectors.toSet());
        // Pinned KC EMI uses actual IDs but displays own chance prototypes as guaranteed and has no selector.
        // Re-display EVERY actual native recipe under its own exact ID/order, including distinct legacy
        // offal/dough recipes, with guarded selection. This never removes/replaces runtime recipes.
        registry.removeRecipes(recipe -> ids.contains(recipe.getId()) && !(recipe instanceof com.bettercontent.bettercooking.cutting.NativeCuttingEmiRecipe));
        for (var recipe : cuts) registry.addRecipe(new com.bettercontent.bettercooking.cutting.NativeCuttingEmiRecipe(recipe));
    }
}
