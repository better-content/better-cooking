package com.bettercontent.bettercooking.fixture.tests.stockpot;

import com.bettercontent.bettercooking.CookingItems;
import com.bettercontent.bettercooking.CookingMod;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Negative real-native controls only: no fabricated completed work, fake player, or shortened cooking. */
@GameTestHolder(CookingMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PreparedStockpotGameTests {
    private static StockpotBlockEntity place(GameTestHelper helper) {
        BlockPos pos=new BlockPos(1,1,1);helper.setBlock(pos,ModBlocks.STOCKPOT.get().defaultBlockState());
        return (StockpotBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=40)
    public static void uncommittedBatchRejectsBeforeDebit(GameTestHelper helper) {
        helper.assertTrue(!CookingMod.discoveryOnly(),"requires implementation-mode fixture, not discovery qualification");
        StockpotBlockEntity pot=place(helper);ItemStack unpaid=new ItemStack(CookingItems.PREPARED_BATCH.get());
        CompoundTag before=unpaid.save(new CompoundTag());
        helper.assertTrue(!pot.addSoupBase(helper.getLevel(),null,unpaid),"batch cannot become a paid native soup base");
        helper.assertTrue(!pot.addIngredient(helper.getLevel(),null,unpaid),"uncommitted batch rejected");
        helper.assertTrue(before.equals(unpaid.save(new CompoundTag()))&&pot.isEmpty(),"rejection before split");
        helper.assertTrue(pot.getStatus()==StockpotBlockEntity.PUT_SOUP_BASE,"rejection leaves native empty state");helper.succeed();
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=40)
    public static void corruptOwnedStateRefusesBeforeChunkRemoval(GameTestHelper helper) {
        helper.assertTrue(!CookingMod.discoveryOnly(),"requires implementation-mode fixture");
        StockpotBlockEntity pot=place(helper);CompoundTag state=pot.saveWithFullMetadata();
        CompoundTag emptyFixture=state.copy();
        state.putInt("Status",StockpotBlockEntity.PUT_INGREDIENT);
        state.putString("better_cooking:prepared_stockpot","invalid ownership");pot.load(state);
        CompoundTag before=pot.saveWithFullMetadata();
        boolean removed=helper.getLevel().setBlock(pot.getBlockPos(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        helper.assertTrue(!removed,"invalid own state refuses native Level chunk mutation");
        helper.assertTrue(helper.getLevel().getBlockEntity(pot.getBlockPos())==pot&&!pot.isRemoved(),"old actual BE remains installed");
        helper.assertTrue(before.equals(pot.saveWithFullMetadata()),"refusal preserves exact native/owned state");
        pot.load(emptyFixture); // Restore only this never-paid empty corruption fixture for normal cleanup.
        helper.succeed();
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=40)
    public static void malformedSealedReloadNeverReturnsWater(GameTestHelper helper) {
        helper.assertTrue(!CookingMod.discoveryOnly(),"requires implementation-mode fixture");
        StockpotBlockEntity pot=place(helper);CompoundTag state=pot.saveWithFullMetadata();
        CompoundTag emptyFixture=state.copy();
        state.putInt("Status",StockpotBlockEntity.PUT_INGREDIENT);state.putString("SoupBaseId","kaleidoscope_cookery:water");
        state.putString("better_cooking:prepared_stockpot","invalid persisted ownership");pot.load(state);
        ItemStack bucket=new ItemStack(Items.BUCKET);
        helper.assertTrue(!pot.removeSoupBase(helper.getLevel(),null,bucket),"malformed seal fails closed before native water return");
        helper.assertTrue(bucket.getCount()==1,"bucket not debited");
        CompoundTag saved=pot.saveWithFullMetadata();pot.load(saved);
        helper.assertTrue(!pot.removeSoupBase(helper.getLevel(),null,bucket),"second save/reload preserves deny even with no native inputs");
        pot.load(emptyFixture); // No paid input/output exists in this deliberately corrupt empty fixture.
        helper.succeed();
    }
}
