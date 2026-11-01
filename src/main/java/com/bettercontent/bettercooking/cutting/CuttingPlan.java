package com.bettercontent.bettercooking.cutting;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CraftingHelper;

/** Immutable frozen source specification. No probability draw or intake occurs in parsing/preview. */
public final class CuttingPlan {
    public static final int MAX_OUTPUTS = 64, MAX_NBT = 65536, MAX_PACKET = 5 * 1024 * 1024;
    public record Output(ItemStack stack, double chance) {
        public Output {
            Objects.requireNonNull(stack);
            if (stack.isEmpty() || stack.getCount() < 1 || stack.getCount() > 64 || !Double.isFinite(chance) || chance < 0 || chance > 1)
                throw new IllegalArgumentException("Invalid cutting output/chance");
            requireBounded(stack); stack = stack.copy();
        }
        @Override public ItemStack stack() { return stack.copy(); }
    }
    private final Ingredient ingredient, tool;
    private final List<Output> outputs;
    private final int cuts;
    private final ResourceLocation model, source;
    public CuttingPlan(Ingredient ingredient, Ingredient tool, List<Output> outputs, int cuts, ResourceLocation model, ResourceLocation source) {
        this.ingredient = Objects.requireNonNull(ingredient); this.tool = Objects.requireNonNull(tool);
        if (ingredient.isEmpty() || tool.isEmpty() || outputs.isEmpty() || outputs.size() > MAX_OUTPUTS || cuts < 1 || cuts > 64)
            throw new IllegalArgumentException("Invalid cutting ingredient/tool/yield/cuts");
        this.outputs = List.copyOf(outputs); this.cuts = cuts; this.model = model; this.source = source;
        if (json().toString().getBytes(StandardCharsets.UTF_8).length > MAX_PACKET) throw new IllegalArgumentException("Oversized frozen cutting specification");
    }
    public Ingredient ingredient() { return ingredient; }
    public Ingredient tool() { return tool; }
    public int cuts() { return cuts; }
    public ResourceLocation model() { return model; }
    public List<ItemStack> preview() { return outputs.stream().map(Output::stack).toList(); }
    public List<Output> outputs() { return outputs; }
    public long select(RandomSource random) {
        long mask = 0;
        for (int i = 0; i < outputs.size(); i++) {
            Output output = outputs.get(i);
            // Exactly one real Level RNG trial per probabilistic authored entry; deterministic bounds do not draw.
            if (output.chance() == 1 || output.chance() > 0 && random.nextFloat() < output.chance()) mask |= 1L << i;
        }
        return mask;
    }
    public List<ItemStack> selected(long mask) {
        if (outputs.size() < 64 && mask >>> outputs.size() != 0) throw new IllegalArgumentException("Cutting selection exceeds source entries");
        List<ItemStack> selected = new ArrayList<>();
        for (int i = 0; i < outputs.size(); i++) {
            Output output = outputs.get(i); boolean chosen = (mask & 1L << i) != 0;
            if (chosen && output.chance() == 0 || !chosen && output.chance() == 1) throw new IllegalArgumentException("Impossible persisted cutting chance selection");
            if (chosen) selected.add(output.stack());
        }
        return List.copyOf(selected);
    }
    public List<ItemStack> roll(RandomSource random) { return selected(select(random)); }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private record Node(Tag tag, int depth) {}
    public static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new JsonParseException("Invalid cutting resource ID: " + value);
        return id;
    }
    private static void requireWritableUtf(String text) {
        int length = 0;
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i); length += value >= 1 && value <= 127 ? 1 : value <= 2047 ? 2 : 3;
            if (length > 65535) throw new IllegalArgumentException("Cutting NBT string exceeds modified-UTF limit");
        }
    }
    public static void requireBounded(ItemStack stack) {
        if (stack.hasTag()) {
            Deque<Node> walk = new ArrayDeque<>(); walk.add(new Node(stack.getTag(), 0)); int visited = 0;
            while (!walk.isEmpty()) {
                Node node = walk.removeLast();
                if (node.depth() > 32 || ++visited > 4096) throw new IllegalArgumentException("Cutting stack NBT exceeds depth/node bound");
                if (node.tag() instanceof CompoundTag compound) for (String key : compound.getAllKeys()) { requireWritableUtf(key); walk.add(new Node(compound.get(key), node.depth() + 1)); }
                else if (node.tag() instanceof CollectionTag<?> collection) for (Tag child : collection) walk.add(new Node(child, node.depth() + 1));
                else if (node.tag() instanceof StringTag text) requireWritableUtf(text.getAsString());
            }
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.write(stack.save(new CompoundTag()), new DataOutputStream(bytes));
            if (bytes.size() > MAX_NBT) throw new IllegalArgumentException("Cutting stack NBT exceeds 64 KiB");
        } catch (IOException invalidNbt) { throw new IllegalArgumentException("Cannot serialize bounded cutting stack NBT", invalidNbt); }
    }
    public JsonObject json() {
        JsonObject root = new JsonObject(); root.addProperty("type", "better_cooking:cutting");
        root.add("ingredient", ingredient.toJson()); root.add("tool", tool.toJson()); root.addProperty("cut_count", cuts);
        if (model != null) root.addProperty("model_id", model.toString());
        if (source != null) root.addProperty("source_recipe_id", source.toString());
        JsonArray values = new JsonArray();
        for (Output output : outputs) {
            ItemStack stack = output.stack(); JsonObject value = new JsonObject();
            value.addProperty("item", net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
            value.addProperty("count", stack.getCount()); value.addProperty("chance", output.chance());
            if (stack.hasTag()) value.addProperty("nbt", stack.getTag().toString());
            values.add(value);
        }
        root.add("results", values); return root;
    }
    public String fingerprint() {
        return digest(json().toString());
    }
    public static CuttingPlan parse(JsonObject root) {
        for (String key : root.keySet()) if (!Set.of("type", "ingredient", "tool", "results", "cut_count", "model_id", "source_recipe_id", "conditions").contains(key))
            throw new JsonParseException("Unknown cutting field: " + key);
        if (root.has("type") && !GsonHelper.getAsString(root, "type").equals("better_cooking:cutting")) throw new JsonParseException("Wrong cutting type");
        // Standard Forge conditions belong to RecipeManager.apply, NOT an invented recipe predicate.
        if (root.has("conditions") && !root.get("conditions").isJsonArray()) throw new JsonParseException("Forge conditions must be an array");
        Ingredient ingredient = Ingredient.fromJson(root.get("ingredient"), false), tool = Ingredient.fromJson(root.get("tool"), false);
        List<Output> outputs = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(root, "results")) {
            JsonObject value = element.getAsJsonObject();
            for (String key : value.keySet()) if (!Set.of("item", "count", "chance", "nbt").contains(key)) throw new JsonParseException("Unknown cutting result field: " + key);
            outputs.add(new Output(CraftingHelper.getItemStack(value, true, true), GsonHelper.getAsDouble(value, "chance", 1)));
            if (outputs.size() > MAX_OUTPUTS) throw new JsonParseException("Too many cutting outputs");
        }
        return new CuttingPlan(ingredient, tool, outputs, GsonHelper.getAsInt(root, "cut_count"),
                root.has("model_id") ? id(GsonHelper.getAsString(root, "model_id")) : null,
                root.has("source_recipe_id") ? id(GsonHelper.getAsString(root, "source_recipe_id")) : null);
    }
    public void write(FriendlyByteBuf buffer) {
        ingredient.toNetwork(buffer); tool.toNetwork(buffer); buffer.writeVarInt(outputs.size());
        for (Output output : outputs) { buffer.writeItem(output.stack()); buffer.writeDouble(output.chance()); }
        buffer.writeVarInt(cuts); buffer.writeBoolean(model != null); if (model != null) buffer.writeResourceLocation(model);
        buffer.writeBoolean(source != null); if (source != null) buffer.writeResourceLocation(source);
    }
    public static CuttingPlan read(FriendlyByteBuf buffer) {
        int start = buffer.readerIndex();
        Ingredient ingredient = Ingredient.fromNetwork(buffer), tool = Ingredient.fromNetwork(buffer);
        int count = buffer.readVarInt(); if (count < 1 || count > MAX_OUTPUTS) throw new IllegalArgumentException("Invalid cutting output count");
        List<Output> outputs = new ArrayList<>();
        for (int i = 0; i < count; i++) outputs.add(new Output(buffer.readItem(), buffer.readDouble()));
        int cuts = buffer.readVarInt(); ResourceLocation model = buffer.readBoolean() ? buffer.readResourceLocation() : null;
        ResourceLocation source = buffer.readBoolean() ? buffer.readResourceLocation() : null;
        if (buffer.readerIndex() - start > MAX_PACKET) throw new IllegalArgumentException("Oversized cutting recipe payload");
        return new CuttingPlan(ingredient, tool, outputs, cuts, model, source);
    }
}
