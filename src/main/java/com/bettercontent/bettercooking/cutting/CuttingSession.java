package com.bettercontent.bettercooking.cutting;

import com.bettercontent.bettercooking.CookingRuntime;
import com.bettercontent.betterbumblezonecrops.api.CultivarAcquisition;
import com.google.gson.JsonParser;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;

/** Board-owned paid input, frozen chance decisions, and persisted ordered delivery cursor.
 * Ordinary save/load and failed spawn retries are supported; cross-entity crash atomicity is not asserted. */
public final class CuttingSession {
    public static final String KEY = "better_cooking:cutting_transaction";
    private final UUID transaction;
    private final ResourceLocation recipe;
    private final CuttingPlan plan;
    private final ItemStack paid;
    private final String fingerprint;
    private boolean selected, materialized;
    private List<ItemStack> selectedOutputs = List.of(), outputs = List.of();
    private int cursor;
    private long selectionMask;
    private static String identity(ResourceLocation recipe, CuttingPlan plan, ItemStack paid) {
        CuttingPlan.requireBounded(paid);
        return CuttingPlan.digest(recipe + "|" + plan.fingerprint() + "|" + paid.save(new CompoundTag()));
    }
    public CuttingSession(ResourceLocation recipe, CuttingPlan plan, ItemStack paid) {
        this(UUID.randomUUID(), recipe, plan, paid, identity(recipe, plan, paid));
    }
    private CuttingSession(UUID transaction, ResourceLocation recipe, CuttingPlan plan, ItemStack paid, String fingerprint) {
        if (paid.isEmpty() || paid.getCount() != 1) throw new IllegalArgumentException("Native cutting must own exactly one paid input");
        CuttingPlan.requireBounded(paid);
        if (!plan.ingredient().test(paid) || !identity(recipe, plan, paid).equals(fingerprint)) throw new IllegalArgumentException("Cutting transaction source/fingerprint mismatch");
        this.transaction = transaction; this.recipe = recipe; this.plan = plan; this.paid = paid.copy(); this.fingerprint = fingerprint;
    }
    public UUID transaction() { return transaction; }
    public CuttingPlan plan() { return plan; }
    public boolean materialized() { return materialized; }
    public int cursor() { return cursor; }
    public boolean complete() { return materialized && cursor == outputs.size(); }
    public List<ItemStack> outputs() { return outputs.stream().map(ItemStack::copy).toList(); }
    public void validateNative(ItemStack actualPaid, int cuts, int current) {
        if (!ItemStack.matches(paid, actualPaid) || cuts != plan.cuts() || current < 0 || current > cuts || selected && current < cuts)
            throw new IllegalStateException("Native board/paid cutting transaction drift");
    }
    public void select(RandomSource random, Runnable dirty) {
        if (!selected) {
            selectionMask = plan.select(random); selectedOutputs = plan.selected(selectionMask); selected = true;
            // Commit trials before a provider callback can fail; retry/load never rerolls them.
            dirty.run();
        }
    }
    public void materialize(ServerLevel level, Runnable dirty) {
        if (!selected) throw new IllegalStateException("Cutting outcome requested before native ready completion");
        if (materialized) return;
        List<ItemStack> candidates = CookingRuntime.carryCuttingProvenance(paid.copy(), selectedOutputs.stream().map(ItemStack::copy).toList(), level.getGameTime());
        if (candidates.size() != selectedOutputs.size()) throw new IllegalStateException("Cutting provenance callback changed output entry count");
        List<ItemStack> accepted = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            ItemStack before = selectedOutputs.get(i), candidate = candidates.get(i);
            if (candidate.getItem() != before.getItem() || candidate.getCount() != before.getCount()) throw new IllegalStateException("Cutting provenance changed authored output identity/count");
            ItemStack restricted = CultivarAcquisition.restrictNewOutput(level.dimension().location(), candidate);
            if (!restricted.isEmpty()) { CuttingPlan.requireBounded(restricted); accepted.add(restricted.copy()); }
        }
        outputs = List.copyOf(accepted); materialized = true; cursor = 0; dirty.run();
    }
    /** Pure loot query: old input refund before selection; otherwise only committed undelivered results.
     * A detached clone may finish a previously failed provider projection, NEVER another RNG trial. */
    public List<ItemStack> previewDrops(ServerLevel level) {
        if (!selected) return List.of(paid.copy()); // Existing inventory refund is not NEW seed production.
        CuttingSession frozen = this;
        if (!materialized) {
            frozen = load(save());
            frozen.materialize(level, () -> {});
        }
        List<ItemStack> remaining = new ArrayList<>();
        for (int i = frozen.cursor; i < frozen.outputs.size(); i++) {
            ItemStack candidate = CultivarAcquisition.restrictNewOutput(level.dimension().location(), frozen.outputs.get(i).copy());
            if (!candidate.isEmpty()) remaining.add(candidate);
        }
        return List.copyOf(remaining);
    }
    public void deliver(int ordinal, ItemStack candidate, ServerLevel level, BlockPos pos, Runnable dirty) {
        if (!materialized || ordinal < 0 || ordinal >= outputs.size() || !ItemStack.matches(candidate, outputs.get(ordinal)))
            throw new IllegalStateException("Native cutting output cursor/identity drift");
        if (ordinal < cursor) return; // Earlier entries already entered the real world; retry skips them.
        if (ordinal != cursor) throw new IllegalStateException("Out-of-order native cutting delivery");
        ItemEntity entity = new ItemEntity(level, pos.getX() + .5, pos.getY() + .25, pos.getZ() + .5, candidate.copy(), 0, 0, 0);
        entity.setDefaultPickUpDelay();
        if (!level.addFreshEntity(entity)) throw new IllegalStateException("Native cutting output spawn rejected; paid transaction retained");
        cursor++; dirty.run();
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 1); tag.putUUID("transaction", transaction);
        tag.putString("recipe", recipe.toString()); tag.putString("plan", plan.json().toString()); tag.putString("fingerprint", fingerprint);
        tag.put("paid", paid.save(new CompoundTag())); tag.putBoolean("selected", selected); tag.putBoolean("materialized", materialized); tag.putInt("cursor", cursor);
        tag.putLong("selection_mask", selectionMask);
        tag.put("selected_outputs", writeStacks(selectedOutputs)); tag.put("outputs", writeStacks(outputs));
        tag.putString("outputs_fingerprint", CuttingPlan.digest(writeStacks(outputs).toString())); return tag;
    }
    public static CuttingSession load(CompoundTag tag) {
        for (String key : List.of("selected", "materialized")) if (!tag.contains(key, Tag.TAG_BYTE)) throw new IllegalArgumentException("Missing cutting phase field: " + key);
        for (String key : List.of("selected_outputs", "outputs")) if (!tag.contains(key, Tag.TAG_LIST)) throw new IllegalArgumentException("Missing cutting yield field: " + key);
        if (!tag.contains("selection_mask", Tag.TAG_LONG) || !tag.contains("cursor", Tag.TAG_INT)) throw new IllegalArgumentException("Missing cutting cursor/mask");
        if (tag.getInt("version") != 1 || !tag.hasUUID("transaction") || tag.getString("plan").length() > CuttingPlan.MAX_PACKET)
            throw new IllegalArgumentException("Unsupported/corrupt cutting transaction");
        CuttingPlan plan = CuttingPlan.parse(JsonParser.parseString(tag.getString("plan")).getAsJsonObject());
        CuttingSession session = new CuttingSession(tag.getUUID("transaction"), CuttingPlan.id(tag.getString("recipe")), plan,
                ItemStack.of(tag.getCompound("paid")), tag.getString("fingerprint"));
        session.selected = tag.getBoolean("selected"); session.materialized = tag.getBoolean("materialized"); session.cursor = tag.getInt("cursor");
        session.selectionMask = tag.getLong("selection_mask");
        session.selectedOutputs = readStacks(tag.getList("selected_outputs", Tag.TAG_COMPOUND)); session.outputs = readStacks(tag.getList("outputs", Tag.TAG_COMPOUND));
        if (!CuttingPlan.digest(writeStacks(session.outputs).toString()).equals(tag.getString("outputs_fingerprint")))
            throw new IllegalArgumentException("Persisted cutting output fingerprint drift");
        if (session.materialized && !session.selected || !session.selected && !session.selectedOutputs.isEmpty()
                || !session.materialized && (!session.outputs.isEmpty() || session.cursor != 0) || session.cursor < 0 || session.cursor > session.outputs.size())
            throw new IllegalArgumentException("Invalid persisted cutting phase/cursor");
        if (session.selected) {
            List<ItemStack> authored = plan.selected(session.selectionMask);
            if (authored.size() != session.selectedOutputs.size()) throw new IllegalArgumentException("Selected cutting yield count drift");
            for (int i = 0; i < authored.size(); i++) if (!ItemStack.matches(authored.get(i), session.selectedOutputs.get(i)))
                throw new IllegalArgumentException("Selected cutting yield is not authored");
        } else if (session.selectionMask != 0) throw new IllegalArgumentException("Uncommitted cutting selection mask");
        int index = 0;
        for (ItemStack output : session.outputs) {
            while (index < session.selectedOutputs.size() && (output.getItem() != session.selectedOutputs.get(index).getItem() || output.getCount() != session.selectedOutputs.get(index).getCount())) index++;
            if (index++ == session.selectedOutputs.size()) throw new IllegalArgumentException("Persisted cutting materialization changed authored identity/count/order");
        }
        return session;
    }
    private static ListTag writeStacks(List<ItemStack> values) { ListTag tag = new ListTag(); values.forEach(stack -> tag.add(stack.save(new CompoundTag()))); return tag; }
    private static List<ItemStack> readStacks(ListTag values) {
        if (values.size() > CuttingPlan.MAX_OUTPUTS) throw new IllegalArgumentException("Oversized persisted cutting yield");
        List<ItemStack> stacks = new ArrayList<>();
        for (Tag value : values) { ItemStack stack = ItemStack.of((CompoundTag) value); if (stack.isEmpty() || stack.getCount() > 64) throw new IllegalArgumentException("Invalid persisted cutting output"); CuttingPlan.requireBounded(stack); stacks.add(stack); }
        return List.copyOf(stacks);
    }
}
