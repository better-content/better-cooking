package com.bettercontent.bettercooking.kitchen;

import com.bettercontent.bettercooking.CookingMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.core.Direction;

public final class KitchenEvents {
    private static boolean registered;
    public static void register() { if (!registered) { registered = true; MinecraftForge.EVENT_BUS.register(new KitchenEvents()); KitchenNetwork.register(); } }
    private static boolean supported(BlockEntity be) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock());
        return id != null && (id.toString().equals("kaleidoscope_cookery:chopping_board") || id.getNamespace().equals("ubesdelight") && id.getPath().startsWith("baking_mat"));
    }
    @SubscribeEvent public void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        BlockEntity be = event.getObject(); if (!supported(be)) return;
        KitchenInventory inventory = new KitchenInventory(be);
        LazyOptional<KitchenInventory> optional = LazyOptional.of(() -> inventory);
        event.addCapability(new ResourceLocation(CookingMod.MOD_ID, "kitchen"), new ICapabilitySerializable<CompoundTag>() {
            @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) { return capability == KitchenContent.KITCHEN ? optional.cast() : LazyOptional.empty(); }
            @Override public CompoundTag serializeNBT() { return inventory.serializeNBT(); }
            @Override public void deserializeNBT(CompoundTag tag) { inventory.deserializeNBT(tag); }
        });
        event.addListener(optional::invalidate);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void interact(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos()); if (be == null) return;
        be.getCapability(KitchenContent.KITCHEN).ifPresent(kitchen -> {
            boolean resume = event.getItemStack().hasTag() && event.getItemStack().getTag().contains(KitchenInventory.RESUME);
            boolean open = event.getEntity().isShiftKeyDown() && event.getItemStack().isEmpty();
            if (!resume && !open) return;
            event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
            if (!(event.getEntity() instanceof ServerPlayer player) || !kitchen.canAccess(player)) return;
            if (resume) {
                if (kitchen.restore(event.getItemStack())) event.getItemStack().shrink(1);
                else kitchen.refundObsolete(event.getItemStack(), player);
                return;
            }
            if (open) NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inv, p) -> new KitchenMenu(id, inv, kitchen, kitchen.surface()), Component.literal("Kitchen preparation")),
                buf -> buf.writeUtf(kitchen.surface()));
        });
    }
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.containerMenu instanceof KitchenMenu menu && menu.kitchen() != null) menu.serverTick(player);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void breakBlock(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (be != null) be.getCapability(KitchenContent.KITCHEN).ifPresent(KitchenInventory::recover);
    }
}
