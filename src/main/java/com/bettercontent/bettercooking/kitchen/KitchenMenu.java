package com.bettercontent.bettercooking.kitchen;

import com.bettercontent.bettercooking.recipe.CookingRecipes;
import com.bettercontent.bettercooking.recipe.PreparationRecipe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import java.util.Comparator;
import java.util.List;

public final class KitchenMenu extends AbstractContainerMenu {
    private final KitchenInventory kitchen;
    private final ItemStackHandler items;
    private final List<PreparationRecipe> recipes;
    private final Player viewer;
    private final List<com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe> cuts;
    private final int[] data = new int[6]; // recipe index, stage, repeat, elapsed, paid
    public KitchenMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, null, buf.readUtf());
    }
    public KitchenMenu(int id, Inventory inventory, KitchenInventory kitchen, String surface) {
        super(KitchenContent.MENU.get(), id);
        this.kitchen = kitchen; this.items = kitchen == null ? new ItemStackHandler(10) : kitchen;
        recipes = inventory.player.level().getRecipeManager().getAllRecipesFor(CookingRecipes.PREPARATION_TYPE.get()).stream()
            .filter(r -> surface.equals(r.surface()))
            .sorted(Comparator.comparing(r -> r.getId().toString())).toList();
        viewer = inventory.player;
        cuts = surface.equals("board") ? com.bettercontent.bettercooking.cutting.NativeCuttingSelection.recipes(viewer.level()) : List.of();
        data[0] = -1; data[5] = -1;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) addSlot(new SlotItemHandler(items, row * 3 + col, 8 + col * 18, 24 + row * 18) {
            @Override public boolean mayPlace(ItemStack stack) { return super.mayPlace(stack) && (KitchenMenu.this.kitchen == null || !KitchenMenu.this.kitchen.preparing()); }
            @Override public boolean mayPickup(Player player) { return KitchenMenu.this.kitchen == null || !KitchenMenu.this.kitchen.preparing(); }
        });
        addSlot(new SlotItemHandler(items, 9, 144, 42) { @Override public boolean mayPlace(ItemStack stack) { return false; } });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 126 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 184));
        for (int i = 0; i < data.length; i++) { final int index = i; addDataSlot(new DataSlot() {
            @Override public int get() { return data[index]; }
            @Override public void set(int value) { data[index] = value; }
        }); }
    }
    public java.util.Optional<KitchenTransfer.Plan> transferPlan(PreparationRecipe recipe) {
        if ((kitchen != null ? kitchen.preparing() : preparing()) || !items.getStackInSlot(9).isEmpty()
                || recipes.stream().noneMatch(r -> r.getId().equals(recipe.getId()))) return java.util.Optional.empty();
        return KitchenTransfer.plan(recipe, slots.subList(0, 9).stream().map(Slot::getItem).toList(), slots.subList(10, 46).stream().map(Slot::getItem).toList());
    }
    private long lastFill = Long.MIN_VALUE;
    public boolean transfer(ServerPlayer player, ResourceLocation recipeId, int window) {
        long now = player.serverLevel().getGameTime();
        if (window != containerId || kitchen == null || !kitchen.valid(player) || lastFill == now) return false;
        PreparationRecipe recipe = recipes.stream().filter(r -> r.getId().equals(recipeId)).findFirst().orElse(null);
        if (recipe == null || player.serverLevel().getRecipeManager().byKey(recipeId).orElse(null) != recipe) return false;
        lastFill = now;
        var plan = transferPlan(recipe);
        if (plan.isEmpty() || !kitchen.select(recipe)) return false;
        // The read-only plan and commit execute in one server task; no partial debit or client-provided stacks.
        for (int i = 0; i < 9; i++) items.setStackInSlot(i, plan.get().inputs().get(i).copy());
        for (int i = 0; i < 36; i++) slots.get(10 + i).set(plan.get().inventory().get(i).copy());
        player.getInventory().setChanged(); broadcastChanges(); return true;
    }
    public boolean chooseNativeCut(ServerPlayer player, ResourceLocation recipeId, int window) {
        if (window != containerId || player.containerMenu != this || kitchen == null || !kitchen.valid(player) || kitchen.occupied()) return false;
        var entity = player.serverLevel().getBlockEntity(kitchen.pos());
        if (!(entity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity board)) return false;
        var recipe = cuts.stream().filter(r -> r.getId().equals(recipeId)).findFirst().orElse(null);
        if (recipe == null || !com.bettercontent.bettercooking.cutting.NativeCuttingSelection.choose(board, recipe, getCarried())) return false;
        broadcastChanges(); return true;
    }
    public boolean supportsNativeCuts() { return !cuts.isEmpty(); }
    public com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe selectedNativeCut() {
        return data[5] >= 0 && data[5] < cuts.size() ? cuts.get(data[5]) : null;
    }
    public KitchenInventory kitchen() { return kitchen; }
    public PreparationRecipe selected() { return data[0] >= 0 && data[0] < recipes.size() ? recipes.get(data[0]) : null; }
    public int stage() { return data[1]; }
    public int repetition() { return data[2]; }
    public int elapsed() { return data[3]; }
    public boolean preparing() { return data[4] != 0; }
    @Override public void broadcastChanges() {
        if (kitchen != null) {
            data[0] = -1;
            for (int i = 0; i < recipes.size(); i++) if (recipes.get(i).getId().equals(kitchen.recipeId())) { data[0] = i; break; }
            data[1] = kitchen.progress().stage(); data[2] = kitchen.progress().repetition();
            data[3] = kitchen.progress().elapsed(kitchenLevelTime()); data[4] = kitchen.preparing() ? 1 : 0;
            data[5] = -1;
            var entity = viewer.level().getBlockEntity(kitchen.pos());
            if (entity instanceof com.bettercontent.bettercooking.cutting.CuttingBoardAccess access && access.cooking$cuttingChoice() != null)
                for (int i = 0; i < cuts.size(); i++) if (cuts.get(i).getId().equals(access.cooking$cuttingChoice().recipe())) { data[5] = i; break; }
        }
        super.broadcastChanges();
    }
    private long kitchenLevelTime() { return playerTime; }
    private long playerTime;
    public void serverTick(ServerPlayer player) { playerTime = player.serverLevel().getGameTime(); kitchen.tick(player); }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer server) || kitchen == null || !kitchen.valid(server)) return false;
        if (id == 2 && !cuts.isEmpty()) {
            for (int offset = 1; offset <= cuts.size(); offset++) {
                var recipe = cuts.get((Math.max(-1, data[5]) + offset) % cuts.size());
                if (recipe.matches(new net.minecraft.world.SimpleContainer(getCarried()), player.level()) && chooseNativeCut(server, recipe.getId(), containerId)) return true;
            }
            return false;
        }
        if (id == 1) return kitchen.begin(server);
        if (id == 0 && !kitchen.preparing() && !recipes.isEmpty()) {
            // Pick a matching current-ingredient recipe first, then cycle matching alternatives.
            int start = Math.max(-1, data[0]);
            for (int offset = 1; offset <= recipes.size(); offset++) {
                PreparationRecipe recipe = recipes.get((start + offset) % recipes.size());
                if (recipe.matches(kitchen, player.level()) && kitchen.select(recipe)) return true;
            }
        }
        return false;
    }
    @Override public boolean stillValid(Player player) { return kitchen == null || kitchen.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index); if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack current = slot.getItem(), original = current.copy();
        if (index < 10) { if (!moveItemStackTo(current, 10, 46, true)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(current, 0, 9, false)) return ItemStack.EMPTY;
        if (current.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, current); return original;
    }
}
