package com.bettercontent.bettercooking.fixture.tests.cutting;

import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.cutting.CuttingBoardAccess;
import com.bettercontent.bettercooking.cutting.NativeCuttingSelection;
import com.bettercontent.bettercooking.kitchen.KitchenMenu;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModBlocks;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** AUTHORED/COMPILED ONLY. Full actual recipe graph, genuine connected menu, no RecipeManager replacement. */
@GameTestHolder("better_cooking_cutting_selection")
@PrefixGameTestTemplate(false)
public final class NativeCuttingSelectionGameTests {
    @GameTest(templateNamespace="better_cooking",template="native_discovery",batch="cut_select_fd_beef",timeoutTicks=100)
    public static void fullgraph_fd_mince_coexists_with_native_offal(GameTestHelper h) { run(h,"minecraft:beef","farmersdelight:cutting/beef",false); }
    @GameTest(templateNamespace="better_cooking",template="native_discovery",batch="cut_select_kc_beef",timeoutTicks=100)
    public static void fullgraph_native_offal_is_not_shadowed_by_mince(GameTestHelper h) { run(h,"minecraft:beef","kaleidoscope_cookery:chopping_board/raw_cow_offal",false); }
    @GameTest(templateNamespace="better_cooking",template="native_discovery",batch="cut_select_fd_dough",timeoutTicks=100)
    public static void fullgraph_fd_dough_exact_recipe_and_output(GameTestHelper h) { run(h,"farmersdelight:wheat_dough","farmersdelight:cutting/tag_dough",false); }
    @GameTest(templateNamespace="better_cooking",template="native_discovery",batch="cut_select_kc_dough",timeoutTicks=100)
    public static void fullgraph_native_dough_exact_recipe_and_output(GameTestHelper h) { run(h,"farmersdelight:wheat_dough","kaleidoscope_cookery:chopping_board/raw_noodles_from_raw_dough",false); }
    @GameTest(templateNamespace="better_cooking",template="native_discovery",batch="cut_select_stale",timeoutTicks=100)
    public static void stale_persisted_choice_never_falls_back_before_debit(GameTestHelper h) { run(h,"minecraft:beef","farmersdelight:cutting/beef",true); }

