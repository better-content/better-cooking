package com.bettercontent.bettercooking.cutting;

import com.bettercontent.bettercooking.recipe.NativeCuttingSerializer;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Exact native KC type/matching/board ownership, with a source-preserving probabilistic yield plan. */
public final class NativeCuttingRecipe extends ChoppingBoardRecipe {
    private final CuttingPlan plan;
    public NativeCuttingRecipe(ResourceLocation id, CuttingPlan plan) {
        super(id, plan.ingredient(), plan.preview(), plan.cuts(), plan.model()); this.plan = plan;
    }
    public CuttingPlan plan() { return plan; }
    @Override public RecipeSerializer<?> getSerializer() { return NativeCuttingSerializer.INSTANCE; }
}
