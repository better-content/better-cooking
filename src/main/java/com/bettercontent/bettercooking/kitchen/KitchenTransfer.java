package com.bettercontent.bettercooking.kitchen;

import com.bettercontent.bettercooking.recipe.IngredientAccounting;
import com.bettercontent.bettercooking.recipe.PreparationRecipe;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Read-only simulation shared by EMI preview and authoritative server fill. One real batch only. */
public final class KitchenTransfer {
    public record Plan(List<ItemStack> inputs, List<ItemStack> inventory) {
        public Plan { inputs = inputs.stream().map(ItemStack::copy).toList(); inventory = inventory.stream().map(ItemStack::copy).toList(); }
    }
    private KitchenTransfer() {}
    public static Optional<Plan> plan(PreparationRecipe recipe, List<ItemStack> inputs, List<ItemStack> inventory) {
        if (inputs.size() != 9 || inventory.size() != 36) return Optional.empty();
        List<ItemStack> source = new ArrayList<>();
        inputs.forEach(s -> source.add(s.copy())); inventory.forEach(s -> source.add(s.copy()));
        var accounting = IngredientAccounting.plan(recipe.inputs(), new SimpleContainer(source.toArray(ItemStack[]::new)));
        if (accounting.isEmpty()) return Optional.empty();
        int[] consumed = accounting.get().consumed();
        List<ItemStack> target = new ArrayList<>(); for (int i = 0; i < 9; i++) target.add(ItemStack.EMPTY);
        for (int i = 0; i < source.size(); i++) {
            if (consumed[i] == 0) continue;
            ItemStack selected = source.get(i).copy(); selected.setCount(consumed[i]);
            if (recipe.flexible()) {
                // A default batch is one DISTINCT kitchen slot per required unit, never a merged oversized stack.
                for (int unit = 0; unit < consumed[i]; unit++) {
                    int empty = -1;
                    for (int slot = 0; slot < target.size(); slot++) if (target.get(slot).isEmpty()) { empty = slot; break; }
                    if (empty < 0) return Optional.empty();
                    ItemStack one = selected.copy(); one.setCount(1); target.set(empty, one);
                }
            } else if (!insert(target, selected)) return Optional.empty();
            source.get(i).shrink(consumed[i]);
        }
        List<ItemStack> player = new ArrayList<>(); for (int i = 9; i < 45; i++) player.add(source.get(i));
        for (int i = 0; i < 9; i++) if (!insert(player, source.get(i).copy())) return Optional.empty();
        if (recipe.plan(new SimpleContainer(target.stream().map(ItemStack::copy).toArray(ItemStack[]::new))).isEmpty()) return Optional.empty();
        return Optional.of(new Plan(target, player));
    }
    private static boolean insert(List<ItemStack> slots, ItemStack remaining) {
        if (remaining.isEmpty()) return true;
        for (ItemStack stack : slots) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, remaining)) {
                int count = Math.min(Math.min(64, stack.getMaxStackSize()) - stack.getCount(), remaining.getCount());
                if (count > 0) { stack.grow(count); remaining.shrink(count); }
                if (remaining.isEmpty()) return true;
            }
        }
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).isEmpty()) {
            int count = Math.min(Math.min(64, remaining.getMaxStackSize()), remaining.getCount());
            ItemStack stack = remaining.copy(); stack.setCount(count); slots.set(i, stack); remaining.shrink(count);
            if (remaining.isEmpty()) return true;
        }
        return false;
    }
}
