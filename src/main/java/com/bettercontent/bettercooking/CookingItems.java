package com.bettercontent.bettercooking;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class CookingItems {
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,CookingMod.MOD_ID);
    public static final RegistryObject<Item> PREPARED_BATCH=ITEMS.register("prepared_batch",()->new Item(new Item.Properties().stacksTo(1)) {
        @Override public Component getName(ItemStack stack) {
            var food=CookingRuntime.preparedOutput(stack);
            return food.isEmpty()?Component.translatable("item.better_cooking.prepared_batch"):
                    Component.translatable("item.better_cooking.prepared_named",food.getHoverName());
        }
    });
    private CookingItems() {}
    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
