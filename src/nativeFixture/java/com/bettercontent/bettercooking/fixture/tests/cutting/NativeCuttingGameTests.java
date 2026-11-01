package com.bettercontent.bettercooking.fixture.tests.cutting;

import com.bettercontent.bettercooking.cutting.CuttingBoardAccess;
import com.bettercontent.bettercooking.cutting.CuttingPlan;
import com.bettercontent.bettercooking.cutting.CuttingSession;
import com.bettercontent.bettercooking.cutting.NativeCuttingRecipe;
import com.bettercontent.bettercooking.*;
import com.bettercontent.betterbumblezonecrops.api.CultivarAcquisition;
import com.bettercontent.betterindustrialheat.food.FoodThermalService;
import com.github.ysbbbbbb.kaleidoscopecookery.block.dispenser.ChoppingBoardDispenseBehavior;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.init.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

/** Authored real-world controls, NOT RUN. Separate batches serialize temporary actual RecipeManager changes.
 * No mock/FakePlayer, intake invocation, private counter writes or RNG substitution. */
@GameTestHolder("better_cooking_cutting")
@PrefixGameTestTemplate(false)
public final class NativeCuttingGameTests {
    @GameTest(templateNamespace="better_cooking", template="native_discovery", batch="cutting_raw", timeoutTicks=100)
    public static void native_raw_ready_phase_and_save_load(GameTestHelper helper) { exercise(helper, false, false, false, false); }
    @GameTest(templateNamespace="better_cooking", template="native_discovery", batch="cutting_dispenser", timeoutTicks=100)
    public static void actual_dispenser_debits_only_one_owned_input(GameTestHelper helper) { exercise(helper, true, false, false, false); }
    @GameTest(templateNamespace="better_cooking", template="native_discovery", batch="cutting_retry", timeoutTicks=100)
    public static void cancelled_real_spawn_retries_without_duplicate_prefix(GameTestHelper helper) { exercise(helper, false, true, false, false); }
    @GameTest(templateNamespace="better_cooking", template="native_discovery", batch="cutting_pie", timeoutTicks=100)
    public static void paid_pie_metadata_is_already_per_portion(GameTestHelper helper) { exercise(helper, false, false, true, false); }
    @GameTest(templateNamespace="better_cooking", template="native_discovery", batch="cutting_seed", timeoutTicks=100)
    public static void actual_wild_tomato_seed_gate_and_ordered_chance(GameTestHelper helper) { exercise(helper, false, false, false, true); }

