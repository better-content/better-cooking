package com.bettercontent.bettercooking.cutting;

import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.kitchen.KitchenHooks;
import com.github.ysbbbbbb.kaleidoscopecookery.block.dispenser.ChoppingBoardDispenseBehavior;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import com.github.ysbbbbbb.kaleidoscopecookery.init.tag.TagMod;
import net.minecraft.core.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;

/** At the real vanilla invocation only; never overwrites any global item behavior or knife tag. */
public final class NativeCuttingRouting {
    private NativeCuttingRouting() {}
    public static ItemStack dispense(DispenseItemBehavior original, BlockSource source, ItemStack stack) {
        if (CookingMod.discoveryOnly()) return original.dispense(source, stack);
        var position = source.getPos().relative(source.getBlockState().getValue(DispenserBlock.FACING));
        var entity = source.getLevel().getBlockEntity(position);
        if (!(entity instanceof ChoppingBoardBlockEntity board) || !(entity instanceof CuttingBoardAccess access) || KitchenHooks.occupied(board))
            return original.dispense(source, stack);
        CuttingSession session = access.cooking$cuttingSession();
        if (session != null && stack.is(TagMod.KITCHEN_KNIFE) && session.plan().tool().test(stack))
            return new ChoppingBoardDispenseBehavior(true).dispense(source, stack);
        if (session == null && board.getCurrentCutStack().isEmpty()) {
            var recipe = NativeCuttingSelection.resolve(board, stack);
            if (recipe.orElse(null) instanceof NativeCuttingRecipe)
                return new ChoppingBoardDispenseBehavior(false).dispense(source, stack);
        }
        return original.dispense(source, stack);
    }
}
