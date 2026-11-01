package com.bettercontent.bettercooking.kitchen;

import com.bettercontent.bettercooking.CookingMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class KitchenContent {
    public static final Capability<KitchenInventory> KITCHEN = CapabilityManager.get(new CapabilityToken<>() {});
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, CookingMod.MOD_ID);
    public static final RegistryObject<MenuType<KitchenMenu>> MENU = MENUS.register("preparation", () -> IForgeMenuType.create(KitchenMenu::new));
    private KitchenContent() {}
    public static void register(IEventBus bus) {
        MENUS.register(bus);
        bus.addListener((net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent event) -> event.register(KitchenInventory.class));
    }
}