    @GameTest(templateNamespace="better_cooking", template="native_discovery", batch="cutting_ready_break", timeoutTicks=100)
    public static void ready_unrolled_break_returns_original_without_chance(GameTestHelper helper) { exercise(helper, false, false, false, false, true, false, false); }
    @GameTest(templateNamespace="better_cooking", template="native_discovery", batch="cutting_partial_break", timeoutTicks=100)
    public static void partial_cancel_then_break_returns_only_undelivered_suffix(GameTestHelper helper) { exercise(helper, false, true, false, false, false, true, false); }
    @GameTest(templateNamespace="better_cooking", template="native_discovery", batch="cutting_pie_router", timeoutTicks=100)
    public static void actual_vanilla_dispenser_routes_paid_pie_and_native_knife(GameTestHelper helper) { exercise(helper, true, false, true, false, false, false, true); }
    @GameTest(templateNamespace="better_cooking", template="native_discovery", batch="cutting_router_fallback", timeoutTicks=100)
    public static void nonboard_dispenser_keeps_original_behavior(GameTestHelper helper) {
        var level = helper.getLevel(); BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos.south(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH));
        var inventory = (net.minecraft.world.Container) level.getBlockEntity(pos); inventory.setItem(0, new ItemStack(Items.PAPER, 2));
        ((DispenserBlock) Blocks.DISPENSER).tick(level.getBlockState(pos), level, pos, level.random);
        helper.assertTrue(inventory.getItem(0).getCount() == 1, "No-board target must keep actual original one-item dispense behavior");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream().anyMatch(e -> e.getItem().is(Items.PAPER)), "Original behavior must actually deliver paper, not swallow it");
        helper.succeed();
    }
    private static void pureLoot(GameTestHelper helper, ChoppingBoardBlockEntity board) {
        CompoundTag before = ((CuttingBoardAccess) board).cooking$cuttingSession().save();
        for (int i = 0; i < 2; i++) {
            var context = new net.minecraft.world.level.storage.loot.LootParams.Builder(helper.getLevel())
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(board.getBlockPos()))
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, ItemStack.EMPTY)
                    .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY, board);
            board.getBlockState().getDrops(context);
            helper.assertTrue(before.equals(((CuttingBoardAccess) board).cooking$cuttingSession().save()), "Repeated actual loot queries may not select, debit, advance cursor or mutate paid provenance");
        }
    }
    private static Item registered(String id) {
        ResourceLocation key = Objects.requireNonNull(ResourceLocation.tryParse(id));
        Item item = ForgeRegistries.ITEMS.getValue(key);
        if (item == null || item == Items.AIR) throw new IllegalStateException("Native fixture requires actual registered " + id);
        return item;
    }
    private static void exercise(GameTestHelper helper, boolean dispenser, boolean cancelOne, boolean pie, boolean wild) {
        exercise(helper, dispenser, cancelOne, pie, wild, false, false, false);
    }
    private static void exercise(GameTestHelper helper, boolean dispenser, boolean cancelOne, boolean pie, boolean wild, boolean destroyReady, boolean destroyAfterFailure, boolean vanillaRouter) {
        helper.assertTrue(!CookingMod.discoveryOnly(), "Cutting execution is not discovery-only acceptance");
        var level = helper.getLevel(); var manager = level.getRecipeManager();
        List<Recipe<?>> savedRecipes = new ArrayList<>(manager.getRecipes());
        Item source = pie ? registered("farmersdelight:apple_pie") : wild ? registered("farmersdelight:wild_tomatoes") : Items.BEEF;
        Item edible = pie ? registered("farmersdelight:apple_pie_slice") : wild ? registered("farmersdelight:tomato") : Items.COOKED_BEEF;
        Item seeds = wild ? registered("farmersdelight:tomato_seeds") : Items.WHEAT_SEEDS;
        ItemStack knife = new ItemStack(ModItems.IRON_KITCHEN_KNIFE.get());
        List<CuttingPlan.Output> outputs = pie ? List.of(new CuttingPlan.Output(new ItemStack(edible, 4), 1))
                : wild ? List.of(new CuttingPlan.Output(new ItemStack(seeds), 1), new CuttingPlan.Output(new ItemStack(edible), .2))
                : List.of(new CuttingPlan.Output(new ItemStack(edible, 2), 1), new CuttingPlan.Output(new ItemStack(Items.BONE, 3), 0), new CuttingPlan.Output(new ItemStack(Items.LEATHER), 1));
        var recipe = new NativeCuttingRecipe(ResourceLocation.tryParse("better_cooking:fixture_cutting"),
                new CuttingPlan(Ingredient.of(source), Ingredient.of(knife), outputs, 1, null, null));
        List<Recipe<?>> temporary = new ArrayList<>();
        savedRecipes.stream().filter(r -> r.getType() != ModRecipes.CHOPPING_BOARD_RECIPE).forEach(temporary::add); temporary.add(recipe);
        manager.replaceRecipes(temporary);
        try { transaction: {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            level.setBlockAndUpdate(pos, ModBlocks.CHOPPING_BOARD.get().defaultBlockState());
            var board = (ChoppingBoardBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(board instanceof CuttingBoardAccess, "Root must activate actual native output mixin");
            ItemStack input = new ItemStack(source, 3);
            if (!wild) {
                input.getOrCreateTag().putInt(CookingRuntime.QUALITY, 3);
                FoodThermalService.INSTANCE.state(input, FoodThermalService.Storage.AMBIENT, level.getGameTime()).putDouble("decay", .8);
            }
            if (pie) {
                CompoundTag metadata = new CompoundTag(), nutrients = new CompoundTag(); nutrients.putDouble("grains", .08);
                metadata.put("nutrients", nutrients); metadata.putDouble("sugar", .03); metadata.putDouble("alcohol", 0);
                metadata.putDouble("spice", .02); metadata.putDouble("hydration", .01); metadata.putDouble("quenching", .01);
                input.getOrCreateTag().put(CookingRuntime.SERVING, metadata);
                helper.assertTrue(CookingRuntime.profile(input) != null && CookingRuntime.profile(input).protectedMeal(), "Actual pie policy must be resolved/protected before this control");
            }
            BlockPos knifeDispenser = pos.west();
            if (dispenser) {
                BlockPos dispenserPos = pos.north();
                level.setBlockAndUpdate(dispenserPos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH));
                if (vanillaRouter) {
                    var inventory = (net.minecraft.world.Container) level.getBlockEntity(dispenserPos); inventory.setItem(0, input);
                    ((DispenserBlock) Blocks.DISPENSER).tick(level.getBlockState(dispenserPos), level, dispenserPos, level.random);
                    helper.assertTrue(inventory.getItem(0).getCount() == 2, "Actual vanilla dispenser router must preserve the two unpaid pie inputs");
                    level.setBlockAndUpdate(knifeDispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
                    ((net.minecraft.world.Container) level.getBlockEntity(knifeDispenser)).setItem(0, knife.copy());
                } else new ChoppingBoardDispenseBehavior(false).dispense(new BlockSourceImpl(level, dispenserPos), input);
            } else helper.assertTrue(board.onPutItem(level, null, input), "Actual actorless native insertion must accept reviewed bulk cut");
            helper.assertTrue(input.getCount() == 2 && board.getCurrentCutStack().getCount() == 1, "Actual native insertion/dispenser must pay exactly one, not two");
            CuttingSession inserted = ((CuttingBoardAccess) board).cooking$cuttingSession();
            helper.assertTrue(inserted != null && !inserted.save().getBoolean("selected"), "Insertion must not select chance outputs");
            if (vanillaRouter) ((DispenserBlock) Blocks.DISPENSER).tick(level.getBlockState(knifeDispenser), level, knifeDispenser, level.random);
            else helper.assertTrue(board.onCutItem(level, null, knife), "Actual native knife action must complete one cut");
            helper.assertTrue(board.getCurrentCutCount() == 1 && !inserted.save().getBoolean("selected"), "One knife action reaches ready but does not replace native collection phase or roll early");
            CompoundTag beforeReload = board.saveWithFullMetadata(); board.load(beforeReload);
            helper.assertTrue(((CuttingBoardAccess) board).cooking$cuttingSession().save().equals(beforeReload.getCompound(CuttingSession.KEY)), "Actual native save/load must preserve paid source and frozen plan");
            AABB bounds = new AABB(pos).inflate(2); Set<UUID> existing = new HashSet<>();
            level.getEntitiesOfClass(ItemEntity.class, bounds).forEach(e -> existing.add(e.getUUID()));
            if (destroyReady) {
                pureLoot(helper, board);
                helper.assertTrue(level.destroyBlock(pos, true), "Actual ready board destruction must succeed");
                var ground = level.getEntitiesOfClass(ItemEntity.class, bounds).stream().filter(e -> !existing.contains(e.getUUID())).map(ItemEntity::getItem).toList();
                helper.assertTrue(ground.stream().filter(s -> s.is(source)).mapToInt(ItemStack::getCount).sum() == 1, "Ready unrolled destruction must refund exactly one actual paid original");
                helper.assertTrue(ground.stream().noneMatch(s -> s.is(edible) || s.is(Items.LEATHER) || s.is(Items.BONE)), "Breaking before selection must not roll or create cut outputs");
                helper.assertTrue(!board.onCutItem(level, null, knife), "Removed old board reference must not emit after loot takes ownership");
                break transaction;
            }
            AtomicBoolean cancelled = new AtomicBoolean();
            Consumer<EntityJoinLevelEvent> listener = event -> {
                if (event.getLevel() == level && event.getEntity() instanceof ItemEntity entity && bounds.contains(entity.position())
                        && entity.getItem().is(Items.LEATHER) && cancelled.compareAndSet(false, true)) event.setCanceled(true);
            };
            if (cancelOne) MinecraftForge.EVENT_BUS.addListener(listener);
            try {
                if (cancelOne) {
                    boolean rejected = false;
                    try { board.onCutItem(level, null, ItemStack.EMPTY); }
                    catch (IllegalStateException expected) { if (!expected.getMessage().contains("spawn rejected")) throw expected; rejected = true; }
                    helper.assertTrue(rejected && cancelled.get() && ((CuttingBoardAccess) board).cooking$cuttingSession().cursor() == 1,
                            "Actual cancelled spawn retains the paid transaction after one successful prefix output");
                }
            } finally { if (cancelOne) MinecraftForge.EVENT_BUS.unregister(listener); }
            if (destroyAfterFailure) {
                pureLoot(helper, board);
                helper.assertTrue(level.destroyBlock(pos, true), "Actual partial-delivery board destruction must succeed");
                var ground = level.getEntitiesOfClass(ItemEntity.class, bounds).stream().filter(e -> !existing.contains(e.getUUID())).map(ItemEntity::getItem).toList();
                helper.assertTrue(ground.stream().filter(s -> s.is(edible)).mapToInt(ItemStack::getCount).sum() == 2, "Breaking must not duplicate the already-delivered edible prefix");
                helper.assertTrue(ground.stream().filter(s -> s.is(Items.LEATHER)).mapToInt(ItemStack::getCount).sum() == 1 && ground.stream().noneMatch(s -> s.is(source)), "Breaking must deliver only undelivered suffix, not original input");
                helper.assertTrue(!board.onCutItem(level, null, knife), "Removed partial board must not repeat delivered suffix");
                break transaction;
            }
            if (vanillaRouter) ((DispenserBlock) Blocks.DISPENSER).tick(level.getBlockState(knifeDispenser), level, knifeDispenser, level.random);
            else helper.assertTrue(board.onCutItem(level, null, ItemStack.EMPTY), "Actual native ready-output collection must finish/retry");
            helper.assertTrue(board.getCurrentCutStack().isEmpty() && ((CuttingBoardAccess) board).cooking$cuttingSession() == null, "Successful delivery clears native input/owned cursor once");
            List<ItemStack> delivered = level.getEntitiesOfClass(ItemEntity.class, bounds).stream().filter(e -> !existing.contains(e.getUUID())).map(ItemEntity::getItem).toList();
            int edibleCount = delivered.stream().filter(s -> s.is(edible)).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(wild ? edibleCount == 0 || edibleCount == 1 : edibleCount == (pie ? 4 : 2), "Exact authored food count/chance retained with no duplicate retry prefix");
            if (wild) {
                int allowedSeeds = CultivarAcquisition.restrictNewOutput(level.dimension().location(), new ItemStack(seeds)).getCount();
                helper.assertTrue(delivered.stream().filter(s -> s.is(seeds)).mapToInt(ItemStack::getCount).sum() == allowedSeeds, "All actual new canonical tomato seeds must obey provider origin authority");
            } else {
                helper.assertTrue(delivered.stream().noneMatch(s -> s.is(Items.BONE)), "Zero-chance byproduct must not appear");
                for (ItemStack output : delivered) if (output.is(edible)) {
                    helper.assertTrue(output.getTag().getInt(CookingRuntime.QUALITY) == 3, "Actual paid Poor quality retained without reroll");
                    helper.assertTrue(Math.abs(output.getTag().getCompound("better_industrial_heat_food").getDouble("decay") - .8) < .00001, "Oldest input clock retained on actual cut output");
                    if (pie) helper.assertTrue(Math.abs(output.getTag().getCompound(CookingRuntime.SERVING).getCompound("nutrients").getDouble("grains") - .08) < .00001,
                            "Pie per-portion metadata must not be divided by four again");
                }
            }
        } } finally { manager.replaceRecipes(savedRecipes); }
        helper.succeed();
    }
}
