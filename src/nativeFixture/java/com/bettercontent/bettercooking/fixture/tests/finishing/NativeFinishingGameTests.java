package com.bettercontent.bettercooking.fixture.tests.finishing;

import com.bettercontent.bettercooking.*;
import com.bettercontent.bettercooking.recipe.*;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.*;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.container.*;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import com.github.ysbbbbbb.kaleidoscopecookery.item.quality.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

/** Authored final-commit frames, not full-duration cooking, normal player interaction or client acceptance. */
@GameTestHolder(CookingMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeFinishingGameTests {
    private static final BlockPos POS=new BlockPos(1,1,1);
    private static ItemStack pay(GameTestHelper h,String finish) {
        var recipe=h.getLevel().getRecipeManager().getAllRecipesFor(CookingRecipes.PREPARATION_TYPE.get()).stream()
            .filter(r->r.finish().equals(finish)).sorted(java.util.Comparator.comparing(r->r.getId().toString())).findFirst()
            .orElseThrow(()->new AssertionError("No actual preparation/native route for "+finish));
        SimpleContainer inventory=new SimpleContainer(9);
        for(int i=0;i<recipe.inputs().size();i++) {
            var input=recipe.inputs().get(i);ItemStack[] choices=input.ingredient().getItems();
            h.assertTrue(choices.length>0,"actual registered ingredient choice");
            ItemStack raw=choices[0].copyWithCount(input.count());QualityUtils.setQuality(raw,Quality.POOR);inventory.setItem(i,raw);
        }
        var paid=IngredientAccounting.debit(recipe,inventory).orElseThrow(()->new AssertionError("Real atomic ingredient debit failed"));
        h.assertTrue(inventory.isEmpty(),"all fixture inputs really debited once");
        ItemStack batch=CookingRuntime.finishPreparation(recipe,paid,h.getLevel());
        h.assertTrue(batch.is(CookingItems.PREPARED_BATCH.get()),"actual committed paid batch");return batch;
    }
    private static BlockEntity place(GameTestHelper h,String name) {
        h.setBlock(POS.below(),Blocks.CAMPFIRE.defaultBlockState());
        var block=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("kaleidoscope_cookery",name));
        h.assertTrue(block!=null,"pinned native block registered");var state=block.defaultBlockState();
        if(state.hasProperty(StockpotBlock.HAS_LID))state=state.setValue(StockpotBlock.HAS_LID,true);
        if(state.hasProperty(SteamerBlock.HAS_LID))state=state.setValue(SteamerBlock.HAS_LID,true);
        h.setBlock(POS,state);return h.getLevel().getBlockEntity(h.absolutePos(POS));
    }
    private static NonNullList<ItemStack> slots(int size,ItemStack first) {
        var slots=NonNullList.withSize(size,ItemStack.EMPTY);slots.set(0,first);return slots;
    }
    private static void nativeResult(CompoundTag state,ItemStack output,int status) {
        QualityUtils.setQuality(output,Quality.STANDARD);state.put("Result",output.save(new CompoundTag()));
        state.putInt("Status",status);state.putInt("CurrentTick",0);
    }
    private static ItemStack result(BlockEntity entity) {return ItemStack.of(entity.saveWithFullMetadata().getCompound("Result"));}
    private static void check(GameTestHelper h,ItemStack batch,ItemStack actual) {
        h.assertTrue(!actual.isEmpty(),"native commit did not destroy the output");
        if(CookingMod.discoveryOnly()) {
            h.assertTrue(!actual.hasTag()||!actual.getTag().contains(CookingRuntime.SERVING),"Discovery does not upgrade native output");
            return;
        }
        ItemStack expected=CookingRuntime.finishFromPrepared(batch,actual);
        h.assertTrue(!expected.isEmpty()&&ItemStack.isSameItemSameTags(expected,actual)&&expected.getCount()==actual.getCount(),
            "real final output equals sole carry authority: portions, actual paid vector, inherited quality and existing Heat age");
        h.assertTrue(QualityUtils.getQuality(actual)==Quality.POOR,"late carry defeats native Standard overwrite without rerolling");
    }
    private static void checkFailure(GameTestHelper h,ItemStack batch,ItemStack actual,int units) {
        h.assertTrue(!actual.isEmpty()&&actual.getCount()==units,"exact native failure units preserved");
        if(CookingMod.discoveryOnly()) {
            h.assertTrue(!actual.hasTag()||!actual.getTag().contains(CookingRuntime.SERVING),"Discovery failure has no culinary upgrade");return;
        }
        h.assertTrue(QualityUtils.getQuality(actual)==Quality.POOR,"native failure cannot launder inherited quality");
        CompoundTag source=CookingRuntime.preparedOutput(batch).getTag().getCompound(CookingRuntime.SERVING).getCompound("nutrients");
        CompoundTag target=actual.getTag().getCompound(CookingRuntime.SERVING).getCompound("nutrients");
        int originalPortions=batch.getTag().getCompound(CookingRuntime.PREP).getInt("servings");
        int finalPortions=units*CookingRuntime.profile(actual).servings();
        for(String key:source.getAllKeys())h.assertTrue(Math.abs(source.getDouble(key)*originalPortions-target.getDouble(key)*finalPortions)<1.0e-8,"paid vector conserved through actual failure: "+key);
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=80)
    public static void potActualFailedStirPreservesPaidFailure(GameTestHelper h) {
        ItemStack batch=pay(h,"pot");var entity=(PotBlockEntity)place(h,"pot");var state=entity.saveWithFullMetadata();
        ContainerHelper.saveAllItems(state,slots(9,batch));
        nativeResult(state,CookingRuntime.preparedOutput(batch),PotBlockEntity.COOKING);state.putInt("StirFryCount",1);
        entity.load(state);entity.tick(h.getLevel());checkFailure(h,batch,result(entity),1);h.succeed();
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=100)
    public static void teaActualRecipeMissSurvivesSaveLoad(GameTestHelper h) {
        ItemStack foreign=pay(h,"pot");var entity=(TeapotBlockEntity)place(h,"teapot");var state=entity.saveWithFullMetadata();
        state.put("Input",foreign.save(new CompoundTag()));state.putString("TeaFluidId","minecraft:water");
        state.putInt("Status",TeapotBlockEntity.PUT_INGREDIENT);state.putInt("CurrentTick",0);entity.load(state);
        boolean[] reloaded={false};
        h.succeedWhen(()-> {
            if(!reloaded[0]) {
                h.assertTrue(entity.getStatus()==TeapotBlockEntity.PROCESSING,"real native quick-check miss happened");
                CompoundTag saved=entity.saveWithFullMetadata();
                if(!CookingMod.discoveryOnly())h.assertTrue(saved.getBoolean("better_cooking:actual_teapot_recipe_miss"),"actual miss cause persisted");
                saved.putInt("CurrentTick",0);entity.load(saved);reloaded[0]=true;
            }
            h.assertTrue(entity.getStatus()==TeapotBlockEntity.FINISHED,"real final transition after reload");
            checkFailure(h,foreign,result(entity),4);
        });
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=80)
    public static void stockpotActualReservedFallbackPreservesPaidFailure(GameTestHelper h) {
        ItemStack foreign=pay(h,"teapot");var entity=(StockpotBlockEntity)place(h,"stockpot");var state=entity.saveWithFullMetadata();
        ContainerHelper.saveAllItems(state,slots(9,foreign));state.putInt("Status",StockpotBlockEntity.PUT_INGREDIENT);state.putInt("CurrentTick",0);
        entity.load(state);boolean[] reloaded={false};
        h.succeedWhen(()-> {
            if(!reloaded[0]) {
                h.assertTrue(entity.getStatus()==StockpotBlockEntity.COOKING,"real native unknown recipe branch happened");
                CompoundTag saved=entity.saveWithFullMetadata();
                h.assertTrue(saved.getString("RecipeId").equals(com.github.ysbbbbbb.kaleidoscopecookery.crafting.serializer.StockpotRecipeSerializer.EMPTY_ID.toString()),"actual reserved fallback selected");
                saved.putInt("CurrentTick",0);entity.load(saved);reloaded[0]=true;
            }
            h.assertTrue(entity.getStatus()==StockpotBlockEntity.FINISHED,"actual stockpot final transition");checkFailure(h,foreign,result(entity),1);
        });
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=80)
    public static void potFinalTickCarriesPaidBatch(GameTestHelper h) {
        ItemStack batch=pay(h,"pot");var entity=(PotBlockEntity)place(h,"pot");
        var state=entity.saveWithFullMetadata();ContainerHelper.saveAllItems(state,slots(9,batch));entity.load(state);
        var container=entity.getContainer();var nativeRecipe=h.getLevel().getRecipeManager().getRecipeFor(ModRecipes.POT_RECIPE,container,h.getLevel()).orElseThrow();
        nativeResult(state,nativeRecipe.assemble(container,h.getLevel().registryAccess()),PotBlockEntity.COOKING);
        state.putInt("StirFryCount",0);entity.load(state);entity.tick(h.getLevel());check(h,batch,result(entity));h.succeed();
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=80)
    public static void stockpotCarriesBeforeRealInputClear(GameTestHelper h) {
        ItemStack batch=pay(h,"stockpot");var entity=(StockpotBlockEntity)place(h,"stockpot");var inputs=slots(9,batch);
        var recipe=h.getLevel().getRecipeManager().getAllRecipesFor(ModRecipes.STOCKPOT_RECIPE).stream()
            .filter(r->r.matches(new StockpotContainer(inputs,r.soupBase()),h.getLevel())).findFirst().orElseThrow();
        var state=entity.saveWithFullMetadata();ContainerHelper.saveAllItems(state,inputs);
        state.putString("RecipeId",recipe.getId().toString());state.putString("SoupBaseId",recipe.soupBase().toString());
        ItemStack output=recipe.assemble(new StockpotContainer(inputs,recipe.soupBase()),h.getLevel().registryAccess());
        nativeResult(state,output,StockpotBlockEntity.COOKING);state.putInt("TakeoutCount",Math.min(9,output.getCount()));
        entity.load(state);entity.tick(h.getLevel());check(h,batch,result(entity));
        h.assertTrue(entity.getContainer().isEmpty(),"actual native debit/clear still occurs");h.succeed();
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=80)
    public static void steamerUsesOnlyItsSpecificPaidSlotAndKeepsFallback(GameTestHelper h) {
        ItemStack batch=pay(h,"steamer"),foreign=pay(h,"pot");var entity=(SteamerBlockEntity)place(h,"steamer");
        var items=slots(8,batch);items.set(1,new ItemStack(Items.COBBLESTONE));items.set(2,foreign.copy());
        var state=entity.saveWithFullMetadata();ContainerHelper.saveAllItems(state,items);
        state.putIntArray(SteamerBlockEntity.COOKING_PROGRESS_TAG,new int[8]);state.putIntArray(SteamerBlockEntity.COOKING_TIME_TAG,new int[8]);
        entity.load(state);entity.updateLitLevel(h.getLevel());entity.tick(h.getLevel());
        var after=slots(8,ItemStack.EMPTY);ContainerHelper.loadAllItems(entity.saveWithFullMetadata(),after);check(h,batch,after.get(0));
        h.assertTrue(after.get(1).is(Items.COBBLESTONE),"ordinary missing recipe remains native identity");
        h.assertTrue(ItemStack.isSameItemSameTags(after.get(2),foreign),"other slot missing recipe retains its exact paid batch");h.succeed();
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=80)
    public static void teapotFinalTransitionRetainsPaidPortions(GameTestHelper h) {
        ItemStack batch=pay(h,"teapot");var entity=(TeapotBlockEntity)place(h,"teapot");
        var recipe=h.getLevel().getRecipeManager().getAllRecipesFor(ModRecipes.TEAPOT_RECIPE).stream()
            .filter(r->r.ingredient().test(batch)).findFirst().orElseThrow();
        var state=entity.saveWithFullMetadata();state.put("Input",batch.save(new CompoundTag()));state.putString("TeaFluidId",recipe.teaFluid().toString());
        nativeResult(state,recipe.assemble(new TeapotContainer(batch,recipe.teaFluid()),h.getLevel().registryAccess()),TeapotBlockEntity.PROCESSING);
        entity.load(state);
        h.succeedWhen(()-> {h.assertTrue(entity.getStatus()==TeapotBlockEntity.FINISHED,"actual native eligible tick reached");check(h,batch,result(entity));});
    }
}