    private static void run(GameTestHelper h,String inputId,String recipeId,boolean stale) {
        h.assertTrue(!CookingMod.discoveryOnly(),"Discovery is not selection acceptance");
        var level=h.getLevel();
        ServerPlayer player=level.getServer().getPlayerList().getPlayers().stream()
                .filter(p->!(p instanceof FakePlayer)&&p.connection!=null&&!p.hasDisconnected()&&p.serverLevel()==level)
                .findFirst().orElseThrow(()->new AssertionError("Genuine connected client required"));
        var manager=level.getRecipeManager(); var graph=java.util.Set.copyOf(manager.getRecipeIds().toList());
        var input=ForgeRegistries.ITEMS.getValue(new ResourceLocation(inputId));
        h.assertTrue(input!=null&&input!=Items.AIR,"Actual registered fixture input");
        var recipe=manager.byKey(new ResourceLocation(recipeId)).filter(ChoppingBoardRecipe.class::isInstance)
                .map(ChoppingBoardRecipe.class::cast).orElseThrow(()->new AssertionError("Actual migrated native recipe required: "+recipeId));
        h.assertTrue(NativeCuttingSelection.candidates(level,new ItemStack(input)).size()>1,"Full live graph must retain actual competing recipes, not isolate ours");
        BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
        level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos,ModBlocks.CHOPPING_BOARD.get().defaultBlockState());
        var board=(ChoppingBoardBlockEntity)level.getBlockEntity(pos);var access=(CuttingBoardAccess)board;
        ItemStack unpaid=new ItemStack(input,2);
        h.assertTrue(!board.onPutItem(level,null,unpaid)&&unpaid.getCount()==2,"Ambiguous unset board refuses before actual split, never arbitrary priority");
        player.closeContainer();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        player.teleportTo(level,pos.getX()+.5,pos.getY()+1,pos.getZ()+1.5,0,0);player.setShiftKeyDown(true);
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        player.gameMode.useItemOn(player,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit);
        player.setShiftKeyDown(false);
        h.assertTrue(player.containerMenu instanceof KitchenMenu,"Actual secondary-empty-hand native interaction must open real menu");
        KitchenMenu menu=(KitchenMenu)player.containerMenu;
        h.assertTrue(!menu.chooseNativeCut(player,recipe.getId(),menu.containerId+1),"Wrong window cannot select");
        h.assertTrue(!menu.chooseNativeCut(player,recipe.getId(),menu.containerId),"No real cursor ingredient cannot select");
        // Controlled physical fixture input, then normal authoritative inventory PICKUP (no client-provided stacks).
        h.assertTrue(player.getInventory().getItem(9).isEmpty(),"Fresh fixture reserves empty inventory9, never overwrites existing player stock");
        player.getInventory().setItem(9,unpaid);
        menu.clicked(10,0,ClickType.PICKUP,player);
        h.assertTrue(menu.getCarried().getCount()==2,"Actual inventory click carries original two units");
        h.assertTrue(menu.chooseNativeCut(player,recipe.getId(),menu.containerId),"Real menu recipe-sheet handler accepts exact matching ID");
        h.assertTrue(menu.getCarried().getCount()==2&&board.getCurrentCutStack().isEmpty()&&access.cooking$cuttingSession()==null,"Selection pays no input, creates no work/output/session");
        var saved=board.saveWithFullMetadata();board.load(saved);
        h.assertTrue(access.cooking$cuttingChoice().recipe().equals(recipe.getId()),"Actual board save/load preserves shared selected ID");
        menu.clicked(10,0,ClickType.PICKUP,player);player.closeContainer();
        h.assertTrue(!menu.chooseNativeCut(player,recipe.getId(),menu.containerId),"Closed/stale menu cannot select even with an old valid window ID");
        ItemStack physical=player.getInventory().getItem(9);
        h.assertTrue(physical.getCount()==2&&physical.is(input),"Cursor returned actual stock before physical native placement");
        if(stale) {
            var bad=board.saveWithFullMetadata();
            bad.getCompound(NativeCuttingSelection.KEY).putString("recipe","better_cooking:missing_cut_fixture");board.load(bad);
            h.assertTrue(!board.onPutItem(level,null,physical)&&physical.getCount()==2&&board.getCurrentCutStack().isEmpty(),"Missing persisted recipe ID rejects without fallback or debit");
        } else {
            if(recipeId.equals("farmersdelight:cutting/beef")) {
                // The ACTUAL vanilla dispatcher must honor the chosen own route despite native offal coexistence.
                BlockPos dispenser=pos.north();
                level.setBlockAndUpdate(dispenser,Blocks.DISPENSER.defaultBlockState().setValue(net.minecraft.world.level.block.DispenserBlock.FACING,Direction.SOUTH));
                var supply=(net.minecraft.world.Container)level.getBlockEntity(dispenser);
                supply.setItem(0,player.getInventory().removeItemNoUpdate(9));
                ((net.minecraft.world.level.block.DispenserBlock)Blocks.DISPENSER).tick(level.getBlockState(dispenser),level,dispenser,level.random);
                h.assertTrue(board.getCurrentCutStack().getCount()==1&&supply.getItem(0).getCount()==1,"Selected own recipe through actual vanilla dispenser pays only one, not native offal or double debit");
                player.getInventory().setItem(9,supply.removeItemNoUpdate(0));
            } else h.assertTrue(board.onPutItem(level,null,physical)&&physical.getCount()==1,"Selected native recipe actually debits one physical unit");
            var before=board.saveWithFullMetadata();
            boolean rejected=false;try{access.cooking$setCuttingChoice(new NativeCuttingSelection.Choice(recipe.getId(),new ResourceLocation(inputId)));}catch(IllegalStateException expected){rejected=true;}
            h.assertTrue(rejected&&before.equals(board.saveWithFullMetadata()),"Paid choice cannot change or mutate frozen native outputs");
            ItemStack knife=new ItemStack(ModItems.IRON_KITCHEN_KNIFE.get());
            for(int i=0;i<recipe.getCutCount();i++)h.assertTrue(board.onCutItem(level,null,knife),"Actual allowed native tool advances native count");
            h.assertTrue(board.onCutItem(level,null,knife),"Actual next native collection delivers chosen outputs");
            var entities=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1.4));
            var expectedOutputs=NativeCuttingSelection.preview(recipe);
            h.assertTrue(entities.stream().mapToInt(e->e.getItem().getCount()).sum()==expectedOutputs.stream().mapToInt(e->e.stack().getCount()).sum(),"No extra unchosen offal/mince/dough outputs may accompany the exact chosen recipe");
            for(var expected:expectedOutputs) {
                h.assertTrue(expected.chance()==1,"These exact source controls use guaranteed counts, not substituted RNG");
                int actual=entities.stream().filter(e->e.getItem().is(expected.stack().getItem())).mapToInt(e->e.getItem().getCount()).sum();
                h.assertTrue(actual==expected.stack().getCount(),"Exact chosen source output/count preserved: "+recipe.getId()+" / "+expected.stack());
            }
        }
        h.assertTrue(graph.equals(java.util.Set.copyOf(manager.getRecipeIds().toList())),"Full actual graph unchanged; no test-only recipe isolation/priority");
        h.succeed();
    }
}
