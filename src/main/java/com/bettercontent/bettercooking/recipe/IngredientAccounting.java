package com.bettercontent.bettercooking.recipe;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Deterministic integral max-flow avoids greedy tag/alternative overconsumption. */
public final class IngredientAccounting {
    private IngredientAccounting() {}
    public record Plan(List<ItemStack> snapshot, int[] consumed) {
        public Plan {
            snapshot = snapshot.stream().map(ItemStack::copy).toList(); consumed = consumed.clone();
        }
        @Override public int[] consumed() { return consumed.clone(); }
        @Override public List<ItemStack> snapshot() { return snapshot.stream().map(ItemStack::copy).toList(); }
        public Optional<List<ItemStack>> debit(Container inventory) {
            if (inventory.getContainerSize() != snapshot.size()) return Optional.empty();
            for (int slot = 0; slot < snapshot.size(); slot++) {
                ItemStack actual = inventory.getItem(slot), expected = snapshot.get(slot);
                if (actual.getCount() != expected.getCount() || !ItemStack.isSameItemSameTags(actual, expected)) return Optional.empty();
            }
            List<ItemStack> paid = new ArrayList<>();
            for (int slot = 0; slot < consumed.length; slot++) if (consumed[slot] > 0)
                paid.add(inventory.removeItem(slot, consumed[slot]));
            inventory.setChanged();
            return Optional.of(paid.stream().map(ItemStack::copy).toList());
        }
    }
    public static Optional<Plan> plan(PreparationRecipe recipe, Container inventory) {
        return recipe.flexible() ? flexiblePlan(recipe.inputs(), inventory) : plan(recipe.inputs(), inventory);
    }
    public static Optional<List<ItemStack>> debit(PreparationRecipe recipe, Container inventory) {
        return plan(recipe, inventory).flatMap(plan -> plan.debit(inventory));
    }
    private static Optional<Plan> flexiblePlan(List<PreparationRecipe.Input> inputs, Container inventory) {
        int slots = inventory.getContainerSize();
        if (inputs.isEmpty() || inputs.size() > 9 || slots < 1 || slots > 64) return Optional.empty();
        List<ItemStack> snapshot = new ArrayList<>();
        List<Integer> occupied = new ArrayList<>();
        int[] consumed = new int[slots];
        for (int slot = 0; slot < slots; slot++) {
            ItemStack actual = inventory.getItem(slot); snapshot.add(actual.copy());
            if (!actual.isEmpty()) { occupied.add(slot); consumed[slot] = 1; }
        }
        if (occupied.isEmpty() || occupied.size() > FlexibleFormulation.MAX_UNITS) return Optional.empty();
        boolean[][] matches = new boolean[inputs.size()][occupied.size()];
        for (int group = 0; group < inputs.size(); group++) {
            if (inputs.get(group).count() != 1) return Optional.empty();
            for (int unit = 0; unit < occupied.size(); unit++) matches[group][unit] = inputs.get(group).ingredient().test(snapshot.get(occupied.get(unit)));
        }
        if (!FlexibleFormulation.covers(matches)) return Optional.empty();
        return Optional.of(new Plan(snapshot, consumed));
    }
    public static Optional<List<ItemStack>> debit(List<PreparationRecipe.Input> inputs, Container inventory) {
        return plan(inputs, inventory).flatMap(plan -> plan.debit(inventory));
    }
    public static Optional<Plan> plan(List<PreparationRecipe.Input> inputs, Container inventory) {
        if (inputs.isEmpty() || inputs.size() > 9 || inventory.getContainerSize() < 1) return Optional.empty();
        int slots = inventory.getContainerSize(), groups = inputs.size();
        if (slots > 64) throw new IllegalArgumentException("Kitchen inventory too large");
        int source = 0, firstGroup = 1, firstSlot = firstGroup + groups, sink = firstSlot + slots;
        int[][] capacity = new int[sink + 1][sink + 1];
        int required = 0;
        for (int group = 0; group < groups; group++) {
            PreparationRecipe.Input input = inputs.get(group);
            capacity[source][firstGroup + group] = input.count(); required += input.count();
            for (int slot = 0; slot < slots; slot++) if (input.ingredient().test(inventory.getItem(slot)))
                capacity[firstGroup + group][firstSlot + slot] = input.count();
        }
        List<ItemStack> snapshot = new ArrayList<>();
        for (int slot = 0; slot < slots; slot++) {
            snapshot.add(inventory.getItem(slot).copy());
            capacity[firstSlot + slot][sink] = inventory.getItem(slot).getCount();
        }
        int[][] residual = Arrays.stream(capacity).map(int[]::clone).toArray(int[][]::new);
        int flow = 0;
        while (flow < required) {
            int[] parent = new int[sink + 1], queue = new int[sink + 1]; Arrays.fill(parent, -1);
            int read = 0, write = 0; queue[write++] = source; parent[source] = source;
            while (read < write && parent[sink] < 0) {
                int node = queue[read++];
                for (int next = 0; next <= sink; next++) if (parent[next] < 0 && residual[node][next] > 0) {
                    parent[next] = node; queue[write++] = next;
                }
            }
            if (parent[sink] < 0) return Optional.empty();
            int amount = required - flow;
            for (int node = sink; node != source; node = parent[node]) amount = Math.min(amount, residual[parent[node]][node]);
            for (int node = sink; node != source; node = parent[node]) {
                residual[parent[node]][node] -= amount; residual[node][parent[node]] += amount;
            }
            flow += amount;
        }
        int[] consumed = new int[slots];
        for (int slot = 0; slot < slots; slot++) consumed[slot] = capacity[firstSlot + slot][sink] - residual[firstSlot + slot][sink];
        return Optional.of(new Plan(snapshot, consumed));
    }
}
