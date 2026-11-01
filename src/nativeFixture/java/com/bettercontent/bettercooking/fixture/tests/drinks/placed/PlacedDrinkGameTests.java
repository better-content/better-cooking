package com.bettercontent.bettercooking.fixture.tests.drinks.placed;
import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.drinks.placed.CupQueue;
import com.bettercontent.bettercooking.drinks.placed.EncodedCup;
import com.bettercontent.bettercooking.drinks.placed.CupCodec;
import com.bettercontent.bettercooking.drinks.placed.NativeDrinkBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.block.drink.*;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModBlocks;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems;
import com.github.ysbbbbbb.kaleidoscopecookery.item.TeapotItem;
import com.github.ysbbbbbb.kaleidoscopecookery.item.quality.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

/** Authored real-world/native call controls; disconnected actual ServerPlayer, never connected-client acceptance. */
@GameTestHolder(CookingMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlacedDrinkGameTests {
    private static final BlockPos POS=new BlockPos(1,1,1);
    private static ItemStack original(String item,String marker){
        ItemStack result=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("kaleidoscope_cookery",item)));
        result.getOrCreateTag().putString("placed_fixture_original",marker);
        var nested=new CompoundTag();nested.putLong("opaque_original",marker.hashCode());result.getOrCreateTag().put("placed_fixture_nested",nested);
        QualityUtils.setQuality(result,marker.equals("first")?Quality.POOR:Quality.EXCELLENT);return result;
    }
    private static ServerPlayer player(GameTestHelper h){
        var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"cup_fifo_test"));
        BlockPos pos=h.absolutePos(POS);player.setPos(pos.getX()+0.5,pos.getY()+1,pos.getZ()+2.5);return player;
    }
    private static NativeDrinkBlockEntity body(GameTestHelper h){
        var entity=h.getLevel().getBlockEntity(h.absolutePos(POS));h.assertTrue(entity instanceof NativeDrinkBlockEntity,"actual registered lazy native cup BE");return (NativeDrinkBlockEntity)entity;
    }
    private static void gone(GameTestHelper h){h.assertTrue(!(h.getLevel().getBlockEntity(h.absolutePos(POS)) instanceof NativeDrinkBlockEntity),"removed/empty native block has no active cup BE");}
    private static BlockHitResult hit(GameTestHelper h){BlockPos pos=h.absolutePos(POS);return new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);}
    private static void place(GameTestHelper h,ServerPlayer player,ItemStack actual){
        h.setBlock(POS.below(),Blocks.STONE);player.setItemInHand(InteractionHand.MAIN_HAND,actual.copy());
        BlockPos base=h.absolutePos(POS.below());var target=new BlockHitResult(Vec3.atCenterOf(base),Direction.UP,base,false);
        var context=new BlockPlaceContext(h.getLevel(),player,InteractionHand.MAIN_HAND,player.getMainHandItem(),target);
        h.assertTrue(((BlockItem)actual.getItem()).place(context).consumesAction(),"actual native BlockItem placement committed");body(h);
    }
    private static ItemStack pick(GameTestHelper h,ServerPlayer player){
        player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        h.getBlockState(POS).use(h.getLevel(),player,InteractionHand.MAIN_HAND,hit(h));
        ItemStack result=player.getInventory().removeItem(0,1);h.assertTrue(!result.isEmpty(),"actual native pickup delivered one stack");return result;
    }
    private static void compare(GameTestHelper h,ItemStack original,ItemStack actual){
        h.assertTrue(actual.getCount()==1&&actual.getItem()==original.getItem(),"exact one original item");
        if(CookingMod.discoveryOnly())h.assertTrue(!actual.hasTag()||!actual.getTag().contains("placed_fixture_original"),"Discovery leaves native default pickup unchanged");
        else h.assertTrue(ItemStack.isSameItemSameTags(original,actual),"whole original NBT/caps/quality survive, not fresh prototype");
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=60)
    public static void actualPlacementHeterogeneousMergeAndTwoPickups(GameTestHelper h){
        var player=player(h);ItemStack a=original("barley_tea","first"),b=original("barley_tea","second");place(h,player,a);
        player.setItemInHand(InteractionHand.MAIN_HAND,b.copy());h.getBlockState(POS).use(h.getLevel(),player,InteractionHand.MAIN_HAND,hit(h));
        compare(h,a,pick(h,player));compare(h,b,pick(h,player));gone(h);h.succeed();
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=60)
    public static void actualEmptyCupPourPreservesLastTeapotOriginalAndEmptyCups(GameTestHelper h){
        var player=player(h);ItemStack a=original("barley_tea","first");
        h.setBlock(POS.below(),Blocks.STONE);h.setBlock(POS,ModBlocks.EMPTY_CUP.get().defaultBlockState().setValue(EmptyCupBlock.CUP_COUNT,3));
        ItemStack teapot=new ItemStack(ModItems.TEAPOT.get());var data=new CompoundTag();data.putInt(TeapotBlockEntity.STATUS,TeapotBlockEntity.FINISHED);data.put(TeapotBlockEntity.RESULT,a.save(new CompoundTag()));
        BlockItem.setBlockEntityData(teapot,ModBlocks.TEAPOT_BE.get(),data);player.setItemInHand(InteractionHand.MAIN_HAND,teapot);
        h.getBlockState(POS).use(h.getLevel(),player,InteractionHand.MAIN_HAND,hit(h));
        h.assertTrue(TeapotItem.getPourOut(teapot).isEmpty(),"native last pour really cleared result NBT");compare(h,a,pick(h,player));
        h.assertTrue(h.getBlockState(POS).getBlock()==ModBlocks.EMPTY_CUP.get()&&h.getBlockState(POS).getValue(EmptyCupBlock.CUP_COUNT)==2,"only filled cup taken; two real empties remain");gone(h);h.succeed();
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=60)
    public static void actualMilkPlacementAndPickupKeepOneOriginal(GameTestHelper h){
        var player=player(h);ItemStack milk=original("clay_pot_milk_tea","first");place(h,player,milk);compare(h,milk,pick(h,player));gone(h);h.succeed();
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=60)
    public static void capturedOldBePreservesLootAfterRemovalWithoutAnyDestroyScope(GameTestHelper h){
        ItemStack a=original("barley_tea","first"),b=original("barley_tea","second");
        var cups=(TeacupBlock)((BlockItem)a.getItem()).getBlock();var state=cups.defaultBlockState().setValue(cups.getCupCountProperty(),4).setValue(cups.getTeaCountProperty(),2);
        h.setBlock(POS.below(),Blocks.STONE);h.setBlock(POS,state);BlockPos pos=h.absolutePos(POS);var old=body(h);
        var queue=CupQueue.<EncodedCup>empty().append(new EncodedCup(a)).append(new EncodedCup(b));old.commit(state,queue);
        var params=new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(pos)).withParameter(LootContextParams.BLOCK_ENTITY,old);
        var preview=state.getDrops(params);h.assertTrue(old.snapshot(state).size()==2,"preview did not advance FIFO");
        h.getLevel().removeBlock(pos,false);gone(h);var output=state.getDrops(params);
        var filled=output.stream().filter(item->item.is(a.getItem())).toList();
        h.assertTrue(filled.stream().mapToInt(ItemStack::getCount).sum()==2,"exact two filled drops");
        h.assertTrue(output.stream().filter(item->item.is(ModItems.EMPTY_CUP.get())).mapToInt(ItemStack::getCount).sum()==2,"two native empty cups preserved");
        if(!CookingMod.discoveryOnly())h.assertTrue(filled.size()==2&&ItemStack.isSameItemSameTags(a,filled.get(0))&&ItemStack.isSameItemSameTags(b,filled.get(1)),"captured detached old BE supplies two independent originals");
        h.assertTrue(preview.stream().mapToInt(ItemStack::getCount).sum()==output.stream().mapToInt(ItemStack::getCount).sum(),"preview/removal native quantities agree");h.succeed();
    }
    private static void reject(GameTestHelper h,CompoundTag corrupt,net.minecraft.world.level.block.state.BlockState state){
        var body=new NativeDrinkBlockEntity(h.absolutePos(POS),state);var saved=new CompoundTag();saved.put(NativeDrinkBlockEntity.KEY,corrupt);
        body.load(saved);boolean refused=false;try{body.snapshot(state);}catch(IllegalStateException expected){refused=true;}
        h.assertTrue(refused,"BE.load poisons corruption without a swallowed throw/fresh legacy replacement");
        var persisted=body.saveWithFullMetadata();h.assertTrue(persisted.getCompound(NativeDrinkBlockEntity.KEY).getBoolean("poison"),"poison saves explicit sentinel, never throws and loses whole BE tag");
        var again=new NativeDrinkBlockEntity(h.absolutePos(POS),state);again.load(persisted);refused=false;try{again.snapshot(state);}catch(IllegalStateException expected){refused=true;}h.assertTrue(refused,"saved poison stays poison after reload");
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=60)
    public static void corruptOriginalNeverBecomesUnknownLegacy(GameTestHelper h){
        ItemStack a=original("barley_tea","first");var state=((BlockItem)a.getItem()).getBlock().defaultBlockState();
        CompoundTag valid=CupCodec.save("kaleidoscope_cookery:barley_tea",CupQueue.<EncodedCup>empty().append(new EncodedCup(a)));
        CompoundTag missing=valid.copy();missing.getList("queue",10).getCompound(0).remove("stack");reject(h,missing,state);
        CompoundTag count=valid.copy();count.getList("queue",10).getCompound(0).getCompound("stack").putByte("Count",(byte)2);reject(h,count,state);
        CompoundTag counterfeit=valid.copy();counterfeit.getList("queue",10).getCompound(0).putBoolean("unknown",true);reject(h,counterfeit,state);
        CompoundTag missingPayload=valid.copy();missingPayload.getList("queue",10).getCompound(0).getCompound("stack").remove("tag");reject(h,missingPayload,state);
        CompoundTag version=valid.copy();version.putInt("v",2);reject(h,version,state);
        CompoundTag wrongType=valid.copy();var list=new net.minecraft.nbt.ListTag();list.add(net.minecraft.nbt.StringTag.valueOf("bad row"));wrongType.put("queue",list);reject(h,wrongType,state);h.succeed();
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=60)
    public static void oldUnknownCupDoesNotStealNewManagedOriginal(GameTestHelper h){
        var player=player(h);ItemStack b=original("barley_tea","second");h.setBlock(POS.below(),Blocks.STONE);h.setBlock(POS,((BlockItem)b.getItem()).getBlock().defaultBlockState());
        h.getLevel().getChunkAt(h.absolutePos(POS)).removeBlockEntity(h.absolutePos(POS));
        h.assertTrue(body(h).snapshot(h.getBlockState(POS)).head().isEmpty(),"actual missing-tag lazy legacy factory starts UNKNOWN");
        player.setItemInHand(InteractionHand.MAIN_HAND,b.copy());h.getBlockState(POS).use(h.getLevel(),player,InteractionHand.MAIN_HAND,hit(h));
        ItemStack legacy=pick(h,player);h.assertTrue(!legacy.hasTag()||!legacy.getTag().contains("placed_fixture_original"),"UNKNOWN receives native default, never the new original");
        compare(h,b,pick(h,player));gone(h);h.succeed();
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=60)
    public static void wrongSavedBeTypeCannotBecomeFreshLegacy(GameTestHelper h){
        ItemStack a=original("barley_tea","first");var state=((BlockItem)a.getItem()).getBlock().defaultBlockState();h.setBlock(POS,state);
        BlockPos pos=h.absolutePos(POS);var chunk=h.getLevel().getChunkAt(pos);chunk.removeBlockEntity(pos);
        var saved=new CompoundTag();saved.putString("id","minecraft:chest");saved.putInt("x",pos.getX());saved.putInt("y",pos.getY());saved.putInt("z",pos.getZ());
        saved.put(NativeDrinkBlockEntity.KEY,CupCodec.save("kaleidoscope_cookery:barley_tea",CupQueue.<EncodedCup>empty().append(new EncodedCup(a))));chunk.setBlockEntityNbt(saved);
        var bad=body(h);boolean refused=false;try{bad.snapshot(state);}catch(IllegalStateException expected){refused=true;}h.assertTrue(refused,"actual pending-tag promotion preserves wrong-type poison rather than new UNKNOWN");
        h.assertTrue(bad.saveWithFullMetadata().getCompound(NativeDrinkBlockEntity.KEY).getBoolean("poison"),"wrong type remains explicit poison on save");h.succeed();
    }
    @GameTest(template="forge:empty3x3x3",timeoutTicks=60)
    public static void realStackCodecAndBeRoundTrip(GameTestHelper h){
        ItemStack a=original("barley_tea","first"),b=original("barley_tea","second");
        var state=((BlockItem)a.getItem()).getBlock().defaultBlockState();var cups=(TeacupBlock)state.getBlock();state=state.setValue(cups.getCupCountProperty(),4).setValue(cups.getTeaCountProperty(),4);
        var queue=CupQueue.<EncodedCup>legacy(2).append(new EncodedCup(a)).append(new EncodedCup(b));
        var body=new NativeDrinkBlockEntity(h.absolutePos(POS),state);body.commit(state,queue);var saved=body.saveWithFullMetadata();
        var loaded=new NativeDrinkBlockEntity(h.absolutePos(POS),state);loaded.load(saved);var restored=loaded.snapshot(state);
        h.assertTrue(restored.head().isEmpty()&&restored.tail().head().isEmpty(),"two UNKNOWN sentinels stay unknown");
        var known=restored.tail().tail();h.assertTrue(ItemStack.isSameItemSameTags(a,known.head().orElseThrow().stack()),"first exact detached original");
        h.assertTrue(ItemStack.isSameItemSameTags(b,known.tail().head().orElseThrow().stack()),"second independent original");
        a.getOrCreateTag().putString("placed_fixture_original","mutated-after-save");h.assertTrue(!ItemStack.isSameItemSameTags(a,known.head().orElseThrow().stack()),"mutable caller cannot change serialized original");h.succeed();
    }
}
