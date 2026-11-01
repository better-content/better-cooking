package com.bettercontent.bettercooking.compat.emi;

import com.bettercontent.bettercooking.recipe.PreparationRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public final class PreparationEmiRecipe implements EmiRecipe {
    private final PreparationRecipe recipe;
    private final List<EmiIngredient> inputs;
    private final List<EmiStack> outputs;
    public PreparationEmiRecipe(PreparationRecipe recipe) {
        this.recipe = recipe;
        inputs = recipe.inputs().stream().map(input -> EmiIngredient.of(input.ingredient(), input.count())).toList();
        outputs = List.of(EmiStack.of(recipe.getResultItem(Minecraft.getInstance().level.registryAccess())));
    }
    public PreparationRecipe recipe() { return recipe; }
    @Override public EmiRecipeCategory getCategory() { return CookingEmiPlugin.PREPARATION; }
    @Override public ResourceLocation getId() { return recipe.getId(); }
    @Override public List<EmiIngredient> getInputs() { return inputs; }
    @Override public List<EmiStack> getOutputs() { return outputs; }
    @Override public int getDisplayWidth() { return 174; }
    @Override public int getDisplayHeight() { return 96 + recipe.stages().size() * 22; }
    @Override public void addWidgets(WidgetHolder widgets) {
        for (int i = 0; i < inputs.size(); i++) widgets.addSlot(inputs.get(i), i % 3 * 18, i / 3 * 18 + 12);
        widgets.addSlot(outputs.get(0), 146, 30).recipeContext(this);
        widgets.addText(Component.literal(recipe.surface() + " preparation"), 0, 0, 0xff444444, false);
        widgets.addText(Component.literal("Shift + empty hand: menu"), 0, 70, 0xff444444, false);
        int y = 84;
        for (var stage : recipe.stages()) {
            widgets.addText(Component.literal(stage.action() + " x" + stage.repetitions() + "  " + stage.durationTicks() / 20.0 + "s each"), 0, y, 0xff444444, false);
            String tool = stage.toolTag();
            widgets.addText(Component.literal(tool == null || tool.isBlank() ? "Hold action (no tool)" : "Tool: #" + tool.replaceFirst("^#", "")), 0, y + 10, 0xff777777, false);
            y += 22;
        }
        if (!recipe.finishedOnPreparationSurface()) widgets.addText(Component.literal("Then " + recipe.finish() + ": " + recipe.cookTicks() / 20.0 + "s"), 0, y, 0xff444444, false);
    }
}
