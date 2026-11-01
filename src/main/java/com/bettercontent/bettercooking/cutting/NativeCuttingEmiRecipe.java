package com.bettercontent.bettercooking.cutting;

import com.bettercontent.bettercooking.compat.emi.CookingEmiPlugin;
import com.bettercontent.bettercooking.kitchen.KitchenMenu;
import com.bettercontent.bettercooking.kitchen.KitchenNetwork;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Possible ordered outputs, NOT generated results. Selection sends only an ID to a guarded real menu. */
public final class NativeCuttingEmiRecipe implements EmiRecipe {
    private final com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe recipe;
    private final List<EmiIngredient> inputs;
    private final List<EmiIngredient> catalysts;
    private final List<EmiStack> outputs;
    public NativeCuttingEmiRecipe(com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe recipe) {
        this.recipe = recipe;
        inputs = List.of(EmiIngredient.of(recipe.getIngredient()));
        var nativeKnives = com.github.ysbbbbbb.kaleidoscopecookery.init.tag.TagMod.KITCHEN_KNIFE;
        var tools = recipe instanceof NativeCuttingRecipe own
                ? net.minecraft.world.item.crafting.Ingredient.of(java.util.Arrays.stream(own.plan().tool().getItems()).filter(stack -> stack.is(nativeKnives)).map(net.minecraft.world.item.ItemStack::copy))
                : net.minecraft.world.item.crafting.Ingredient.of(nativeKnives);
        catalysts = List.of(EmiIngredient.of(tools));
        outputs = NativeCuttingSelection.preview(recipe).stream().map(o -> EmiStack.of(o.stack()).setChance((float) o.chance())).toList();
    }
    @Override public EmiRecipeCategory getCategory() { return CookingEmiPlugin.CUTTING; }
    @Override public ResourceLocation getId() { return recipe.getId(); }
    @Override public List<EmiIngredient> getInputs() { return inputs; }
    @Override public List<EmiIngredient> getCatalysts() { return catalysts; }
    @Override public List<EmiStack> getOutputs() { return outputs; }
    @Override public int getDisplayWidth() { return 174; }
    @Override public int getDisplayHeight() { return 94 + outputs.size() * 22; }
    private boolean canSelect() {
        var player = Minecraft.getInstance().player;
        return player != null && player.containerMenu instanceof KitchenMenu menu && menu.supportsNativeCuts() && !menu.preparing()
                && recipe.matches(new net.minecraft.world.SimpleContainer(menu.getCarried()), player.level());
    }
    @Override public void addWidgets(WidgetHolder widgets) {
        widgets.addText(Component.literal("Input + source tool AND KC knife"), 0, 0, 0xff444444, false);
        widgets.addSlot(inputs.get(0), 0, 14); widgets.addSlot(catalysts.get(0), 26, 14);
        widgets.addText(Component.literal("Possible outputs, native order:"), 0, 38, 0xff444444, false);
        for (int i = 0; i < outputs.size(); i++) {
            int y = 50 + i * 22;
            widgets.addSlot(outputs.get(i), 0, y).recipeContext(this);
            widgets.addText(Component.literal((i + 1) + ". " + NativeCuttingSelection.chanceLabel(NativeCuttingSelection.preview(recipe).get(i).chance())), 24, y + 5, 0xff444444, false);
        }
        int y = 52 + outputs.size() * 22;
        widgets.addButton(0, y, 16, 16, 0, 0, this::canSelect, (x, mouseY, button) -> {
            if (button == 0 && canSelect() && Minecraft.getInstance().player.containerMenu instanceof KitchenMenu menu)
                KitchenNetwork.chooseCut(menu.containerId, recipe.getId());
        });
        widgets.addText(Component.literal("Select on open board →"), 20, y + 4, 0xff444444, false);
        widgets.addTooltipText(List.of(Component.literal("Open board menu; lift actual input onto cursor."),
                Component.literal("Outside a valid board menu this CANNOT select or grant anything."),
                Component.literal("Server validates reach, live recipe, cursor input and unpaid board.")), 0, y, 174, 18);
        widgets.addText(Component.literal("No grant, roll or preparation work"), 0, y + 22, 0xff777777, false);
    }
}
