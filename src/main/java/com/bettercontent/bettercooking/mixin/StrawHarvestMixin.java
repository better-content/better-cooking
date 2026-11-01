package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.acquisition.NewCropOutputs;
import com.github.ysbbbbbb.kaleidoscopecookery.block.misc.StrawBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Both NEW native outputs only: native panicle five and native rice four; no grain multiplication. */
@Mixin(StrawBlocks.class)
public abstract class StrawHarvestMixin {
    @ModifyArg(method = "fallOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;F)V",
        at = @At(value = "INVOKE", target = "Lcom/github/ysbbbbbb/kaleidoscopecookery/block/misc/StrawBlocks;popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V"), index = 2, require = 2)
    private ItemStack betterCooking$newStrawOutput(Level level, BlockPos position, ItemStack produced) { return NewCropOutputs.canonicalNewOutput(level, position, produced); }
}
