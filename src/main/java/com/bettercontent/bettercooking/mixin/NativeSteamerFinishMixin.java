package com.bettercontent.bettercooking.mixin;
import com.bettercontent.bettercooking.finishing.NativeFinishing;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SteamerBlockEntity.class)
public abstract class NativeSteamerFinishMixin {
    /** The real old slot still contains its paid batch. Never scan another parallel slot's preparation. */
    @Redirect(method="cookingTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lcom/github/ysbbbbbb/kaleidoscopecookery/blockentity/kitchen/SteamerBlockEntity;)V",remap=false,
        at=@At(value="INVOKE",target="Lnet/minecraft/core/NonNullList;set(ILjava/lang/Object;)Ljava/lang/Object;",remap=false),require=1)
    private Object betterCooking$carryFinalSteamerSlot(NonNullList<ItemStack> slots,int index,Object produced) {
        if(!(produced instanceof ItemStack output))throw new IllegalStateException("Pinned native steamer no longer commits an ItemStack");
        return slots.set(index,NativeFinishing.steamer(slots.get(index),output));
    }
}
