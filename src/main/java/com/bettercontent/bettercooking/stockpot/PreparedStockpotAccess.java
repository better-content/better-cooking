package com.bettercontent.bettercooking.stockpot;

import com.bettercontent.bettercooking.recipe.PreparedStockpotRecipe;

/** Implemented on the actual pinned native record, never on an alternate RecipeType payload. */
public interface PreparedStockpotAccess {
    PreparedStockpotRecipe cooking$preparedStockpot();
    void cooking$attachPreparedStockpot(PreparedStockpotRecipe descriptor);
}
