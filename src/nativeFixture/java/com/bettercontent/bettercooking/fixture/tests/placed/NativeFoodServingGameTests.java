package com.bettercontent.bettercooking.fixture.tests.placed;

import com.bettercontent.bettercooking.placed.NativeFoodServings;
import com.bettercontent.bettercooking.placed.PlacedProvenanceData;
import com.bettercontent.bettercooking.*;
import com.bettercontent.betterspicyfarmers.seasoning.ServingContext;
import com.bettercontent.betterindustrialheat.api.CookingAgeApi;
import com.bettercontent.betterindustrialheat.food.FoodThermalService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.List;
import com.github.ysbbbbbb.kaleidoscopecookery.block.food.*;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.NinePart;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Actual registered block/item placement + native use. Requires one REAL connected fixture client.
 * No mock/FakePlayer, direct dispatcher calls, private intake writes or synthetic completion receipts. */
@GameTestHolder("better_cooking_placed")
@PrefixGameTestTemplate(false)
public final class NativeFoodServingGameTests {
    @GameTest(templateNamespace = "better_cooking", template = "native_discovery", timeoutTicks = 100)
    public static void connected_native_single_bite(GameTestHelper helper) { exercise(helper, 0); }
    @GameTest(templateNamespace = "better_cooking", template = "native_discovery", timeoutTicks = 100)
    public static void connected_native_pair_left_bite(GameTestHelper helper) { exercise(helper, 1); }
    @GameTest(templateNamespace = "better_cooking", template = "native_discovery", timeoutTicks = 100)
    public static void connected_native_large_corner_bite(GameTestHelper helper) { exercise(helper, 2); }
    @GameTest(templateNamespace = "better_cooking", template = "native_discovery", timeoutTicks = 100)
    public static void connected_dark_cuisine_item_two_bite_one(GameTestHelper helper) {
        exercise(helper, 0, "kaleidoscope_cookery:dark_cuisine");
    }
    private static void exercise(GameTestHelper helper, int family) { exercise(helper, family, null); }
    private static void exercise(GameTestHelper helper, int family, String exactId) {
        helper.assertTrue(!CookingMod.discoveryOnly() && NativeFoodServings.enabled(), "Real native serving adapter must be explicitly enabled, not discovery mode");
        var level = helper.getLevel();
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> !(p instanceof FakePlayer) && p.connection != null && p.serverLevel() == level).findFirst()
                .orElseThrow(() -> new IllegalStateException("This native control requires a genuine connected fixture client in the GameTest level"));
        FoodBiteBlock food = BuiltInRegistries.BLOCK.stream().filter(block ->
                family == 1 ? block instanceof FoodBiteOneByTwoBlock : family == 2 ? block instanceof FoodBiteThreeByThreeBlock
                        : block instanceof FoodBiteBlock && !(block instanceof FoodBiteOneByTwoBlock) && !(block instanceof FoodBiteThreeByThreeBlock))
                .map(block -> (FoodBiteBlock) block)
                .filter(block -> block.asItem() instanceof BlockItem)
                .filter(block -> exactId == null || BuiltInRegistries.BLOCK.getKey(block).toString().equals(exactId))
                .filter(block -> CulinaryConsumption.manages(new ItemStack(block.asItem())))
                .findFirst().orElseThrow(() -> new IllegalStateException("No qualified actual KC food family " + family));
        if (exactId != null) {
            ItemStack nativeWhole = new ItemStack(food.asItem()); // deliberately UNTAGGED: native default is already quality-neutral
            var nativeProperties = food.asItem().getFoodProperties(nativeWhole, player);
            helper.assertTrue(nativeProperties != null && nativeProperties.getNutrition() == 2, "Pinned DarkCuisine item action is raw 2, not standard-quality .6 or item/maxBites");
            helper.assertTrue(CookingRuntime.resolve(nativeWhole).orElseThrow().hunger() == 2, "Root must preserve whole native-intake item hunger 2");
        }
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 3, 2));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            level.setBlockAndUpdate(anchor.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(anchor.offset(x, 0, z), Blocks.AIR.defaultBlockState());
        }
        player.teleportTo(level, anchor.getX() + .5, anchor.getY() + 1, anchor.getZ() + 2.5, 0, 0);
        ItemStack original = new ItemStack(food.asItem());
        original.getOrCreateTag().putInt(CookingRuntime.QUALITY, 3); // controlled POOR input: not a fabricated completion
        if (original.isEdible()) FoodThermalService.INSTANCE.state(original, FoodThermalService.Storage.AMBIENT, level.getGameTime()).putDouble("decay", .8);
        else {
            ItemStack aged = new ItemStack(Items.BEEF);
            FoodThermalService.INSTANCE.state(aged, FoodThermalService.Storage.AMBIENT, level.getGameTime()).putDouble("decay", .8);
            ListTag children = new ListTag(); children.add(aged.save(new CompoundTag()));
            CompoundTag serving = new CompoundTag(); serving.put(CookingRuntime.AGE_INPUTS, children);
            original.getOrCreateTag().put(CookingRuntime.SERVING, serving);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, original);
        BlockHitResult placementHit = new BlockHitResult(Vec3.atCenterOf(anchor.below()).add(0, .5, 0), Direction.UP, anchor.below(), false);
        InteractionResult placed = ((BlockItem) original.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, original, placementHit));
        helper.assertTrue(placed.consumesAction(), "Actual native BlockItem placement must commit");
        var anchorBefore = level.getBlockState(anchor);
        helper.assertTrue(anchorBefore.getBlock() == food, "Placement must create actual family anchor");
        var persisted = level.getDataStorage().get(PlacedProvenanceData::load, "better_cooking_placed_provenance");
        helper.assertTrue(persisted != null && persisted.original(anchor, anchorBefore, food.getMaxBites()).isPresent(), "Successful native placement must persist original whole item once");
        BlockPos clicked = family == 1 ? anchor.relative(anchorBefore.getValue(FoodBiteBlock.FACING).getClockWise())
                : family == 2 ? anchor.offset(1, 0, 1) : anchor;
        var clickedBefore = level.getBlockState(clicked);
        if (family == 2) helper.assertTrue(clickedBefore.getValue(FoodBiteThreeByThreeBlock.PART) != NinePart.CENTER, "Use a genuine noncentral part");
        ItemStack portion = NativeFoodServings.preview(level, clicked, clickedBefore);
        var resolved = CookingRuntime.resolve(portion).orElseThrow();
        if (exactId != null) {
            var nativeBite = portion.getTag().getCompound("better_cooking:native_bite");
            helper.assertTrue(nativeBite.getInt("hunger") == 1 && nativeBite.getDouble("saturation_modifier") == 0 && nativeBite.getInt("max_portions") == 3,
                    "Typed original DarkCuisine block properties must survive shared SavedData preview overlay");
            helper.assertTrue(food.getMaxBites() == 3 && CookingRuntime.profile(portion).quantityBasis().equals("native_intake"), "Pinned special has three actions, not protected-meal per-portion semantics");
            helper.assertTrue(resolved.hunger() == 1, "Root special native bite must remain exact 1, not whole-item 2 or divide-rounded 2/3 or Poor-scaled hunger");
        }
        helper.assertTrue(portion.getTag().getBoolean(CookingRuntime.SINGLE_PORTION), "Preview must resolve exactly one portion");
        helper.assertTrue(portion.getTag().getInt(CookingRuntime.QUALITY) == 3, "Placed original Poor quality cannot be rerolled/laundered");
        ItemStack ageProbe = new ItemStack(Items.COOKED_BEEF);
        CookingAgeApi.transfer(List.of(new CookingAgeApi.Input(portion, FoodThermalService.Storage.AMBIENT)), ageProbe, level.getGameTime());
        helper.assertTrue(ageProbe.hasTag() && Math.abs(ageProbe.getTag().getCompound("better_industrial_heat_food").getDouble("decay") - .8) < .00001,
                "Actual placed/native preview must retain oldest direct or nested paid-input age, not fresh replacement");
        long sequence = player.getPersistentData().getLong("better_cooking:completion_sequence");
        player.getFoodData().setFoodLevel(0); player.getFoodData().setSaturation(0);
        InteractionResult eaten = food.use(clickedBefore, level, clicked, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false));
        helper.assertTrue(eaten.consumesAction(), "Actual native use must consume one bite");
        helper.assertTrue(NativeFoodServings.remaining(level.getBlockState(anchor)) == food.getMaxBites() - 1, "Native block must lose exactly one portion");
        helper.assertTrue(player.getPersistentData().getLong("better_cooking:completion_sequence") == sequence + 1, "Actual native use must dispatch exactly one real connected-player completion");
        helper.assertTrue(player.getFoodData().getFoodLevel() == Math.min(20, resolved.hunger()), "Poor quality hunger must be applied once, not native ratio multiplied again");
        helper.assertTrue(CulinaryEffects.currentSource().isEmpty() && ServingContext.currentOr(ItemStack.EMPTY).isEmpty() && !NativeFoodServings.inBiteScope(), "Every synchronous context must unwind after real use");
        helper.succeed();
    }
}
