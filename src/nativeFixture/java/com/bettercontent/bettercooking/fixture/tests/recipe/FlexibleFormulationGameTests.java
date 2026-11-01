package com.bettercontent.bettercooking.fixture.tests.recipe;

import com.bettercontent.bettercooking.recipe.CookingRecipes;
import com.bettercontent.bettercooking.recipe.FlexibleFormulation;
import com.bettercontent.bettercooking.recipe.IngredientAccounting;
import com.bettercontent.bettercooking.recipe.PreparationRecipe;
import com.bettercontent.bettercooking.recipe.PreparationSerializer;
import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.kitchen.KitchenContent;
import com.bettercontent.bettercooking.kitchen.KitchenInventory;
import com.bettercontent.bettercooking.kitchen.KitchenTransfer;
import com.bettercontent.bettercooking.policy.CulinaryRoute;
import com.github.ysbbbbbb.kaleidoscopecookery.item.quality.Quality;
import com.github.ysbbbbbb.kaleidoscopecookery.item.quality.QualityEvaluator;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;
import java.util.List;

/** Real transformed Forge inventory/network/provider checks. Compiling this class is NOT a native pass. */
@GameTestHolder(CookingMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FlexibleFormulationGameTests {
    private static FlexibleFormulation metadata() {
        return new FlexibleFormulation(FlexibleFormulation.FLEXIBLE, "kaleidoscope_cookery:flex_pot/blaze_lamb_chop",
            "kaleidoscope_cookery:flex_pot", List.of("kc_source:item/quality/QualityEvaluator.java"));
    }
    private static PreparationRecipe recipe(FlexibleFormulation quality, PreparationRecipe.Input... inputs) {
        return new PreparationRecipe(new ResourceLocation("better_cooking:formulation_test"), List.of(inputs), new ItemStack(Items.BREAD),
            "board", "pot", 1, "substantial", List.of(new CulinaryRoute.Stage("mix", 20, 1, "")), 20, quality);
    }
    private static PreparationRecipe.Input input(Ingredient ingredient) { return new PreparationRecipe.Input(ingredient, 1); }
    private static List<ItemStack> slots(int count) {
        var result = new ArrayList<ItemStack>(); for (int i = 0; i < count; i++) result.add(ItemStack.EMPTY); return result;
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void paysOneFromEachOccupiedSlotPreservingActualPaidTags(GameTestHelper h) {
        var r = recipe(metadata(), input(Ingredient.of(Items.APPLE)), input(Ingredient.of(Items.CARROT)));
        ItemStack apple = new ItemStack(Items.APPLE, 64); apple.getOrCreateTag().putLong("oldest", 17);
        ItemStack carrot = new ItemStack(Items.CARROT, 23); carrot.getOrCreateTag().putString("provenance", "real-input");
        var inv = new SimpleContainer(apple, apple.copy(), apple.copy(), carrot);
        var paid = IngredientAccounting.debit(r, inv).orElseThrow();
        h.assertTrue(paid.size() == 4 && paid.stream().allMatch(s -> s.getCount() == 1), "four occupied slots pay four actual count1 units");
        h.assertTrue(inv.getItem(0).getCount() == 63 && inv.getItem(1).getCount() == 63 && inv.getItem(2).getCount() == 63 && inv.getItem(3).getCount() == 22, "held excess remains, never nine from one stack");
        h.assertTrue(paid.get(0).getTag().getLong("oldest") == 17 && paid.get(3).getTag().getString("provenance").equals("real-input"), "actual paid tags retained");
        h.assertTrue(r.acceptsPaid(paid), "exact saved paid receipt valid");
        paid.get(0).grow(1); h.assertTrue(!r.acceptsPaid(paid), "oversized saved paid receipt rejected not normalized"); h.succeed();
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void undercoverageUnmatchedAndTenSlotsRejectBeforeDebit(GameTestHelper h) {
        var r = recipe(metadata(), input(Ingredient.of(Items.APPLE, Items.CARROT)), input(Ingredient.of(Items.APPLE)));
        var one = new SimpleContainer(new ItemStack(Items.APPLE, 64));
        h.assertTrue(!r.matches(one, h.getLevel()) && IngredientAccounting.debit(r, one).isEmpty() && one.getItem(0).getCount() == 64, "overlapping groups require distinct physical payments");
        var bad = new SimpleContainer(new ItemStack(Items.APPLE, 4), new ItemStack(Items.CARROT), new ItemStack(Items.DIAMOND));
        h.assertTrue(IngredientAccounting.debit(r, bad).isEmpty() && bad.getItem(0).getCount() == 4, "unmatched occupied slot rejected before any debit");
        var ten = new SimpleContainer(10); for (int i = 0; i < 10; i++) ten.setItem(i, new ItemStack(Items.APPLE));
        h.assertTrue(IngredientAccounting.debit(r, ten).isEmpty() && ten.countItem(Items.APPLE) == 10, "more than nine occupied slots rejected"); h.succeed();
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void overlapUsesAugmentingPathAndStalePlanCannotDebit(GameTestHelper h) {
        var r = recipe(metadata(), input(Ingredient.of(Items.APPLE, Items.CARROT)), input(Ingredient.of(Items.APPLE)));
        var inv = new SimpleContainer(new ItemStack(Items.APPLE, 4), new ItemStack(Items.CARROT, 3));
        var plan = r.plan(inv).orElseThrow();
        inv.getItem(1).getOrCreateTag().putBoolean("changed_after_preview", true);
        h.assertTrue(plan.debit(inv).isEmpty() && inv.getItem(0).getCount() == 4, "stale full-tag snapshot refuses before first debit");
        h.assertTrue(IngredientAccounting.debit(r, inv).orElseThrow().size() == 2, "max-flow reassignment finds actual two-unit coverage"); h.succeed();
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void emiDefaultFansMinimalActualUnitsIntoDistinctSlots(GameTestHelper h) {
        var group = input(Ingredient.of(Items.APPLE)); var r = recipe(metadata(), group, group, group);
        var player = slots(36); ItemStack real = new ItemStack(Items.APPLE, 64); real.getOrCreateTag().putLong("oldest", 99); player.set(0, real);
        var plan = KitchenTransfer.plan(r, slots(9), player).orElseThrow();
        h.assertTrue(plan.inputs().stream().filter(s -> !s.isEmpty()).count() == 3 && plan.inputs().stream().allMatch(s -> s.isEmpty() || s.getCount() == 1), "EMI default is three distinct count1 kitchen slots");
        h.assertTrue(plan.inventory().get(0).getCount() == 61 && real.getCount() == 64, "one minimal actual batch, source preview unchanged");
        h.assertTrue(plan.inputs().stream().filter(s -> !s.isEmpty()).allMatch(s -> s.getTag().getLong("oldest") == 99), "real ingredient provenance retained");
        h.assertTrue(r.matches(new SimpleContainer(plan.inputs().toArray(ItemStack[]::new)), h.getLevel()), "EMI default passes authoritative flexible matcher");
        h.assertTrue(plan.inputs().stream().noneMatch(s -> s.is(Items.BREAD)), "transfer grants no output or work"); h.succeed();
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void nativeQualityJsonAndNetworkRoundTrip(GameTestHelper h) {
        var serializer = new PreparationSerializer();
        for (var quality : List.of(FlexibleFormulation.fixed(), metadata())) {
            var r = recipe(quality, input(Ingredient.of(Items.APPLE)));
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                serializer.toNetwork(buffer, r); var copy = serializer.fromNetwork(r.getId(), buffer);
                h.assertTrue(copy.nativeQuality().equals(quality) && copy.nativeQuality().fingerprint().equals(quality.fingerprint()), "network round trip preserves exact semantic metadata");
                h.assertTrue(buffer.readableBytes() == 0, "network consumes full quality payload");
            } finally { buffer.release(); }
            var json = com.google.gson.JsonParser.parseString("{\"ingredients\":[{\"item\":\"minecraft:apple\"}],\"result\":{\"item\":\"minecraft:bread\"},\"stages\":[{\"action\":\"mix\",\"duration_ticks\":20}]}").getAsJsonObject();
            json.add("native_quality", quality.toJson());
            h.assertTrue(serializer.fromJson(r.getId(), json).nativeQuality().equals(quality), "actual ingredient/result JSON parser preserves native_quality");
        }
        h.succeed();
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void evaluatorMatchesPinnedOriginalRecipeIdAndActualWorldSeed(GameTestHelper h) {
        var r = recipe(metadata(), input(Ingredient.of(Items.APPLE)), input(Ingredient.of(Items.CARROT)));
        for (int apples = 1; apples <= 8; apples++) {
            List<ItemStack> paid = new ArrayList<>();
            for (int i = 0; i < apples; i++) paid.add(new ItemStack(Items.APPLE));
            for (int i = apples; i < 9; i++) paid.add(new ItemStack(Items.CARROT));
            var expected = QualityEvaluator.evaluate(paid, r.inputs().stream().map(PreparationRecipe.Input::ingredient).toList(),
                new ResourceLocation(r.nativeQuality().sourceRecipeId()), h.getLevel().getSeed());
            h.assertTrue(r.evaluateQuality(h.getLevel(), paid) == expected, "same pinned evaluator/original ID/actual seed for ratio " + apples);
        }
        var fixed = recipe(FlexibleFormulation.fixed(), input(Ingredient.of(Items.APPLE)));
        h.assertTrue(fixed.evaluateQuality(h.getLevel(), List.of(new ItemStack(Items.APPLE))) == Quality.STANDARD, "fixed recipe is standard independent of output profile");
        var legacy = new PreparationRecipe(fixed.getId(), fixed.inputs(), fixed.getResultItem(h.getLevel().registryAccess()), fixed.surface(), fixed.finish(), fixed.servings(), fixed.workClass(), fixed.stages(), fixed.cookTicks());
        h.assertTrue(!legacy.flexible(), "backward constructor defaults fixed"); h.succeed();
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void savedFlexibleReceiptRejectsOversizeAndStaleQuality(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 2, 1);
        h.setBlock(pos, ForgeRegistries.BLOCKS.getValue(new ResourceLocation("kaleidoscope_cookery:chopping_board")).defaultBlockState());
        var entity = h.getLevel().getBlockEntity(h.absolutePos(pos));
        KitchenInventory inv = entity.getCapability(KitchenContent.KITCHEN).orElseThrow(() -> new AssertionError("attached kitchen missing"));
        var r = h.getLevel().getRecipeManager().getAllRecipesFor(CookingRecipes.PREPARATION_TYPE.get()).stream()
            .filter(candidate -> candidate.flexible() && inv.accepts(candidate) && candidate.inputs().stream().allMatch(i -> i.ingredient().getItems().length > 0))
            .findFirst().orElseThrow(() -> new AssertionError("actual registered flexible recipe required"));
        CompoundTag empty = inv.serializeNBT();
        CompoundTag state = empty.copy(); state.putString("Recipe", r.getId().toString()); state.putString("RecipeSignature", inv.fingerprint(r));
        var paid = new net.minecraft.nbt.ListTag();
        for (var group : r.inputs()) { ItemStack actual = group.ingredient().getItems()[0].copy(); actual.setCount(1); paid.add(actual.save(new CompoundTag())); }
        state.put("Paid", paid);
        ItemStack bundle = new ItemStack(com.bettercontent.bettercooking.CookingItems.PREPARED_BATCH.get());
        bundle.getOrCreateTag().put(KitchenInventory.RESUME, state.copy());
        h.assertTrue(inv.restore(bundle), "registered flexible paid count1 receipt restores");
        inv.deserializeNBT(empty.copy());
        CompoundTag oversize = state.copy(); oversize.getList("Paid", 10).getCompound(0).putByte("Count", (byte) 2);
        bundle.getOrCreateTag().put(KitchenInventory.RESUME, oversize);
        h.assertTrue(!inv.restore(bundle) && !inv.occupied(), "saved oversized payment rejected without inventory mutation");
        CompoundTag stale = state.copy();
        var fixed = new PreparationRecipe(r.getId(), r.inputs(), r.getResultItem(h.getLevel().registryAccess()), r.surface(), r.finish(), r.servings(), r.workClass(), r.stages(), r.cookTicks());
        stale.putString("RecipeSignature", inv.fingerprint(fixed)); bundle.getOrCreateTag().put(KitchenInventory.RESUME, stale);
        h.assertTrue(!inv.restore(bundle) && !inv.occupied(), "quality mode reload invalidates saved signature before restore");
        h.succeed();
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void kitchenSignatureBindsNativeQualityAndPaidReceipt(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 2, 1);
        h.setBlock(pos, ForgeRegistries.BLOCKS.getValue(new ResourceLocation("kaleidoscope_cookery:chopping_board")).defaultBlockState());
        var entity = h.getLevel().getBlockEntity(h.absolutePos(pos));
        KitchenInventory inv = entity.getCapability(KitchenContent.KITCHEN).orElseThrow(() -> new AssertionError("attached kitchen missing"));
        var flexible = recipe(metadata(), input(Ingredient.of(Items.APPLE)));
        var fixed = recipe(FlexibleFormulation.fixed(), input(Ingredient.of(Items.APPLE)));
        h.assertTrue(!inv.fingerprint(flexible).equals(inv.fingerprint(fixed)), "native quality mode/source ID is part of kitchen reload/save fingerprint");
        h.succeed();
    }
}
