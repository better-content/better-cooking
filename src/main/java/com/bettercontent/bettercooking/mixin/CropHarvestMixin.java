package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.acquisition.NewCropOutputs;
import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.betterbumblezonecrops.CultivarHarvestBoundary;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import com.github.ysbbbbbb.kaleidoscopecookery.block.crop.BaseCropBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Exactly the native BaseCrop tomato right-click production; subclass PASS methods are not phantom targets. */
@Mixin(BaseCropBlock.class)
public abstract class CropHarvestMixin {
    @Redirect(method = "use(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V"), require = 1)
    private void betterCooking$newCropOutput(Level level, BlockPos position, ItemStack produced,
            BlockState actualState, Level actualLevel, BlockPos actualPosition, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (CookingMod.discoveryOnly()) { Block.popResource(level, position, produced); return; }
        if (level != actualLevel || !position.equals(actualPosition)) throw new IllegalStateException("Native KC crop production context drift");
        CultivarHarvestBoundary.begin(actualState, actualLevel, actualPosition, player);
        try { Block.popResource(level, position, NewCropOutputs.canonicalNewOutput(level, position, produced)); }
        finally { CultivarHarvestBoundary.end(); }
    }
}
