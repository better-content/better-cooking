package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.CookingItems;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ShawarmaSpitBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * This native spit splits the actual input BEFORE assemble, then overwrites result count with input
 * count. A prepared batch is not one ordinary meat item: that path would erase provenance or yield.
 * Reject our batch BEFORE debit; typed prepared roasting uses campfire/skillet instead. Native raw
 * meat bulk roasting, automation and every ordinary native recipe remain untouched.
 */
@Mixin(value=ShawarmaSpitBlockEntity.class,remap=false)
public abstract class PreparedRoastingSpitGuardMixin {
    @Inject(method="onPutCookingItem",at=@At("HEAD"),cancellable=true,require=1,remap=false)
    private void betterCooking$appropriateRoastingSurface(Level level,ItemStack input,CallbackInfoReturnable<Boolean> cir) {
        if(input.is(CookingItems.PREPARED_BATCH.get()))cir.setReturnValue(false);
    }
}
