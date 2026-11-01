package com.bettercontent.bettercooking.kitchen;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Typed bridge to root-owned native adapters. Fail closed until installed. */
public final class KitchenHooks {
    private static Predicate<BlockEntity> nativeOccupied = be -> true;
    private KitchenHooks() {}
    public static void setNativeOccupied(Predicate<BlockEntity> predicate) { nativeOccupied = Objects.requireNonNull(predicate); }
    public static boolean nativeOccupied(BlockEntity be) { return nativeOccupied.test(be); }
    public static boolean occupied(BlockEntity be) {
        return be.getCapability(KitchenContent.KITCHEN).map(KitchenInventory::occupied).orElse(false);
    }
}
