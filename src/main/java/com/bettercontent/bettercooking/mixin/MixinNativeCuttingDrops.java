package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.cutting.*;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.ChoppingBoardBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve native block loot, but derive own paid contents from pure session preview for ALL cut counts. */
@Mixin(value = ChoppingBoardBlock.class, remap = false)
public abstract class MixinNativeCuttingDrops {
    @Redirect(method = {"getDrops", "m_49635_"}, at = @At(value = "INVOKE", target = "Lcom/github/ysbbbbbb/kaleidoscopecookery/blockentity/kitchen/ChoppingBoardBlockEntity;getCurrentCutStack()Lnet/minecraft/world/item/ItemStack;"), require = 2, remap = false)
    private ItemStack cooking$suppressOnlyLegacyOwnRefund(ChoppingBoardBlockEntity board) {
        if (!CookingMod.discoveryOnly() && board instanceof CuttingBoardAccess access && access.cooking$cuttingSession() != null)
            return ItemStack.EMPTY; // Native branch would append the input only at cutCount0; our pure ledger owns it.
        return board.getCurrentCutStack();
    }
    @Inject(method = {"getDrops", "m_49635_"}, at = @At("RETURN"), cancellable = true, require = 1, remap = false)
    private void cooking$pureOwnedContents(BlockState state, LootParams.Builder context, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (CookingMod.discoveryOnly()) return;
        var entity = context.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(entity instanceof ChoppingBoardBlockEntity board) || !(entity instanceof CuttingBoardAccess access)) return;
        CuttingSession session = access.cooking$cuttingSession();
        if (session == null) return;
        session.validateNative(board.getCurrentCutStack(), board.getMaxCutCount(), board.getCurrentCutCount());
        List<ItemStack> loot = new ArrayList<>(cir.getReturnValue());
        loot.addAll(session.previewDrops(context.getLevel()));
        cir.setReturnValue(loot); // No live cursor advance, chance selection, reset, or onRemove cleanup.
    }
}
