package com.bettercontent.bettercooking.placed;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.registries.BuiltInRegistries;
import java.io.*;
import java.util.*;

/** Dimension-local original whole-item provenance. No nutrition balance, ownership or new clock. */
public final class PlacedProvenanceData extends SavedData {
    private static final int MAX_ENTRIES = 4096, MAX_ITEM_BYTES = 65536, MAX_TOTAL_BYTES = 16 * 1024 * 1024;
    private record Entry(String block, int remaining, ItemStack original, int bytes) {}
    private final Map<Long, Entry> entries = new HashMap<>();
    static PlacedProvenanceData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PlacedProvenanceData::load, PlacedProvenanceData::new,
                "better_cooking_placed_provenance");
    }
    private static String id(BlockState state) {
        return Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(state.getBlock()), "Unregistered placed block").toString();
    }
    private static int itemBytes(CompoundTag tag) {
        record Node(Tag tag, int depth) {}
        ArrayDeque<Node> pending = new ArrayDeque<>(); pending.push(new Node(tag, 0));
        int nodes = 0;
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (++nodes > 4096 || node.depth() > 32) throw new IllegalArgumentException("Placed original NBT exceeds structural bound");
            if (node.tag() instanceof CompoundTag compound) {
                if (compound.size() > 4096) throw new IllegalArgumentException("Placed original compound too wide");
                for (String key : compound.getAllKeys()) pending.push(new Node(compound.get(key), node.depth() + 1));
            } else if (node.tag() instanceof ListTag list) {
                if (list.size() > 4096) throw new IllegalArgumentException("Placed original list too wide");
                for (Tag child : list) pending.push(new Node(child, node.depth() + 1));
            }
        }
        final int[] bytes = {0};
        try {
            NbtIo.write(tag, new DataOutputStream(new OutputStream() {
                @Override public void write(int value) throws IOException { add(1); }
                @Override public void write(byte[] value, int offset, int length) throws IOException { add(length); }
                private void add(int length) throws IOException {
                    if ((long) bytes[0] + length > MAX_ITEM_BYTES) throw new IOException("Placed original exceeds byte bound");
                    bytes[0] += length;
                }
            }));
        } catch (IOException failure) { throw new IllegalArgumentException("Invalid/oversized placed original NBT", failure); }
        return bytes[0] + 256; // bounded row/block/position overhead in total budget
    }
    private void admit(long pos, Entry entry) {
        if (!entries.containsKey(pos) && entries.size() >= MAX_ENTRIES) throw new IllegalStateException("Placed provenance entry capacity exceeded");
        long bytes = entry.bytes();
        for (var row : entries.entrySet()) if (row.getKey() != pos) bytes += row.getValue().bytes();
        if (bytes > MAX_TOTAL_BYTES) throw new IllegalStateException("Placed provenance total payload bound exceeded");
        entries.put(pos, entry);
    }
    public void put(BlockPos pos, BlockState state, ItemStack original, int remaining) {
        PortionProgress.validate(remaining);
        if (original.isEmpty()) throw new IllegalArgumentException("Empty placed original");
        // Reject oversized/deep metadata before copying, including copied input stacks.
        if (original.hasTag()) itemBytes(original.getTag());
        CompoundTag encoded = original.save(new CompoundTag());
        int bytes = itemBytes(encoded);
        ItemStack whole = original.copy(); whole.setCount(1);
        admit(pos.asLong(), new Entry(id(state), remaining, whole, bytes));
        setDirty();
    }
    /** Pure lookup. A corrupt/stale tracked record refuses visibly rather than laundering fresh food. */
    public Optional<ItemStack> original(BlockPos pos, BlockState state, int remaining) {
        Entry entry = entries.get(pos.asLong());
        if (entry == null) return Optional.empty();
        if (!entry.block().equals(id(state)) || entry.remaining() != remaining)
            throw new IllegalStateException("Placed provenance/native state mismatch at " + pos);
        return Optional.of(entry.original().copy());
    }
    public void complete(BlockPos pos, BlockState before, int remainingBefore) {
        Entry entry = entries.get(pos.asLong());
        if (entry == null) return; // Final native removal may already have cleared it.
        if (!entry.block().equals(id(before))) throw new IllegalStateException("Placed block identity mismatch at " + pos);
        int next = PortionProgress.complete(entry.remaining(), remainingBefore);
        if (next == entry.remaining()) return;
        if (next == 0) entries.remove(pos.asLong());
        else entries.put(pos.asLong(), new Entry(entry.block(), next, entry.original(), entry.bytes()));
        setDirty();
    }
    public void clear(BlockPos pos) { if (entries.remove(pos.asLong()) != null) setDirty(); }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("v", 1); ListTag list = new ListTag();
        entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(row -> {
            Entry entry = row.getValue(); CompoundTag value = new CompoundTag();
            value.putLong("pos", row.getKey()); value.putString("block", entry.block());
            value.putInt("remaining", entry.remaining()); value.put("original", entry.original().save(new CompoundTag())); list.add(value);
        });
        tag.put("entries", list); return tag;
    }
    public static PlacedProvenanceData load(CompoundTag tag) {
        if (tag.getInt("v") != 1 || !tag.contains("entries", Tag.TAG_LIST))
            throw new IllegalArgumentException("Unknown/corrupt placed provenance save schema");
        ListTag list = (ListTag) tag.get("entries");
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Invalid placed provenance entry-list type");
        if (list.size() > MAX_ENTRIES) throw new IllegalArgumentException("Placed provenance save exceeds entry bound");
        PlacedProvenanceData data = new PlacedProvenanceData();
        for (Tag raw : list) {
            CompoundTag row = (CompoundTag) raw;
            if (!row.contains("pos", Tag.TAG_LONG) || !row.contains("block", Tag.TAG_STRING)
                    || !row.contains("remaining", Tag.TAG_INT) || !row.contains("original", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Malformed placed provenance row");
            ResourceLocation block = ResourceLocation.tryParse(row.getString("block"));
            if (block == null || !BuiltInRegistries.BLOCK.containsKey(block)) throw new IllegalArgumentException("Unknown placed provenance block");
            PortionProgress.validate(row.getInt("remaining"));
            CompoundTag encoded = row.getCompound("original"); int bytes = itemBytes(encoded);
            ItemStack original = ItemStack.of(encoded);
            if (original.isEmpty() || original.getCount() != 1) throw new IllegalArgumentException("Invalid original whole food stack");
            long pos = row.getLong("pos");
            if (data.entries.containsKey(pos)) throw new IllegalArgumentException("Duplicate placed provenance position");
            data.admit(pos, new Entry(block.toString(), row.getInt("remaining"), original, bytes));
        }
        return data;
    }
}
