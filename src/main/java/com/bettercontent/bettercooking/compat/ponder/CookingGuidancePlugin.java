package com.bettercontent.bettercooking.compat.ponder;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/** Four independent story IDs referencing KC schematics without replacing upstream plugins. */
public final class CookingGuidancePlugin implements PonderPlugin {
    @Override public String getModId() { return "better_cooking"; }
    private static ResourceLocation id(String value) { return new ResourceLocation(value); }
    @Override public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation pot=id("kaleidoscope_cookery:pot/pot_introduction");
        ResourceLocation stockpot=id("kaleidoscope_cookery:stockpot/stockpot_introduction");
        // ResourceLocation overload preserves upstream schematic namespace, not better_cooking:pot/....
        for(String component:new String[]{"kaleidoscope_cookery:chopping_board","ubesdelight:baking_mat_bamboo","ubesdelight:baking_mat_kelp","better_cooking:prepared_batch"})
            helper.addStoryBoard(id(component),pot,CookingGuidanceScenes::preparation);
        helper.addStoryBoard(id("kaleidoscope_cookery:pot"),pot,CookingGuidanceScenes::pot);
        helper.addStoryBoard(id("kaleidoscope_cookery:stockpot"),stockpot,CookingGuidanceScenes::stockpot);
        for(String component:new String[]{"ubesdelight:baking_mat_bamboo","ubesdelight:baking_mat_kelp","better_cooking:prepared_batch","minecraft:furnace","minecraft:campfire"})
            helper.addStoryBoard(id(component),pot,CookingGuidanceScenes::pastry);
    }
}
