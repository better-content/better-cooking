package com.bettercontent.bettercooking.stockpot;

/** Typed admission check for the actual native recipe-item pre-debit caller. */
public interface PreparedStockpotStateAccess {
    boolean cooking$isPreparedStockpotSealed();
    /** Pure snapshot from actual native phase/input/result/count. Throws on unsafe/corrupt state. */
    java.util.List<net.minecraft.world.item.ItemStack> cooking$preparedStockpotBreakDrops();
}
