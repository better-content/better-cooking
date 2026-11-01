package com.bettercontent.bettercooking.fixture.tests.kitchen;

import com.bettercontent.bettercooking.kitchen.KitchenContent;
import com.bettercontent.bettercooking.kitchen.KitchenHooks;
import com.bettercontent.bettercooking.kitchen.KitchenInventory;
import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.CookingItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Focused disposable-server controls, not connected-client or full culinary qualification. */
@GameTestHolder(CookingMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KitchenGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private static KitchenInventory kitchen(GameTestHelper helper) {
        var id = new ResourceLocation("kaleidoscope_cookery", "chopping_board");
        helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(id), "pinned native board registered");
        helper.setBlock(POS, ForgeRegistries.BLOCKS.getValue(id).defaultBlockState());
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(POS));
        helper.assertTrue(be != null, "native board has block entity");
        return be.getCapability(KitchenContent.KITCHEN).orElseThrow(() -> new AssertionError("attached kitchen missing"));
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void kitchenAttachedNbtRoundTrip(GameTestHelper helper) {
        KitchenInventory inv = kitchen(helper);
        inv.setStackInSlot(0, new ItemStack(Items.APPLE, 3)); inv.setStackInSlot(9, new ItemStack(Items.BREAD, 2));
        CompoundTag state = inv.serializeNBT();
        state.putString("Recipe", "better_cooking:fixture"); state.putInt("Stage", 1); state.putInt("Repetition", 2);
        ListTag paid = new ListTag(); ItemStack potato = new ItemStack(Items.POTATO, 4); potato.getOrCreateTag().putLong("FixtureOldestAge", 17L); paid.add(potato.save(new CompoundTag())); state.put("Paid", paid);
        inv.deserializeNBT(state);
        helper.assertTrue(inv.getStackInSlot(0).getCount() == 3 && inv.getStackInSlot(9).getCount() == 2, "inventory/output persisted");
        helper.assertTrue(inv.progress().stage() == 1 && inv.progress().repetition() == 2 && !inv.progress().active(), "only completed repetitions persist");
        helper.assertTrue(inv.serializeNBT().getList("Paid", 10).equals(paid), "actual paid ingredient tags preserved");
        helper.assertTrue(KitchenHooks.occupied(helper.getLevel().getBlockEntity(helper.absolutePos(POS))), "native mixing locked while occupied");
        helper.succeed();
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void kitchenRecoveryExactlyOnce(GameTestHelper helper) {
        KitchenInventory inv = kitchen(helper);
        inv.setStackInSlot(0, new ItemStack(Items.APPLE, 3)); inv.setStackInSlot(9, new ItemStack(Items.BREAD, 2));
        var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(com.bettercontent.bettercooking.recipe.CookingRecipes.PREPARATION_TYPE.get());
        helper.assertTrue(!recipes.isEmpty(), "compiled preparation recipes available");
        var recipe = recipes.stream().filter(r -> r.inputs().stream().anyMatch(i -> i.ingredient().test(new ItemStack(Items.MILK_BUCKET)))).findFirst().orElseThrow(() -> new AssertionError("milk preparation recipe required"));
        CompoundTag state = inv.serializeNBT(); state.putString("Recipe", recipe.getId().toString()); state.putString("RecipeSignature", inv.fingerprint(recipe));
        ListTag paid = new ListTag();
        for (var input : recipe.inputs()) {
            ItemStack original = input.ingredient().test(new ItemStack(Items.MILK_BUCKET)) ? new ItemStack(Items.MILK_BUCKET) : input.ingredient().getItems()[0].copy();
            original.setCount(input.count()); paid.add(original.save(new CompoundTag()));
        }
        state.put("Paid", paid); inv.deserializeNBT(state);
        inv.recover(); inv.recover();
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(POS)).inflate(2));
        int apples = 0, bread = 0, batches = 0, buckets = 0;
        for (var entity : drops) {
            ItemStack stack = entity.getItem();
            if (stack.is(Items.APPLE)) apples += stack.getCount();
            if (stack.is(Items.BREAD)) bread += stack.getCount();
            if (stack.is(Items.BUCKET) || stack.is(Items.MILK_BUCKET)) buckets += stack.getCount();
            if (stack.is(CookingItems.PREPARED_BATCH.get())) {
                batches += stack.getCount();
                helper.assertTrue(stack.getTag() != null && stack.getTag().getCompound(KitchenInventory.RESUME).getList("Paid", 10).equals(paid), "paid milk remains inside resumable bundle");
            }
        }
        helper.assertTrue(apples == 3 && bread == 2 && batches == 1 && buckets == 0, "no duplicated input/output/remainder on repeated recovery");
        helper.assertTrue(!inv.occupied(), "station cleared before recovery returns"); helper.succeed();
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void kitchenRemovedRecipeRefundsRawOnce(GameTestHelper helper) {
        KitchenInventory inv = kitchen(helper);
        CompoundTag state = inv.serializeNBT(); state.putString("Recipe", "better_cooking:removed_fixture");
        ListTag paid = new ListTag(); ItemStack original = new ItemStack(Items.MILK_BUCKET); original.getOrCreateTag().putLong("FixtureOldestAge", 17);
        paid.add(original.save(new CompoundTag())); state.put("Paid", paid); inv.deserializeNBT(state);
        inv.recover(); inv.recover();
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(POS)).inflate(2));
        helper.assertTrue(drops.size() == 1 && ItemStack.isSameItemSameTags(drops.get(0).getItem(), original), "removed recipe returns original paid milk once, no empty bucket or stranded bundle");
        helper.succeed();
    }
    @GameTest(template = "forge:empty", timeoutTicks = 40)
    public static void kitchenRejectsFakePlayer(GameTestHelper helper) {
        KitchenInventory inv = kitchen(helper); inv.setStackInSlot(0, new ItemStack(Items.APPLE, 3));
        var fake = FakePlayerFactory.getMinecraft(helper.getLevel());
        helper.assertTrue(!inv.valid(fake) && !inv.begin(fake), "fake automation rejected before debit");
        helper.assertTrue(inv.getStackInSlot(0).getCount() == 3, "rejected action leaves inputs intact"); helper.succeed();
    }
}
