package com.bettercontent.bettercooking.cutting;

import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IChoppingBoard;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity;
import net.minecraft.world.item.ItemStack;

/** One accepted native invocation, not an actorless authorization or a refund. No global/thread-local state. */
public final class DispenserDebitProof {
    private final ItemStack original;
    private final int after;
    private final CuttingBoardAccess board;
    private final CuttingSession session;
    private DispenserDebitProof(ItemStack original, int after, CuttingBoardAccess board, CuttingSession session) {
        this.original = original; this.after = after; this.board = board; this.session = session;
    }
    public static DispenserDebitProof accepted(IChoppingBoard nativeBoard, CuttingSession previous, ItemStack original, int before, boolean accepted) {
        if (!accepted || !(nativeBoard instanceof CuttingBoardAccess access)) return null;
        CuttingSession current = access.cooking$cuttingSession();
        if (current == null || current == previous) return null; // Legacy native recipe: leave its behavior entirely unchanged.
        if (!(nativeBoard instanceof ChoppingBoardBlockEntity actual) || before < 1 || original.getCount() != before - 1)
            throw new IllegalStateException("Owned dispenser insertion did not split exactly one actual source item");
        current.validateNative(actual.getCurrentCutStack(), actual.getMaxCutCount(), actual.getCurrentCutCount());
        return new DispenserDebitProof(original, original.getCount(), access, current);
    }
    public void verify(ItemStack stack, int amount) {
        if (stack != original || stack.getCount() != after || amount != 1 || board.cooking$cuttingSession() != session)
            throw new IllegalStateException("Dispenser paid-split proof drift; refusing a speculative shrink suppression");
    }
}
