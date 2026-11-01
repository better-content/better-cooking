package com.bettercontent.bettercooking.fixture.tests.acquisition;

import com.bettercontent.bettercooking.acquisition.NewCropOutputs;
import com.bettercontent.betterbumblezonecrops.CultivarCatalog;
import com.bettercontent.betterbumblezonecrops.WildCropForage;
import com.bettercontent.betterbumblezonecrops.api.CultivarAcquisition;
import com.bettercontent.bettercooking.CookingMod;
import com.github.ysbbbbbb.kaleidoscopecookery.block.crop.RiceCropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Actual registered native stacks/blocks/API; no bootstrap stubs or fabricated qualification.
 * Discovery branches prove ONLY inertness, not production acquisition acceptance.
 */
@GameTestHolder(CookingMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CropAcquisitionGameTests {
    private static ResourceLocation id(String text) { return new ResourceLocation(text); }
    private static ItemStack stack(String text, int count) {
        var item = ForgeRegistries.ITEMS.getValue(id(text));
        if (item == null || item == Items.AIR) throw new AssertionError("Missing exact native item " + text);
        return new ItemStack(item,count);
    }
    @GameTest(template="forge:empty",timeoutTicks=40)
    public static void detachedCanonicalCopyPreservesExactCountAndMeaningfulNbt(GameTestHelper h) {
        ItemStack original=stack("kaleidoscope_cookery:tomato",257);
        var tag=original.getOrCreateTag();tag.putString("BumblezoneCultivarOrigin","the_bumblezone:the_bumblezone");tag.putLong("FixtureOldestAge",9123);tag.putDouble("FixtureQuality",0.83);
        CompoundTag nested=new CompoundTag();nested.putString("paidIngredient","original");tag.put("FixtureNested",nested);
        CompoundTag before=original.save(new CompoundTag());
        h.assertTrue(!CookingMod.discoveryOnly(),"Canonical copy control requires qualified production alias execution; discovery inertness is not copy acceptance");
        ItemStack canonical=NewCropOutputs.canonicalNewOutput(original);
        h.assertTrue(canonical.getCount()==257&&canonical.is(ForgeRegistries.ITEMS.getValue(id("farmersdelight:tomato"))),"exact canonical identity/count, no byte truncation or multiplier");
        h.assertTrue(canonical.getTag()!=original.getTag()&&canonical.getTag().equals(original.getTag()),"meaningful origin/age/quality/nested tags detached intact");
        canonical.getTag().getCompound("FixtureNested").putString("paidIngredient","changedCopy");
        h.assertTrue(original.save(new CompoundTag()).equals(before),"source/old stock remains byte-identical after detached copy mutation");h.succeed();
    }
    @GameTest(template="forge:empty",timeoutTicks=40)
    public static void actualModeHonorsDiscoveryIdentityOrQualifiedProductionAlias(GameTestHelper h) {
        ItemStack original=stack("kaleidoscope_cookery:tomato",5);CompoundTag before=original.save(new CompoundTag());
        ItemStack result=NewCropOutputs.canonicalNewOutput(original);
        if(CookingMod.discoveryOnly())h.assertTrue(result==original,"DiscoveryOnly is identity, never an alias/authority acceptance pass");
        else h.assertTrue(result!=original&&result.is(ForgeRegistries.ITEMS.getValue(id("farmersdelight:tomato")))&&result.getCount()==5,"live qualified authored production alias actually executes");
        h.assertTrue(original.save(new CompoundTag()).equals(before),"ordinary inventory source unchanged");h.succeed();
    }
    @GameTest(template="forge:empty",timeoutTicks=40)
    public static void newSeedOriginGateDoesNotBanCanonicalProduce(GameTestHelper h) {
        h.assertTrue(CultivarAcquisition.apiVersion()==1,"actual runtime provider API version");
        ItemStack seed=stack("farmersdelight:tomato_seeds",4);
        // A pre-existing source marker on the NEW candidate must not mint seeds off-origin.
        seed.getOrCreateTag().putString("BumblezoneCultivarOrigin","the_bumblezone:the_bumblezone");CompoundTag before=seed.save(new CompoundTag());
        h.assertTrue(CultivarAcquisition.restrictNewOutput(id("minecraft:overworld"),seed).isEmpty(),"NEW seed multiplication outside existing origin denied even with copied tag");
        ItemStack allowed=CultivarAcquisition.restrictNewOutput(id("the_bumblezone:the_bumblezone"),seed);
        h.assertTrue(allowed!=seed&&allowed.getCount()==4&&allowed.getTag().getString("BumblezoneCultivarOrigin").equals("the_bumblezone:the_bumblezone"),"actual-source seed allowed as detached copy without count change");
        h.assertTrue(seed.save(new CompoundTag()).equals(before),"physical original seed stock untouched");
        for(var spec:java.util.List.of(java.util.Map.entry("farmersdelight:rice",4),java.util.Map.entry("farmersdelight:rice_panicle",5))){
            ItemStack food=stack(spec.getKey(),spec.getValue());
            h.assertTrue(CultivarAcquisition.restrictNewOutput(id("minecraft:overworld"),food)==food,"native rice four/panicles five are produce, not seed-falsely-banned");
        }
        h.succeed();
    }
    @GameTest(template="forge:empty",timeoutTicks=40)
    public static void typedRiceThreeLocationsHaveExactlyOnePrimaryAndForeignSourceFails(GameTestHelper h) {
        var block=ForgeRegistries.BLOCKS.getValue(id("kaleidoscope_cookery:rice_crop"));h.assertTrue(block instanceof RiceCropBlock,"pinned actual rice class");
        h.assertTrue(!CookingMod.discoveryOnly(),"Typed rice control requires the real production provider registration, not direct private adapter invocation");
        BlockPos base=new BlockPos(1,1,1);int primaries=0;
        for(int location=0;location<3;location++){
            var part=CultivarAcquisition.aliasHarvestPosition(block.defaultBlockState().setValue(RiceCropBlock.LOCATION,location),base.above(location))
                .orElseThrow(()->new AssertionError("Actual registered provider must select the KC rice adapter"));
            h.assertTrue(part.anchor().equals(base)&&part.primary()==(location==0),"exact LOCATION root, no HALF guessing or extra segment credit");if(part.primary())primaries++;
        }
        h.assertTrue(primaries==1,"only one propagation/refund source among all three segments");
        boolean rejected=CultivarAcquisition.aliasHarvestPosition(Blocks.WHEAT.defaultBlockState(),base).isEmpty();
        h.assertTrue(rejected,"foreign native source cannot use KC rice adapter");h.succeed();
    }
    private static boolean hasPlanting(net.minecraft.server.level.ServerLevel level, BlockPos position) {
        for(var tag:com.bettercontent.betterbumblezonecrops.CultivarPlantings.get(level).save(new CompoundTag()).getList("plants",10))
            if(((CompoundTag)tag).getLong("pos")==position.asLong())return true;
        return false;
    }
    @GameTest(template="forge:empty",timeoutTicks=40)
    public static void actualTomatoUseConsumesSourcedProvenanceExactlyOnce(GameTestHelper h) {
        var level=h.getLevel();h.assertTrue(level.dimension()==net.minecraft.world.level.Level.OVERWORLD,"real Overworld provenance fixture");
        BlockPos relative=new BlockPos(1,2,1),absolute=h.absolutePos(relative);
        var crop=ForgeRegistries.BLOCKS.getValue(id("kaleidoscope_cookery:tomato_crop"));
        h.assertTrue(crop instanceof com.github.ysbbbbbb.kaleidoscopecookery.block.crop.BaseCropBlock,"pinned actual native tomato class");
        var mature=crop.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE,7);
        h.setBlock(relative.below(),Blocks.FARMLAND.defaultBlockState());h.setBlock(relative,mature);
        // Real native ServerPlayer type, not FakePlayer; disconnected fixture, NOT connected-client evidence.
        var player=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"crop-fixture"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        ItemStack seed=stack("kaleidoscope_cookery:tomato_seed",1);
        com.bettercontent.betterbumblezonecrops.CultivarPlantings.source(seed,"the_bumblezone:the_bumblezone");
        com.bettercontent.betterbumblezonecrops.CultivarPlantings.planted(player,absolute,mature,seed);
        h.assertTrue(hasPlanting(level,absolute)!=CookingMod.discoveryOnly(),"physical sourced seed provenance follows actual mode/registered alias");
        var observed=new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<com.bettercontent.betterbumblezonecrops.api.event.CultivarHarvestEvent> observer=event->{
            if(event.level==level&&event.position.equals(absolute)&&event.owner.equals(player.getUUID()))observed.incrementAndGet();
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,com.bettercontent.betterbumblezonecrops.api.event.CultivarHarvestEvent.class,observer);
        try {
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(absolute),net.minecraft.core.Direction.UP,absolute,false);
            for(int harvest=0;harvest<2;harvest++){
                h.setBlock(relative,mature); // A second mature harvest, without planting/refunding another seed.
                h.assertTrue(crop.use(mature,level,absolute,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit)==net.minecraft.world.InteractionResult.SUCCESS,"actual transformed native use/pop executes");
            }
            h.assertTrue(!hasPlanting(level,absolute),"first actual produce spawn consumes planting operation");
            h.assertTrue(observed.get()==(CookingMod.discoveryOnly()?0:1),"actual native sourced harvest credited once; discovery never synthesizes provenance");
            var output=ForgeRegistries.ITEMS.getValue(id(CookingMod.discoveryOnly()?"kaleidoscope_cookery:tomato":"farmersdelight:tomato"));
            int emitted=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(absolute).inflate(1.5),entity->entity.getItem().is(output)).stream().mapToInt(entity->entity.getItem().getCount()).sum();
            h.assertTrue(emitted>=2,"actual native produce ItemEntities, not a loot-only calculation");h.succeed();
        } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(observer); }
    }
    @GameTest(template="forge:empty",timeoutTicks=40)
    public static void providerAliasesMatchModeWithoutInventingDimensionGrowthRules(GameTestHelper h) {
        var legacy=CultivarCatalog.byPlant("kaleidoscope_cookery:rice_crop");
        if(CookingMod.discoveryOnly())h.assertTrue(legacy==null,"DiscoveryOnly must not install Cooking's provider aliases");
        else {
            h.assertTrue(legacy==CultivarCatalog.byPlant("farmersdelight:rice"),"legacy rice returns SAME canonical definition, not invented rule");
            h.assertTrue(CultivarCatalog.byPlant("kaleidoscope_cookery:tomato_crop")==CultivarCatalog.byPlant("farmersdelight:tomatoes"),"all BaseCrop tomato alias covered");
            h.assertTrue(CultivarCatalog.byPlant("kaleidoscope_cookery:lettuce_crop")==CultivarCatalog.byPlant("farmersdelight:cabbages"),"all BaseCrop lettuce alias covered despite PASS-only use");
            h.assertTrue(CultivarCatalog.isSeed(legacy,"kaleidoscope_cookery:rice")&&CultivarCatalog.isSeed(legacy,"kaleidoscope_cookery:wild_rice"),"finite legacy seed roles retained");
            h.assertTrue(WildCropForage.blocksPlanting("farmersdelight:rice","kaleidoscope_cookery:rice_crop"),"existing PUBLIC edible planting gate recognizes aliased plant");
            h.assertTrue(!WildCropForage.blocksPlanting("kaleidoscope_cookery:rice","kaleidoscope_cookery:rice_crop"),"legacy OLD seed stock not blanket blocked");
        }
        h.succeed();
    }
}
