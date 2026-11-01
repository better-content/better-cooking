package com.bettercontent.bettercooking.fixture.tests.kitchen;

import com.bettercontent.bettercooking.kitchen.KitchenTransfer;
import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.policy.CulinaryRoute;
import com.bettercontent.bettercooking.recipe.PreparationRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;

/** Actual inventory controls require transformed Forge startup; see TRANSFER-TESTS.md. */
@GameTestHolder(CookingMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KitchenTransferGameTests {
    private static List<ItemStack> slots(int size) {
        var result = new ArrayList<ItemStack>();
        for (int i = 0; i < size; i++) result.add(ItemStack.EMPTY);
        return result;
    }
    private static PreparationRecipe recipe(PreparationRecipe.Input... inputs) {
        return new PreparationRecipe(new ResourceLocation("better_cooking:test"), List.of(inputs), new ItemStack(Items.BREAD, 3), "board", "board", 3, "simple", List.of(new CulinaryRoute.Stage("mix", 20, 1, "")), 0);
    }
    private static void equal(GameTestHelper helper, long expected, long actual, String assertion) {
        helper.assertTrue(expected == actual, assertion + ": expected=" + expected + ", actual=" + actual);
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void preservesActualTagsCountsAndDoesNotMutatePreview(GameTestHelper helper) {
        var input = slots(9); var player = slots(36);
        ItemStack paid = new ItemStack(Items.APPLE, 4); paid.getOrCreateTag().putLong("oldest", 17); player.set(0, paid);
        var plan = KitchenTransfer.plan(recipe(new PreparationRecipe.Input(Ingredient.of(Items.APPLE), 2)), input, player).orElseThrow();
        equal(helper, 4, paid.getCount(), "preview does not debit source");
        helper.assertTrue(input.get(0).isEmpty(), "preview does not fill actual input");
        equal(helper, 2, plan.inputs().get(0).getCount(), "one batch exact input count");
        equal(helper, 17, plan.inputs().get(0).getTag().getLong("oldest"), "actual ingredient age tag retained");
        equal(helper, 2, plan.inventory().get(0).getCount(), "planned inventory remainder exact");
        equal(helper, 0, plan.inputs().stream().filter(s -> !s.isEmpty() && !s.is(Items.APPLE)).count(), "no invented input type");
        helper.succeed();
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void alternativesUseMaxFlowRatherThanGreedyDoubleDebit(GameTestHelper helper) {
        var player = slots(36); player.set(0, new ItemStack(Items.APPLE)); player.set(1, new ItemStack(Items.CARROT));
        var plan = KitchenTransfer.plan(recipe(new PreparationRecipe.Input(Ingredient.of(Items.APPLE, Items.CARROT), 1), new PreparationRecipe.Input(Ingredient.of(Items.APPLE), 1)), slots(9), player).orElseThrow();
        equal(helper, 2, plan.inputs().stream().mapToInt(ItemStack::getCount).sum(), "alternative and strict input both supplied once");
        helper.assertTrue(plan.inventory().stream().allMatch(ItemStack::isEmpty), "all selected ingredients accounted");
        equal(helper, 1, player.get(0).getCount(), "apple preview source unchanged");
        equal(helper, 1, player.get(1).getCount(), "carrot preview source unchanged");
        helper.succeed();
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void fullInventoryRejectsSurplusReturnWithoutMutating(GameTestHelper helper) {
        var input = slots(9); input.set(0, new ItemStack(Items.DIAMOND, 64)); var player = slots(36);
        for (int i = 0; i < 36; i++) player.set(i, new ItemStack(Items.STONE, 64)); player.set(0, new ItemStack(Items.APPLE, 64));
        helper.assertTrue(KitchenTransfer.plan(recipe(new PreparationRecipe.Input(Ingredient.of(Items.APPLE), 1)), input, player).isEmpty(), "no transfer when surplus cannot fit player inventory");
        equal(helper, 64, input.get(0).getCount(), "rejected input unchanged");
        equal(helper, 64, player.get(0).getCount(), "rejected player source unchanged");
        helper.succeed();
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void noNineSlotOverflowFromDistinctTaggedIngredients(GameTestHelper helper) {
        var player = slots(36);
        for (int i = 0; i < 10; i++) { var apple = new ItemStack(Items.APPLE); apple.getOrCreateTag().putInt("distinct", i); player.set(i, apple); }
        helper.assertTrue(KitchenTransfer.plan(recipe(new PreparationRecipe.Input(Ingredient.of(Items.APPLE), 10)), slots(9), player).isEmpty(), "ten distinct actual tags cannot be laundered into nine inputs");
        equal(helper, 10, player.stream().mapToInt(ItemStack::getCount).sum(), "rejected tagged source conserved");
        helper.succeed();
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void alreadyFilledInputsRemainConservedAndNoOutputsOrWorkCreated(GameTestHelper helper) {
        var input = slots(9); input.set(0, new ItemStack(Items.APPLE, 4)); var player = slots(36);
        var plan = KitchenTransfer.plan(recipe(new PreparationRecipe.Input(Ingredient.of(Items.APPLE), 2)), input, player).orElseThrow();
        equal(helper, 2, plan.inputs().stream().mapToInt(ItemStack::getCount).sum(), "already-filled batch exact input count");
        equal(helper, 2, plan.inventory().stream().mapToInt(ItemStack::getCount).sum(), "surplus returned in plan");
        helper.assertTrue(plan.inputs().stream().noneMatch(s -> s.is(Items.BREAD)), "transfer creates no prepared output");
        equal(helper, 4, input.get(0).getCount(), "already-filled actual input unchanged by preview");
        helper.succeed();
    }
}
