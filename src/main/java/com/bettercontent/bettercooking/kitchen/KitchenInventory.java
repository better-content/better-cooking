package com.bettercontent.bettercooking.kitchen;

import com.bettercontent.bettercooking.CookingItems;
import com.bettercontent.bettercooking.CookingRuntime;
import com.bettercontent.bettercooking.policy.PreparationState;
import com.bettercontent.bettercooking.recipe.IngredientAccounting;
import com.bettercontent.bettercooking.recipe.PreparationRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.ItemStackHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Attached to the native block entity; only completed repetitions survive interruption. */
public final class KitchenInventory extends ItemStackHandler implements Container {
    public static final String RESUME = "better_cooking:kitchen_resume";
    private final BlockEntity station;
    private ResourceLocation recipeId;
    private String signature = "";
    private PreparationState progress = new PreparationState();
    private List<ItemStack> paid = new ArrayList<>();
    private UUID actor;
    private long lastPacket = Long.MIN_VALUE;
    private long heartbeat = Long.MIN_VALUE;
    public KitchenInventory(BlockEntity station) { super(10); this.station = station; }
    public BlockPos pos() { return station.getBlockPos(); }
    public boolean occupied() { return !isEmpty() || !paid.isEmpty(); }
    public boolean preparing() { return !paid.isEmpty(); }
    public ResourceLocation recipeId() { return recipeId; }
    public PreparationState progress() { return progress; }
    public PreparationRecipe recipe() {
        if (recipeId == null || station.getLevel() == null) return null;
        return station.getLevel().getRecipeManager().byKey(recipeId).filter(PreparationRecipe.class::isInstance).map(PreparationRecipe.class::cast).orElse(null);
    }
    public String surface() { return net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(station.getBlockState().getBlock()).getNamespace().equals("ubesdelight") ? "baking_mat" : "board"; }
    public String fingerprint(PreparationRecipe recipe) {
        return recipe.fingerprint();
    }
    private boolean current(PreparationRecipe recipe) {
        return recipe != null && signature.equals(fingerprint(recipe)) && (!preparing()
            || recipe.acceptsPaid(paid));
    }
    public boolean accepts(PreparationRecipe recipe) {
        return surface().equals(recipe.surface());
    }
    public boolean select(PreparationRecipe recipe) {
        if (preparing() || !getStackInSlot(9).isEmpty() || !accepts(recipe)) return false;
        recipeId = recipe.getId(); progress = new PreparationState(); changed(); return true;
    }
    public boolean canAccess(ServerPlayer player) {
        double reach = player.getAttributeValue(net.minecraftforge.common.ForgeMod.BLOCK_REACH.get());
        return !(player instanceof FakePlayer) && player.isAlive() && !player.isSpectator() && player.connection != null && !player.hasDisconnected()
            && station.getLevel() == player.level() && !station.isRemoved() && player.level().getBlockEntity(pos()) == station
            && player.getEyePosition().distanceToSqr(pos().getCenter()) <= reach * reach && !KitchenHooks.nativeOccupied(station);
    }
    public boolean valid(ServerPlayer player) { return canAccess(player) && player.containerMenu instanceof KitchenMenu menu && menu.kitchen() == this; }
    public boolean toolValid(ServerPlayer player, PreparationRecipe recipe) {
        if (progress.finished(recipe.stages())) return true;
        String tool = recipe.stages().get(progress.stage()).toolTag();
        if (tool == null || tool.isBlank()) return true;
        ResourceLocation id = ResourceLocation.tryParse(tool.startsWith("#") ? tool.substring(1) : tool);
        return id != null && (player.getMainHandItem().is(TagKey.create(Registries.ITEM, id)) || player.getOffhandItem().is(TagKey.create(Registries.ITEM, id)));
    }
    public boolean begin(ServerPlayer player) {
        PreparationRecipe recipe = recipe();
        if (!valid(player) || recipe == null || !accepts(recipe) || preparing() || !getStackInSlot(9).isEmpty() || !toolValid(player, recipe)) return false;
        // The container exposes nine inputs to accounting; the output is never an ingredient.
        var debit = IngredientAccounting.debit(recipe, this);
        if (debit.isEmpty()) return false;
        paid = new ArrayList<>(debit.get()); signature = fingerprint(recipe); progress = new PreparationState(); actor = null; changed(); return true;
    }
    public void pulse(ServerPlayer player, int window, ResourceLocation expected) {
        long now = player.serverLevel().getGameTime();
        if (lastPacket == now) return;
        PreparationRecipe recipe = recipe();
        if (!valid(player) || player.containerMenu.containerId != window || !current(recipe) || !recipe.getId().equals(expected)
                || !preparing() || !toolValid(player, recipe)) return;
        if (actor != null && !actor.equals(player.getUUID()) && progress.active() && now >= heartbeat && now - heartbeat <= 6) return;
        lastPacket = now;
        actor = player.getUUID(); heartbeat = now; progress.pulse(actor, now, true, recipe.stages());
    }
    public void tick(ServerPlayer player) {
        PreparationRecipe recipe = recipe();
        if (!current(recipe)) { interrupt(); return; }
        if (player.getUUID().equals(actor)) {
            boolean moved = progress.tick(actor, player.serverLevel().getGameTime(), valid(player) && toolValid(player, recipe), recipe.stages());
            if (!progress.active()) actor = null;
            if (moved) changed();
        }
        if (progress.finished(recipe.stages()) && preparing() && getStackInSlot(9).isEmpty() && valid(player)) {
            List<ItemStack> originals = paid.stream().map(ItemStack::copy).toList();
            ItemStack result = CookingRuntime.finishPreparation(recipe, originals, player);
            if (!result.isEmpty()) {
                List<ItemStack> remainders = CookingRuntime.remainders(originals);
                // Commit output and retire paid provenance before exposing any remainder.
                paid.clear(); actor = null; setStackInSlot(9, result); changed();
                for (ItemStack remainder : remainders) {
                    ItemStack remaining = remainder.copy();
                    player.getInventory().add(remaining);
                    if (!remaining.isEmpty()) Containers.dropItemStack(player.serverLevel(), pos().getX() + .5, pos().getY() + .5, pos().getZ() + .5, remaining);
                }
            }
        }
    }
    public void interrupt() { progress.interrupt(); actor = null; heartbeat = Long.MIN_VALUE; }
    public void changed() { station.setChanged(); }
    @Override protected void onContentsChanged(int slot) { changed(); }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return slot < 9 && !preparing(); }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return slot < 9 && preparing() ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate); }
    @Override public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        if (recipeId != null) tag.putString("Recipe", recipeId.toString());
        tag.putString("RecipeSignature", signature);
        tag.putInt("Stage", progress.stage()); tag.putInt("Repetition", progress.repetition());
        ListTag list = new ListTag(); for (ItemStack stack : paid) list.add(stack.save(new CompoundTag())); tag.put("Paid", list);
        return tag;
    }
    @Override public void deserializeNBT(CompoundTag tag) {
        super.deserializeNBT(tag); recipeId = ResourceLocation.tryParse(tag.getString("Recipe")); signature = tag.getString("RecipeSignature");
        progress = new PreparationState(Math.max(0, tag.getInt("Stage")), Math.max(0, tag.getInt("Repetition")));
        paid = new ArrayList<>(); for (Tag entry : tag.getList("Paid", Tag.TAG_COMPOUND)) { ItemStack stack = ItemStack.of((CompoundTag) entry); if (!stack.isEmpty()) paid.add(stack); }
        interrupt();
    }
    public boolean restore(ItemStack bundle) {
        if (occupied() || !bundle.is(CookingItems.PREPARED_BATCH.get()) || bundle.getTag() == null || !bundle.getTag().contains(RESUME, Tag.TAG_COMPOUND)) return false;
        CompoundTag state = bundle.getTag().getCompound(RESUME);
        ResourceLocation id = ResourceLocation.tryParse(state.getString("Recipe"));
        if (id == null || station.getLevel() == null) return false;
        var recipe = station.getLevel().getRecipeManager().byKey(id).filter(PreparationRecipe.class::isInstance).map(PreparationRecipe.class::cast).orElse(null);
        if (recipe == null || !accepts(recipe) || !savedCurrent(recipe, state)) return false;
        int stage = state.getInt("Stage"), repeat = state.getInt("Repetition");
        if (stage < 0 || stage > recipe.stages().size() || repeat < 0 || (stage < recipe.stages().size() && repeat >= recipe.stages().get(stage).repetitions())) return false;
        deserializeNBT(state); changed(); return true;
    }
    private boolean savedCurrent(PreparationRecipe recipe, CompoundTag state) {
        if (!fingerprint(recipe).equals(state.getString("RecipeSignature"))) return false;
        List<ItemStack> originals = new ArrayList<>();
        for (Tag entry : state.getList("Paid", Tag.TAG_COMPOUND)) originals.add(ItemStack.of((CompoundTag) entry));
        return recipe.acceptsPaid(originals);
    }
    /** A bundle made obsolete by a datapack reload can always recover its originals, never a new output. */
    public boolean refundObsolete(ItemStack bundle, ServerPlayer player) {
        if (!canAccess(player) || !bundle.is(CookingItems.PREPARED_BATCH.get()) || bundle.getTag() == null || !bundle.getTag().contains(RESUME, Tag.TAG_COMPOUND)) return false;
        CompoundTag saved = bundle.getTag().getCompound(RESUME);
        ResourceLocation id = ResourceLocation.tryParse(saved.getString("Recipe"));
        var candidate = id == null ? null : player.serverLevel().getRecipeManager().byKey(id).filter(PreparationRecipe.class::isInstance).map(PreparationRecipe.class::cast).orElse(null);
        if (candidate != null && savedCurrent(candidate, saved)) return false;
        List<ItemStack> originals = new ArrayList<>();
        for (Tag entry : saved.getList("Paid", Tag.TAG_COMPOUND)) { ItemStack stack = ItemStack.of((CompoundTag) entry); if (!stack.isEmpty()) originals.add(stack); }
        if (originals.isEmpty() || originals.size() > 64) return false;
        bundle.shrink(1);
        for (ItemStack original : originals) {
            player.getInventory().add(original);
            if (!original.isEmpty()) Containers.dropItemStack(player.serverLevel(), pos().getX() + .5, pos().getY() + .5, pos().getZ() + .5, original);
        }
        return true;
    }
    /** Called once only after a noncancelled break. Clear before emitting any drops. */
    public void recover() {
        if (station.getLevel() == null || station.getLevel().isClientSide) return;
        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < 10; i++) { drops.add(getStackInSlot(i).copy()); setStackInSlot(i, ItemStack.EMPTY); }
        if (preparing()) {
            if (current(recipe())) {
                ItemStack bundle = new ItemStack(CookingItems.PREPARED_BATCH.get());
                bundle.getOrCreateTag().put(RESUME, serializeNBT()); drops.add(bundle);
            } else {
                // Removed/changed datapack recipes cannot strand or convert paid materials.
                for (ItemStack stack : paid) drops.add(stack.copy());
            }
        }
        paid.clear(); recipeId = null; signature = ""; progress = new PreparationState(); interrupt(); changed();
        for (ItemStack stack : drops) if (!stack.isEmpty()) Containers.dropItemStack(station.getLevel(), pos().getX() + .5, pos().getY() + .5, pos().getZ() + .5, stack);
    }
    @Override public int getContainerSize() { return 9; }
    @Override public boolean isEmpty() { for (int i = 0; i < 10; i++) if (!getStackInSlot(i).isEmpty()) return false; return true; }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < 9 ? getStackInSlot(slot) : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) { return extractItem(slot, amount, false); }
    @Override public ItemStack removeItemNoUpdate(int slot) { ItemStack result = getItem(slot); setItem(slot, ItemStack.EMPTY); return result; }
    @Override public void setItem(int slot, ItemStack stack) { if (slot >= 0 && slot < 9) setStackInSlot(slot, stack); }
    @Override public void setChanged() { changed(); }
    @Override public boolean stillValid(Player player) {
        double reach = player.getAttributeValue(net.minecraftforge.common.ForgeMod.BLOCK_REACH.get());
        return station.getLevel() == player.level() && !station.isRemoved() && player.level().getBlockEntity(pos()) == station
            && player.getEyePosition().distanceToSqr(pos().getCenter()) <= reach * reach;
    }
    @Override public void clearContent() { for (int i = 0; i < 10; i++) setStackInSlot(i, ItemStack.EMPTY); }
}
