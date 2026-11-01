package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.cutting.*;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IChoppingBoard;
import com.github.ysbbbbbb.kaleidoscopecookery.block.dispenser.ChoppingBoardDispenseBehavior;
import net.minecraft.core.BlockSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only the pinned KC handler's redundant outer debit, after a proven own-recipe native split. */
@Mixin(value = ChoppingBoardDispenseBehavior.class, remap = false)
public abstract class MixinNativeCuttingDispenser {
    @Unique private DispenserDebitProof cooking$paidSplit;
    @Unique private long cooking$invocation;
    @Inject(method = {"execute", "m_7498_"}, at = @At("HEAD"), require = 1, remap = false)
    private void cooking$begin(BlockSource source, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        cooking$paidSplit = null; cooking$invocation++;
    }
    @Redirect(method = {"execute", "m_7498_"}, at = @At(value = "INVOKE", target = "Lcom/github/ysbbbbbb/kaleidoscopecookery/api/blockentity/IChoppingBoard;onPutItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z"), require = 1, remap = false)
    private boolean cooking$actualPaidInsertion(IChoppingBoard board, Level level, LivingEntity actor, ItemStack stack) {
        cooking$paidSplit = null;
        if (CookingMod.discoveryOnly()) return board.onPutItem(level, actor, stack);
        CuttingSession previous = board instanceof CuttingBoardAccess access ? access.cooking$cuttingSession() : null;
        int before = stack.getCount(); long invocation = cooking$invocation;
        boolean accepted;
        try { accepted = board.onPutItem(level, actor, stack); }
        finally { cooking$paidSplit = null; }
        if (invocation != cooking$invocation) throw new IllegalStateException("Reentrant KC dispenser insertion");
        cooking$paidSplit = DispenserDebitProof.accepted(board, previous, stack, before, accepted);
        return accepted;
    }
    // Both pinned mapped-development and actual shipped SRG callsite names, exactly one required as a group.
    @Group(name = "cutting_paid_debit", min = 1, max = 1)
    @Redirect(method = {"execute", "m_7498_"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), require = 0, remap = false)
    private void cooking$mappedOuterDebit(ItemStack stack, int amount) { cooking$outerDebit(stack, amount); }
    @Group(name = "cutting_paid_debit", min = 1, max = 1)
    @Redirect(method = {"execute", "m_7498_"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;m_41774_(I)V"), require = 0, remap = false)
    private void cooking$runtimeOuterDebit(ItemStack stack, int amount) { cooking$outerDebit(stack, amount); }
    @Unique private void cooking$outerDebit(ItemStack stack, int amount) {
        DispenserDebitProof proof = cooking$paidSplit; cooking$paidSplit = null;
        try {
            if (proof == null || CookingMod.discoveryOnly()) stack.shrink(amount);
            else proof.verify(stack, amount); // The actual native split already paid once; no second shrink or refund.
        } finally { cooking$paidSplit = null; }
    }
    @Inject(method = {"execute", "m_7498_"}, at = @At("RETURN"), require = 1, remap = false)
    private void cooking$end(BlockSource source, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) { cooking$paidSplit = null; }
}
