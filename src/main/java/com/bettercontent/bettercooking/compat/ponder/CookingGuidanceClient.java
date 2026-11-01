package com.bettercontent.bettercooking.compat.ponder;

import net.minecraftforge.fml.ModList;

/** Invoke only after the optional-mod guard in Dist.CLIENT KitchenClient setup enqueueWork. */
public final class CookingGuidanceClient {
    private static boolean registered;
    private CookingGuidanceClient() {}
    public static void register() {
        if(registered || !ModList.get().isLoaded("ponder"))return;
        // Canonical public registration also used by pinned KC's KitchenPonderPlugin.init().
        net.createmod.ponder.foundation.PonderIndex.addPlugin(new CookingGuidancePlugin());
        registered=true;
    }
}
