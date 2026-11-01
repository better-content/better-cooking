package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.cutting.NativeCuttingRouting;
import net.minecraft.core.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Vanilla target uses ordinary refmap remapping; exactly one actual behavior invocation. */
@Mixin(DispenserBlock.class)
public abstract class MixinNativeCuttingRouting {
    @Redirect(method = "dispenseFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/dispenser/DispenseItemBehavior;dispense(Lnet/minecraft/core/BlockSource;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"), require = 1)
    private ItemStack cooking$onlyActualOwnedBoard(DispenseItemBehavior original, BlockSource source, ItemStack stack) {
        return NativeCuttingRouting.dispense(original, source, stack);
    }
}
