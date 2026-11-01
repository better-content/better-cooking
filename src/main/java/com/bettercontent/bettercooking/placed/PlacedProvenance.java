package com.bettercontent.bettercooking.placed;

import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.CookingRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Shared native lifecycle entrypoints for Spice FD/cake hooks and explicit root-owned KC adapters.
 * Only completed native transactions call placed/completed/replaced; preview never awards intake. */
public final class PlacedProvenance {
    private PlacedProvenance() {}
    public static void placed(ServerLevel level, BlockPos pos, BlockState state, ItemStack original, int remaining) {
        if (CookingMod.discoveryOnly()) return;
        pos = NativeFoodServings.anchor(pos, state);
        if (remaining <= 0) { PlacedProvenanceData.get(level).clear(pos); return; }
        PlacedProvenanceData.get(level).put(pos, state, original, remaining);
    }
    public static ItemStack preview(ServerLevel level, BlockPos pos, BlockState state, ItemStack portion, int remaining) {
        if (CookingMod.discoveryOnly()) return portion;
        return PlacedProvenanceData.get(level).original(NativeFoodServings.anchor(pos, state), state, remaining)
                .map(original -> CookingRuntime.copyServingProvenance(original, portion.copy(), level.getGameTime()))
                .orElseGet(portion::copy);
    }
    public static void completed(ServerLevel level, BlockPos pos, BlockState before, int remainingBefore) {
        if (!CookingMod.discoveryOnly()) PlacedProvenanceData.get(level).complete(NativeFoodServings.anchor(pos, before), before, remainingBefore);
    }
    public static void replaced(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        if (!CookingMod.discoveryOnly() && before.getBlock() != after.getBlock()) PlacedProvenanceData.get(level).clear(NativeFoodServings.anchor(pos, before));
    }
}
