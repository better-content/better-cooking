package com.bettercontent.bettercooking.fixture.tests.stockpot;

import com.bettercontent.bettercooking.CookingItems;
import com.bettercontent.bettercooking.stockpot.PreparedStockpotStateAccess;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import java.util.List;

/** Real-client harness assertions on genuinely prepared native pots. NEVER creates work/batches or advances time. */
public final class PreparedStockpotBreakAssertions {
    private PreparedStockpotBreakAssertions() {}
    public static void healthyUncooked(GameTestHelper h,StockpotBlockEntity actual) {
        h.assertTrue(actual.getStatus()==IStockpot.PUT_INGREDIENT,"actual uncooked native phase");unfinished(h,actual);
    }
    public static void healthyCooking(GameTestHelper h,StockpotBlockEntity actual) {
        h.assertTrue(actual.getStatus()==IStockpot.COOKING,"actual native cooking phase; no CurrentTick shortening");unfinished(h,actual);
    }
    public static void healthyFinished(GameTestHelper h,StockpotBlockEntity actual) {
        h.assertTrue(actual.getStatus()==IStockpot.FINISHED,"actual full-duration finish");
        h.assertTrue(actual.getTakeoutCount()==actual.getResult().getCount(),"no servings previously extracted");finished(h,actual);
    }
    public static void healthyPartiallyServed(GameTestHelper h,StockpotBlockEntity actual) {
        h.assertTrue(actual.getStatus()==IStockpot.FINISHED,"actual finish phase");
        h.assertTrue(actual.getTakeoutCount()>0&&actual.getTakeoutCount()<actual.getResult().getCount(),"actual native takeout prefix exists");finished(h,actual);
    }
    /** Run after the matching healthy phase assertion; this executes a real physical player break. */
    public static void healthyPhysicalPlayerBreak(GameTestHelper h,StockpotBlockEntity actual,
            net.minecraft.server.level.ServerPlayer actualPlayer) {
        h.assertTrue(!(actualPlayer instanceof net.minecraftforge.common.util.FakePlayer)&&!actualPlayer.isCreative()
            &&actualPlayer.serverLevel()==h.getLevel()
            &&h.getLevel().getServer().getPlayerList().getPlayer(actualPlayer.getUUID())==actualPlayer,
            "actual connected survival player executes healthy physical break");
        h.assertTrue(actualPlayer.getMainHandItem().is(net.minecraft.world.item.Items.IRON_PICKAXE),"ordinary actual break tool");
        var access=(PreparedStockpotStateAccess)(Object)actual;
        h.assertTrue(access.cooking$isPreparedStockpotSealed()
            &&com.bettercontent.bettercooking.stockpot.PreparedStockpotBreakPolicy.evaluate(access).allowed(),"healthy current owned break admitted");
        var box=new net.minecraft.world.phys.AABB(actual.getBlockPos()).inflate(5);
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).isEmpty(),"exclusive clean physical-break arena");
        var expected=Block.getDrops(actual.getBlockState(),h.getLevel(),actual.getBlockPos(),actual);
        h.assertTrue(actualPlayer.gameMode.destroyBlock(actual.getBlockPos()),"actual native GameMode physical break succeeded");
        h.assertTrue(h.getLevel().getBlockEntity(actual.getBlockPos())!=actual&&actual.isRemoved(),"actual old BE removed from physical world");
        h.assertTrue(access.cooking$isPreparedStockpotSealed(),"old BE ownership survives removal for native LootParams query");
        var delivered=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).stream()
            .map(net.minecraft.world.entity.item.ItemEntity::getItem).toList();
        h.assertTrue(settledCounts(expected,h).equals(settledCounts(delivered,h)),"exact actual native pot/lid/paid payload once; Root age API settles both sides, no new clock");
    }
    private static java.util.Map<String,Integer> settledCounts(List<ItemStack> stacks,GameTestHelper h) {
        java.util.Map<String,Integer> counts=new java.util.TreeMap<>();
        for(ItemStack stack:stacks) {
            ItemStack settled=com.bettercontent.betterindustrialheat.api.CookingAgeApi.transfer(
                java.util.List.of(new com.bettercontent.betterindustrialheat.api.CookingAgeApi.Input(stack,
                    com.bettercontent.betterindustrialheat.food.FoodThermalService.Storage.AMBIENT)),stack.copy(),h.getLevel().getGameTime());
            counts.merge(settled.copyWithCount(1).save(new CompoundTag()).toString(),settled.getCount(),Integer::sum);
        }
        return counts;
    }
    public static void unpaidCarrierRefusesWithoutPayment(GameTestHelper h,StockpotBlockEntity actual,
            net.minecraft.server.level.ServerPlayer actualPlayer) {
        h.assertTrue(!(actualPlayer instanceof net.minecraftforge.common.util.FakePlayer)&&!actualPlayer.isCreative()
            &&actualPlayer.serverLevel()==h.getLevel()
            &&h.getLevel().getServer().getPlayerList().getPlayer(actualPlayer.getUUID())==actualPlayer,
            "actual connected survival player, not a fake work actor");
        var tool=actualPlayer.getMainHandItem();
        h.assertTrue(tool.is(net.minecraft.world.item.Items.IRON_PICKAXE)&&tool.getDamageValue()<tool.getMaxDamage()-1,
            "actual ordinary damageable pickaxe makes no-tool-debit refusal observable");
        var access=(PreparedStockpotStateAccess)(Object)actual;
        var cause=com.bettercontent.bettercooking.stockpot.PreparedStockpotBreakPolicy.evaluate(access);
        h.assertTrue(!cause.allowed()&&cause.unpaidCarrier(),"refusal specifically unpaid carriers, not invalid source/phase");
        h.assertTrue(actual.getStatus()==IStockpot.FINISHED,"actual materialized native finished food");
        var before=actual.saveWithFullMetadata();var state=actual.getBlockState();
        var inventory=actualPlayer.getInventory().save(new net.minecraft.nbt.ListTag());
        h.assertTrue(Block.getDrops(state,h.getLevel(),actual.getBlockPos(),actual).isEmpty(),"blocked pure preview emits no food/pot/lid");
        h.assertTrue(!actualPlayer.gameMode.destroyBlock(actual.getBlockPos()),"actual GameMode refuses before tool debit/destruction");
        h.assertTrue(h.getLevel().getBlockEntity(actual.getBlockPos())==actual&&!actual.isRemoved()
            &&state.equals(h.getLevel().getBlockState(actual.getBlockPos())),"actual pot/BE remain installed unchanged");
        h.assertTrue(before.equals(actual.saveWithFullMetadata()),"paid native input/output/remaining count state unchanged");
        h.assertTrue(inventory.equals(actualPlayer.getInventory().save(new net.minecraft.nbt.ListTag())),"all actual inventory/tool counts and NBT unchanged");
        h.assertTrue(Block.getDrops(state,h.getLevel(),actual.getBlockPos(),actual).isEmpty(),"second preview still emits no pot/lid duplicate");
    }
    public static void unpaidCarrierGenericDestroyRefuses(GameTestHelper h,StockpotBlockEntity actual) {
        assertUnpaid(h,actual);var before=actual.saveWithFullMetadata();assertNoPayloadEntities(h,actual);
        h.assertTrue(!h.getLevel().destroyBlock(actual.getBlockPos(),true),"native actorless destroy refuses unpaid carrier pot");
        assertRetained(h,actual,before);assertNoPayloadEntities(h,actual);
    }
    public static void unpaidCarrierExplosionRetainsWithoutDuplicateLoot(GameTestHelper h,StockpotBlockEntity actual) {
        assertUnpaid(h,actual);var before=actual.saveWithFullMetadata();assertNoPayloadEntities(h,actual);var pos=actual.getBlockPos();
        var explosion=h.getLevel().explode(null,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,2.0F,net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);
        h.assertTrue(explosion.getToBlow().contains(pos),"actual native explosion attempted this pot; resistance/RNG did not mask refusal");
        assertRetained(h,actual,before);assertNoPayloadEntities(h,actual);
    }
    public static void unpaidCarrierSupportLossRetainsWithoutDuplicateLoot(GameTestHelper h,StockpotBlockEntity actual) {
        assertUnpaid(h,actual);var before=actual.saveWithFullMetadata();assertNoPayloadEntities(h,actual);
        h.assertTrue(!h.getLevel().getBlockState(actual.getBlockPos().below()).isAir(),"actual support exists before loss");
        h.assertTrue(h.getLevel().setBlock(actual.getBlockPos().below(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3),"actual support removed through native update path");
        assertRetained(h,actual,before);assertNoPayloadEntities(h,actual);
    }
    private static void assertUnpaid(GameTestHelper h,StockpotBlockEntity actual) {
        var cause=com.bettercontent.bettercooking.stockpot.PreparedStockpotBreakPolicy.evaluate((PreparedStockpotStateAccess)(Object)actual);
        h.assertTrue(actual.getStatus()==IStockpot.FINISHED&&!cause.allowed()&&cause.unpaidCarrier(),"actual finished unpaid-carrier cause, not corruption");
    }
    private static void assertRetained(GameTestHelper h,StockpotBlockEntity actual,CompoundTag before) {
        h.assertTrue(h.getLevel().getBlockEntity(actual.getBlockPos())==actual&&!actual.isRemoved(),"physical same old pot remains installed");
        h.assertTrue(before.equals(actual.saveWithFullMetadata()),"native outputs/remaining quantity/seal unchanged");
    }
    private static void assertNoPayloadEntities(GameTestHelper h,StockpotBlockEntity actual) {
        var nearby=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(actual.getBlockPos()).inflate(5),e->{var s=e.getItem();return
                s.is(actual.getBlockState().getBlock().asItem())||s.is(ModItems.STOCKPOT_LID.get())||s.getItem()==actual.getResult().getItem();});
        h.assertTrue(nearby.isEmpty(),"exclusive test arena has no spawned pot/lid/food duplicate while physical pot retained");
    }
    private static void unfinished(GameTestHelper h,StockpotBlockEntity actual) {
        List<ItemStack> physical=actual.getInputs().stream().filter(s->!s.isEmpty()).toList();
        h.assertTrue(physical.size()==1&&physical.get(0).is(CookingItems.PREPARED_BATCH.get())&&physical.get(0).getCount()==1,
            "single actual physical committed batch, not its raw ingredients/witness");
        List<ItemStack> payload=pureNativeDrops(h,actual);
        h.assertTrue(payload.size()==1&&same(payload.get(0),physical.get(0)),"one exact actual batch refund, no raw ingredient double refund");
    }
    private static void finished(GameTestHelper h,StockpotBlockEntity actual) {
        List<ItemStack> payload=pureNativeDrops(h,actual);int count=0;
        for(ItemStack stack:payload) {
            h.assertTrue(!stack.is(CookingItems.PREPARED_BATCH.get())&&ItemStack.isSameItemSameTags(stack,actual.getResult()),
                "only actual materialized native result/full provenance, no batch/witness or served prefix refund");
            count+=stack.getCount();
        }
        h.assertTrue(count==actual.getTakeoutCount(),"exact actual remaining output quantity");
    }
    private static List<ItemStack> pureNativeDrops(GameTestHelper h,StockpotBlockEntity actual) {
        h.assertTrue(((PreparedStockpotStateAccess)(Object)actual).cooking$isPreparedStockpotSealed(),"actual owned backend");
        CompoundTag before=actual.saveWithFullMetadata();
        List<ItemStack> a=Block.getDrops(actual.getBlockState(),h.getLevel(),actual.getBlockPos(),actual);
        List<ItemStack> b=Block.getDrops(actual.getBlockState(),h.getLevel(),actual.getBlockPos(),actual);
        h.assertTrue(before.equals(actual.saveWithFullMetadata()),"two native drop previews do not alter phase/inputs/output/count/seal");
        h.assertTrue(encoded(a).equals(encoded(b)),"repeated previews identical, no consuming cursor/one-shot refund");
        return a.stream().filter(s->!s.is(actual.getBlockState().getBlock().asItem())&&!s.is(ModItems.STOCKPOT_LID.get())).toList();
    }
    private static List<String> encoded(List<ItemStack> stacks) {
        return stacks.stream().map(s->s.save(new CompoundTag()).toString()).sorted().toList();
    }
    private static boolean same(ItemStack a,ItemStack b) {return a.getCount()==b.getCount()&&ItemStack.isSameItemSameTags(a,b);}
}
