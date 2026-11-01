package com.bettercontent.bettercooking.compat.ponder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

/** Ponder visual instructions only: no real-world interactions, work completion or intake. */
public final class CookingGuidanceScenes {
    private CookingGuidanceScenes() {}
    private static Block block(String id) {
        var key=new ResourceLocation(id);
        if(!ForgeRegistries.BLOCKS.containsKey(key))throw new IllegalStateException("Missing guidance display block "+id);
        return ForgeRegistries.BLOCKS.getValue(key);
    }
    private static ItemStack icon(String id) {
        var key=new ResourceLocation(id);
        if(!ForgeRegistries.ITEMS.containsKey(key))throw new IllegalStateException("Missing guidance icon "+id);
        return new ItemStack(ForgeRegistries.ITEMS.getValue(key));
    }
    private static void begin(SceneBuilder scene,SceneBuildingUtil util,String id,String title) {
        scene.title(id,title);scene.configureBasePlate(0,0,5);scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(0,1,0,4,3,4),Direction.DOWN);scene.idle(20);
    }
    private static void text(SceneBuilder scene,SceneBuildingUtil util,String value) {
        scene.addKeyframe();scene.overlay().showText(110).text(value).pointAt(util.vector().centerOf(2,2,2)).placeNearTarget();scene.idle(120);
    }
    private static void click(SceneBuilder scene,SceneBuildingUtil util,ItemStack displayOnly) {
        scene.overlay().showControls(util.vector().blockSurface(util.grid().at(2,2,2),Direction.UP),Pointing.DOWN,55).rightClick().withItem(displayOnly);
    }
    public static void preparation(SceneBuilder scene,SceneBuildingUtil util) {
        // Reuse upstream POT geometry only; replace display blocks in Ponder's own isolated client scene.
        scene.world().setBlock(util.grid().at(2,1,2),Blocks.STONE.defaultBlockState(),false);
        scene.world().setBlock(util.grid().at(2,2,2),block("kaleidoscope_cookery:chopping_board").defaultBlockState(),false);
        scene.world().setBlock(util.grid().at(4,1,2),Blocks.STONE.defaultBlockState(),false);
        scene.world().setBlock(util.grid().at(4,2,2),block("ubesdelight:baking_mat_bamboo").defaultBlockState(),false);
        begin(scene,util,"hands_on_preparation","Prepare together at a board or baking mat");
        text(scene,util,"Sneak-right-click the board or mat with an empty main hand to open Kitchen preparation. Keep the native station empty before using its preparation inventory.");
        text(scene,util,"For a listed assembly recipe: place its real ingredients in the nine inputs, Pick recipe, then Begin batch. This commits those ingredients, not a finished meal.");
        text(scene,util,"Keep the required tool in either hand and hold the preparation area. An unfinished hands-on stage needs a nearby connected player. Walking away or closing the menu pauses that active hold.");
        text(scene,util,"Completed repetitions and committed ingredients persist at this shared station. Another player can continue the same batch. Only an interrupted, unfinished active hold must be done again; the batch has no permanent player owner.");
        text(scene,util,"Native cutting is separate: lift the real cutting input onto your inventory cursor, use Next cut (input on cursor), and read the selected recipe ID and ordered output/chance tooltip. Selection consumes nothing.");
        click(scene,util,new ItemStack(Items.BEEF));
        text(scene,util,"Return the cursor stack to inventory and close the menu. Place that input on the board, then cut with a knife satisfying BOTH the KC knife tag and that recipe's source-tool predicate. Raw cuts do not use the held assembly timer.");
        text(scene,util,"Minced beef and native offal can share an input but remain different recipes. The shared board remembers your choice. An ambiguous unset or missing/mismatched choice refuses before payment; a paid board cannot switch recipes.");
        text(scene,util,"EMI lists every native route separately. Read output order, counts and percentages: 20% is a possibility, not a guaranteed extra item. Its Select button requires a valid open board menu and the matching real cursor input; tools are reusable catalysts.");
        text(scene,util,"Once the required hands-on work is genuinely complete, passive heat and supported automation can continue after the original cook leaves. Follow only available recipes; unavailable routes are not demonstrated as finished dishes.");
    }
    public static void pot(SceneBuilder scene,SceneBuildingUtil util) {
        begin(scene,util,"pot_stir_serve","Prepare, stir and serve at the pot");
        text(scene,util,"Use the actual recipe shown for the pot. A protected dish starts with its committed preparation batch; a recipe preview or raw ingredient is not a completed batch.");
        click(scene,util,icon("kaleidoscope_cookery:oil"));
        text(scene,util,"Place the pot above an appropriate working heat source. Follow its actual recipe's oil and input requirements; not every route accepts the same substitute ingredients.");
        click(scene,util,icon("kaleidoscope_cookery:kitchen_shovel"));
        text(scene,util,"Use the native kitchen shovel and perform the required stirs, then allow the native cooking time. A failed stir follows the actual failure result; do not treat waiting or this animation as successful preparation.");
        text(scene,util,"Collect the real completed result with that recipe's actual required container or native serving tool. A pictured bowl would not mean every dish accepts a bowl.");
        text(scene,util,"Age and quality come from the ingredients already committed to the batch. Previewing, transferring and serving do not reset freshness or roll a new quality bonus.");
        text(scene,util,"Follow the currently available pot recipe, not an assumed substitute dish. After hands-on stages are complete, passive cooking can continue without the original cook staying nearby.");
    }
    public static void stockpot(SceneBuilder scene,SceneBuildingUtil util) {
        begin(scene,util,"stockpot_paid_serving","Serve real remaining portions before breaking");
        text(scene,util,"Use the qualified stockpot route's actual prepared batch, soup base, lid and cooking time. The batch has already committed real ingredients; its preview is not extra food.");
        text(scene,util,"A completed batch contains its actual recipe yield. Each successful take-out removes one real remaining portion. Reopening, saving or changing players must not refill it.");
        text(scene,util,"If the recipe requires an external container, bring that actual container for each portion. A bowl, bottle or other carrier is accepted only when its real recipe predicate allows it; no container is fabricated on break.");
        text(scene,util,"For finished food whose required containers have not yet been paid, serve it first. Breaking is refused before block removal or tool wear; do not assume breaking packages the remaining food for free.");
        text(scene,util,"Serve remaining food with required containers before breaking.");
        text(scene,util,"After serving, only the true remaining state can be recovered. A no-container or already-paid carrier route is different from an unpaid external-container route; never treat them as interchangeable.");
        text(scene,util,"An unfinished committed preparation is recovered as its actual batch, not promoted to cooked portions. Corrupt or mismatched state is not permission to mint replacement food.");
        text(scene,util,"The original cook does not own the pot. After the real preparation is complete, passive cooking and supported automation can continue; another player can serve the remaining portions with the correct containers.");
    }
    public static void pastry(SceneBuilder scene,SceneBuildingUtil util) {
        scene.world().setBlock(util.grid().at(2,1,2),Blocks.STONE.defaultBlockState(),false);
        scene.world().setBlock(util.grid().at(2,2,2),block("ubesdelight:baking_mat_bamboo").defaultBlockState(),false);
        scene.world().setBlock(util.grid().at(0,1,2),Blocks.FURNACE.defaultBlockState(),false);
        scene.world().setBlock(util.grid().at(4,1,2),Blocks.CAMPFIRE.defaultBlockState(),false);
        begin(scene,util,"pastry_typed_finish","Prepare on the mat, finish in the correct appliance");
        text(scene,util,"At the baking mat, open Kitchen preparation with an empty main hand while sneaking. Add the exact real ingredients, Pick recipe, Begin batch, then perform its tool and hold stages.");
        text(scene,util,"The output is the actual committed prepared batch, not automatically baked pastry. Transfer that same stack to the appliance required by its currently qualified finishing recipe.");
        text(scene,util,"An oven-style baking route uses the typed furnace-compatible finish and its real cooking time and fuel. A roasting route uses the typed campfire finish. One appliance is not a universal substitute for the other.");
        scene.overlay().showControls(util.vector().centerOf(0,1,2),Pointing.RIGHT,55).withItem(new ItemStack(Items.COAL));
        text(scene,util,"The pictured furnace is the source furnace-compatible path, not a promise that any modded oven is supported. Read the exact recipe. Do not use a raw-to-premium shortcut or a different batch's recipe.");
        scene.overlay().showControls(util.vector().centerOf(4,1,2),Pointing.LEFT,55).withItem(new ItemStack(Items.CLOCK));
        text(scene,util,"The actual carried batch must match the current recipe and recorded preparation. Missing or obsolete preparation is refused. Roasting-spit/shawarma support is NOT claimed; its split and yield restrictions are separate.");
        text(scene,util,"Finishing transfers the actual old ingredient ages and batch quality. Moving through the mat, furnace or campfire does not manufacture a fresh clock, reroll quality or award intake before eating.");
        text(scene,util,"After real hands-on preparation is complete, passive furnace or campfire cooking does not require the original cook to remain online. Use only the exact available finishing recipe; no unlisted finished pastry is promised.");
    }
}
