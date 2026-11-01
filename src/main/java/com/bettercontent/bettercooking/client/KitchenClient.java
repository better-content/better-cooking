package com.bettercontent.bettercooking.client;

import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.kitchen.KitchenContent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CookingMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KitchenClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(KitchenContent.MENU.get(), KitchenScreen::new));
        if (net.minecraftforge.fml.ModList.get().isLoaded("ponder")) event.enqueueWork(com.bettercontent.bettercooking.compat.ponder.CookingGuidanceClient::register);
    }
}
