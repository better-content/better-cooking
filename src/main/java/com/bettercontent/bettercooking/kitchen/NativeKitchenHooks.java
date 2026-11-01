package com.bettercontent.bettercooking.kitchen;

import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity;
import com.chefmooon.ubesdelight.common.block.entity.forge.BakingMatBlockEntityImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

/** Narrow adapters for the pinned native surfaces. No reflective access or general automation ban. */
public final class NativeKitchenHooks {
    private NativeKitchenHooks() {}
    public static void install() {
        KitchenHooks.setNativeOccupied(be -> {
            if (be instanceof ChoppingBoardBlockEntity board) return !board.getCurrentCutStack().isEmpty() || board.getCurrentCutCount() != 0;
            if (be instanceof BakingMatBlockEntityImpl mat) return !mat.isEmpty();
            return true;
        });
    }
    public static void recoverOnRemove(BlockState oldState, Level level, BlockPos pos, BlockState replacement) {
        if (level.isClientSide || oldState.is(replacement.getBlock())) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) be.getCapability(KitchenContent.KITCHEN).ifPresent(KitchenInventory::recover);
    }
    public static ItemStackHandler guardNativeInventory(BlockEntity station, ItemStackHandler original) {
        return new GuardedInventory(station, original);
    }
    /** Install at native handler creation, so handlers cached before preparation remain protected. */
    private static final class GuardedInventory extends ItemStackHandler {
        private final BlockEntity station;
        private final ItemStackHandler delegate;
        private GuardedInventory(BlockEntity station, ItemStackHandler delegate) {
            super(delegate.getSlots()); this.station = station; this.delegate = delegate;
        }
        @Override public void setSize(int size) { delegate.setSize(size); }
        @Override public int getSlots() { return delegate.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return delegate.getStackInSlot(slot); }
        @Override public void setStackInSlot(int slot, ItemStack stack) { delegate.setStackInSlot(slot, stack); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return KitchenHooks.occupied(station) ? stack : delegate.insertItem(slot, stack, simulate);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return delegate.extractItem(slot, amount, simulate); }
        @Override public int getSlotLimit(int slot) { return delegate.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return !KitchenHooks.occupied(station) && delegate.isItemValid(slot, stack); }
        @Override public CompoundTag serializeNBT() { return delegate.serializeNBT(); }
        @Override public void deserializeNBT(CompoundTag tag) { delegate.deserializeNBT(tag); }
    }
}
