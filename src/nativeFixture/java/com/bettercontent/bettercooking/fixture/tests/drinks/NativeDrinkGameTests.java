package com.bettercontent.bettercooking.fixture.tests.drinks;

import com.bettercontent.bettercooking.drinks.NativeDrinkRoutes;
import com.bettercontent.bettercooking.drinks.NativeDrinkProvenance;
import com.bettercontent.bettercooking.drinks.NativeDrinkIngredients;
import com.bettercontent.bettercooking.drinks.NativeDrinkSafety;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Native declarations ONLY until executed. These getters do NOT qualify connected consumption. */
@GameTestHolder("better_cooking_drinks")
@PrefixGameTestTemplate(false)
public final class NativeDrinkGameTests {
    private NativeDrinkGameTests() {}
    @GameTest(templateNamespace="better_cooking",template="native_discovery",timeoutTicks=20)
    public static void exactNineNativeWholeDrinkGetters(GameTestHelper helper){
        for(String name:NativeDrinkRoutes.IDS){
            ResourceLocation id=new ResourceLocation(name);helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id),"Missing "+name);
            var item=ForgeRegistries.ITEMS.getValue(id);var stack=item.getDefaultInstance();
            helper.assertTrue(!item.isEdible(),"Native cup became edible: "+name);
            helper.assertTrue(item.getFoodProperties(stack,null)==null,"Null native getter fabricated as food: "+name);
            helper.assertTrue(stack.getUseAnimation()==UseAnim.DRINK&&stack.getUseDuration()==32,"Native DRINK32 drift: "+name);
        }helper.succeed();
    }
    @GameTest(templateNamespace="better_cooking",template="native_discovery",timeoutTicks=20)
    public static void legacyMilkTeaCannotInventYield(GameTestHelper helper){
        var stack=ForgeRegistries.ITEMS.getValue(new ResourceLocation(NativeDrinkRoutes.MILK_TEA)).getDefaultInstance();
        helper.assertTrue(NativeDrinkProvenance.hydration(stack).isEmpty(),"Legacy milk tea invented fluid allocation");
        helper.assertTrue(NativeDrinkProvenance.resolve(stack).isEmpty(),"Legacy milk tea invented nutrient facts");helper.succeed();
    }
    @GameTest(templateNamespace="better_cooking",template="native_discovery",timeoutTicks=20)
    public static void unsafeWaterTeapotRejectsBeforeDebit(GameTestHelper helper){
        var pos=new net.minecraft.core.BlockPos(0,1,0);
        helper.setBlock(pos,com.github.ysbbbbbb.kaleidoscopecookery.init.ModBlocks.TEAPOT.get().defaultBlockState());
        var owner=(com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        var actual=dev.ghen.thirst.content.purity.WaterPurity.addPurity(Items.WATER_BUCKET.getDefaultInstance(),0);
        var itemBefore=actual.save(new net.minecraft.nbt.CompoundTag());var before=owner.saveWithFullMetadata();
        helper.assertTrue(owner.canReceiveDripstoneFluid(),"Teapot not otherwise eligible for native fill");
        helper.assertTrue(actual.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM)
                .map(handler->handler.getTanks()>0&&handler.getFluidInTank(0).getAmount()>=1000
                        &&handler.getFluidInTank(0).getFluid().isSame(net.minecraft.world.level.material.Fluids.WATER)).orElse(false),"Not an actual native1000mB water source");
        // Null is deliberately an absent actor, NOT a fabricated player. The guard must reject
        // BEFORE the native method can need an actor, debit an item or return a container.
        helper.assertTrue(!owner.addTeaFluid(helper.getLevel(),null,actual),"Unsafe water filled teapot");
        helper.assertTrue(before.equals(owner.saveWithFullMetadata()),"Rejected teapot owner changed");
        helper.assertTrue(itemBefore.equals(actual.save(new net.minecraft.nbt.CompoundTag())),"Rejected source/container changed");helper.succeed();
    }
    @GameTest(templateNamespace="better_cooking",template="native_discovery",timeoutTicks=20)
    public static void unsafeWaterStockpotRejectsBeforeDebit(GameTestHelper helper){
        var pos=new net.minecraft.core.BlockPos(0,1,0);
        helper.setBlock(pos,com.github.ysbbbbbb.kaleidoscopecookery.init.ModBlocks.STOCKPOT.get().defaultBlockState());
        var owner=(com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        var actual=dev.ghen.thirst.content.purity.WaterPurity.addPurity(Items.WATER_BUCKET.getDefaultInstance(),0);
        var itemBefore=actual.save(new net.minecraft.nbt.CompoundTag());var before=owner.saveWithFullMetadata();
        helper.assertTrue(!owner.hasLid()&&owner.getStatus()==com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot.PUT_SOUP_BASE,"Stockpot not otherwise eligible for native fill");
        helper.assertTrue(owner.getSoupBase()!=null&&owner.getSoupBase().isSoupBase(actual),"Actual native soup matcher does not accept this water source");
        helper.assertTrue(!owner.addSoupBase(helper.getLevel(),null,actual),"Unsafe water filled stockpot");
        helper.assertTrue(before.equals(owner.saveWithFullMetadata()),"Rejected stockpot owner changed");
        helper.assertTrue(itemBefore.equals(actual.save(new net.minecraft.nbt.CompoundTag())),"Rejected source/container changed");helper.succeed();
    }
    @GameTest(templateNamespace="better_cooking",template="native_discovery",timeoutTicks=20)
    public static void unknownDripWaterRejectsBeforeCommit(GameTestHelper helper){
        var pos=new net.minecraft.core.BlockPos(0,1,0);
        helper.setBlock(pos,com.github.ysbbbbbb.kaleidoscopecookery.init.ModBlocks.TEAPOT.get().defaultBlockState());
        var owner=(com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        var before=owner.saveWithFullMetadata();
        helper.assertTrue(owner.canReceiveDripstoneFluid(),"Drip fixture not otherwise eligible for native fill");
        helper.assertTrue(!owner.receiveDripstoneFluid(net.minecraft.world.level.material.Fluids.WATER),"Unclassified water drip committed");
        helper.assertTrue(before.equals(owner.saveWithFullMetadata()),"Denied drip mutated owner");helper.succeed();
    }
    @GameTest(templateNamespace="better_cooking",template="native_discovery",timeoutTicks=20)
    public static void safeItemAndNonWaterClassificationDoNotRequireActor(GameTestHelper helper){
        var safe=dev.ghen.thirst.content.purity.WaterPurity.addPurity(Items.WATER_BUCKET.getDefaultInstance(),3);
        var before=safe.save(new net.minecraft.nbt.CompoundTag());
        helper.assertTrue(NativeDrinkSafety.allowsSoupItem(safe),"Actual purity3 source blocked");
        helper.assertTrue(before.equals(safe.save(new net.minecraft.nbt.CompoundTag())),"Purity query mutated actual source");
        helper.assertTrue(NativeDrinkSafety.allowsSoupItem(Items.MILK_BUCKET.getDefaultInstance()),"Milk pretended to be water");
        helper.assertTrue(NativeDrinkSafety.allowsDripstone(net.minecraft.world.level.material.Fluids.LAVA),"Non-water native drip changed");helper.succeed();
    }
    @GameTest(templateNamespace="better_cooking",template="native_discovery",timeoutTicks=20)
    public static void corruptReceiptCannotLookLikeUntaggedLegacy(GameTestHelper helper){
        ItemStack stack=ForgeRegistries.ITEMS.getValue(new ResourceLocation(NativeDrinkRoutes.MILK_TEA)).getDefaultInstance();
        stack.getOrCreateTag().putString(NativeDrinkProvenance.KEY,"deliberately_corrupt_negative_fixture");
        helper.assertTrue(NativeDrinkProvenance.hasReceipt(stack),"Corrupt NEW receipt misclassified as old stock");
        boolean rejected=false;try{NativeDrinkProvenance.resolve(stack);}catch(IllegalArgumentException expected){rejected=true;}
        helper.assertTrue(rejected,"Corrupt new receipt silently converted to legacy fallback");helper.succeed();
    }
    @GameTest(templateNamespace="better_cooking",template="native_discovery",timeoutTicks=20)
    public static void unknownIntakeCannotBecomeZeroFacts(GameTestHelper helper){
        net.minecraft.nbt.CompoundTag malformed=new net.minecraft.nbt.CompoundTag();malformed.putString("nutrients","unavailable_negative_fixture");
        boolean rejected=false;try{NativeDrinkIngredients.validateIntake(malformed);}catch(IllegalArgumentException expected){rejected=true;}
        helper.assertTrue(rejected,"Unknown ingredient intake became an empty/zero entitlement");helper.succeed();
    }
    @GameTest(templateNamespace="better_cooking",template="native_discovery",timeoutTicks=20)
    public static void existingMilkAndPotionAreNotNewDrinkIds(GameTestHelper helper){
        for(var item:new net.minecraft.world.item.Item[]{Items.MILK_BUCKET,Items.POTION,Items.SPLASH_POTION,Items.LINGERING_POTION}){
            ItemStack stack=new ItemStack(item);helper.assertTrue(!NativeDrinkProvenance.isNativeDrink(stack),"New adapter seized existing Water source");
            helper.assertTrue(NativeDrinkProvenance.resolve(stack).isEmpty(),"New adapter resolved unrelated source");
        }helper.succeed();
    }
}
