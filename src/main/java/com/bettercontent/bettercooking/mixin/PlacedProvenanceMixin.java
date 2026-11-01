package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.placed.PlacedProvenance;
import com.bettercontent.betterspicyfarmers.seasoning.PlacedFoodAdapters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Decorates mandatory Spice's existing successful native placed-food transactions, not a new eating path. */
@Mixin(value = PlacedFoodAdapters.class, remap = false)
public abstract class PlacedProvenanceMixin {
    @Inject(method = "placed", at = @At("RETURN"), remap = false)
    private static void cooking$placed(Level level, BlockPos pos, BlockState state, ItemStack original, CallbackInfo ci) {
        if (!CookingMod.discoveryOnly() && level instanceof ServerLevel server)
            PlacedProvenance.placed(server, pos, state, original, PlacedFoodAdapters.remaining(state));
    }
    @Inject(method = "preview", at = @At("RETURN"), cancellable = true, remap = false)
    private static void cooking$preview(Level level, BlockPos pos, BlockState state, ItemStack portion,
                                       CallbackInfoReturnable<ItemStack> cir) {
        if (!CookingMod.discoveryOnly() && level instanceof ServerLevel server)
            cir.setReturnValue(PlacedProvenance.preview(server, pos, state, cir.getReturnValue(), PlacedFoodAdapters.remaining(state)));
    }
    @Inject(method = "completed", at = @At("RETURN"), remap = false)
    private static void cooking$completed(Level level, BlockPos pos, BlockState before, CallbackInfo ci) {
        if (!CookingMod.discoveryOnly() && level instanceof ServerLevel server)
            PlacedProvenance.completed(server, pos, before, PlacedFoodAdapters.remaining(before));
    }
    @Inject(method = "replaced", at = @At("RETURN"), remap = false)
    private static void cooking$replaced(Level level, BlockPos pos, BlockState before, BlockState after, CallbackInfo ci) {
        if (!CookingMod.discoveryOnly() && level instanceof ServerLevel server)
            PlacedProvenance.replaced(server, pos, before, after);
    }
}
