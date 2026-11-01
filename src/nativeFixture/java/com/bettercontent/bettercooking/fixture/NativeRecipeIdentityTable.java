package com.bettercontent.bettercooking.fixture;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Nonshipping, invocation-local raw observations only. No registry/bootstrap or approval capability. */
final class NativeRecipeIdentityTable<T, K> {
    static final int MAX_RAW_BYTES = 64 * 1024 * 1024;
    static final int MAX_DEPTH = 64;
    static final int MAX_NODES = 2_000_000;
    private static final Pattern JSON_NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private static final Pattern OWNER_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    record Probe<T, K>(String api, Function<T, K> ownerKey, BiPredicate<T, K> sameObject) {
        Probe {
            Objects.requireNonNull(api);
            Objects.requireNonNull(ownerKey);
            Objects.requireNonNull(sameObject);
        }
    }

    private final IdentityHashMap<T, JsonObject> byIdentity = new IdentityHashMap<>();
    private final JsonArray nodes = new JsonArray();
    private final String prefix;
    private final String field;
    private final List<Probe<T, K>> probes;
    private final Function<K, String> keyText;
    private String previousRecipeId;

    NativeRecipeIdentityTable(String prefix, List<Probe<T, K>> probes, Function<K, String> keyText) {
        this.prefix = Objects.requireNonNull(prefix);
        this.probes = List.copyOf(probes);
        this.keyText = Objects.requireNonNull(keyText);
        List<String> apis = this.probes.stream().map(Probe::api).toList();
        if (prefix.equals("t") && apis.equals(List.of("builtin", "forge"))) field = "native_recipe_type";
        else if (prefix.equals("s") && apis.equals(List.of("forge"))) field = "native_serializer";
        else throw new IllegalArgumentException("Only the fixed type/serializer observation API sets are supported");
    }

    /** Called once per sorted recipe, including missing getters. Callback failures are deliberately terminal. */
    JsonObject observe(String recipeId, T actualObject, JsonArray gaps) {
        Objects.requireNonNull(recipeId);
        Objects.requireNonNull(gaps);
        if (previousRecipeId != null && previousRecipeId.compareTo(recipeId) >= 0)
            throw new IllegalArgumentException("Recipe IDs must be unique and strictly sorted");
        previousRecipeId = recipeId;
        JsonObject observation = new JsonObject();
        if (actualObject == null) {
            observation.addProperty("getter", "returned_null");
            observation.addProperty("state", "missing");
            observation.add("ref", JsonNull.INSTANCE);
            gap(gaps, recipeId, field, "Actual getter returned null; no object and no registry lookups");
            return observation;
        }
        JsonObject node = byIdentity.get(actualObject);
        if (node == null) {
            node = sample(recipeId, actualObject);
            byIdentity.put(actualObject, node);
            nodes.add(node);
        }
        observation.addProperty("getter", "returned_object");
        observation.addProperty("state", node.get("state").getAsString());
        observation.add("ref", node.get("ref"));
        appendGaps(recipeId, node, gaps);
        return observation;
    }

    private JsonObject sample(String recipeId, T actualObject) {
        JsonObject lookups = new JsonObject(), active = new JsonObject();
        boolean allAbsent = true, allKeys = true, agree = true;
        String commonKey = null;
        for (Probe<T, K> probe : probes) {
            K ownerKey = probe.ownerKey().apply(actualObject);
            String text = ownerKey == null ? null : Objects.requireNonNull(keyText.apply(ownerKey));
            if (text != null && !OWNER_KEY.matcher(text).matches())
                throw new IllegalArgumentException("Actual registry returned an invalid owner key");
            JsonObject lookup = new JsonObject();
            lookup.addProperty("outcome", ownerKey == null ? "absent" : "key");
            lookup.add("key", text == null ? JsonNull.INSTANCE : new JsonPrimitive(text));
            lookup.add("error", JsonNull.INSTANCE);
            lookups.add(probe.api(), lookup);
            // Roundtrip the ACTUAL key, not a reconstructed source/serializer/class identifier.
            active.addProperty(probe.api(), ownerKey == null ? "not_sampled"
                    : probe.sameObject().test(actualObject, ownerKey) ? "same_object" : "mismatch");
            allAbsent &= ownerKey == null;
            allKeys &= ownerKey != null;
            if (text != null) {
                if (commonKey == null) commonKey = text;
                else agree &= commonKey.equals(text);
            }
        }
        String state = allAbsent ? "actually_unregistered" : allKeys && agree ? "registered" : "unknown";
        JsonObject node = new JsonObject();
        node.addProperty("ref", prefix + nodes.size());
        node.addProperty("class", actualObject.getClass().getName());
        node.addProperty("first_recipe_id", recipeId);
        node.addProperty("state", state);
        node.add("registered_key", state.equals("registered") ? new JsonPrimitive(commonKey) : JsonNull.INSTANCE);
        node.add("lookups", lookups);
        node.add("active_binding", active);
        return node;
    }

    private void appendGaps(String recipeId, JsonObject node, JsonArray gaps) {
        String state = node.get("state").getAsString();
        if (state.equals("actually_unregistered"))
            gap(gaps, recipeId, field, "Actual nonnull object; all required successful owner-key lookups returned null (known absence, not getter failure)");
        else if (state.equals("unknown"))
            gap(gaps, recipeId, field, "Actual required owner-key observations conflict, including one-null/one-key; no registry is preferred");
        JsonObject active = node.getAsJsonObject("active_binding");
        for (Probe<T, K> probe : probes) {
            String result = active.get(probe.api()).getAsString();
            if (result.equals("mismatch"))
                gap(gaps, recipeId, field + ".active_binding", probe.api() + ": actual public owner-key roundtrip did not return the same object");
            else if (result.equals("not_sampled"))
                gap(gaps, recipeId, field + ".active_binding", probe.api() + ": active roundtrip not sampled because actual owner-key lookup returned null");
        }
    }

    /** Only table data is exported; maps/object references never escape or persist across invocations. */
    JsonArray nodes() { return nodes.deepCopy(); }

    private static void gap(JsonArray gaps, String id, String field, String reason) {
        JsonObject gap = new JsonObject();
        gap.addProperty("scope", "recipe"); gap.addProperty("id", id);
        gap.addProperty("field", field); gap.addProperty("reason", reason);
        gaps.add(gap);
    }

    record TreeMeasure(int nodes, int depth) {}

    static TreeMeasure checkTree(JsonElement root) {
        int[] measure = {0, 0};
        walk(Objects.requireNonNull(root), 0, measure);
        return new TreeMeasure(measure[0], measure[1]);
    }

    private static void walk(JsonElement value, int depth, int[] measure) {
        if (depth > MAX_DEPTH || ++measure[0] > MAX_NODES)
            throw new IllegalArgumentException("Complete raw JSON structural bounds exceeded");
        measure[1] = Math.max(measure[1], depth);
        if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                unicode(entry.getKey());
                walk(entry.getValue(), depth + 1, measure);
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) walk(child, depth + 1, measure);
        } else if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isString()) unicode(primitive.getAsString());
            else if (primitive.isNumber() && !JSON_NUMBER.matcher(primitive.getAsString()).matches())
                throw new IllegalArgumentException("Nonfinite or invalid JSON number lexeme");
        } else if (!value.isJsonNull()) throw new IllegalArgumentException("Unsupported raw JSON value");
    }

    private static void unicode(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i)))
                    throw new IllegalArgumentException("Unpaired high surrogate in raw JSON key/string");
            } else if (Character.isLowSurrogate(c))
                throw new IllegalArgumentException("Unpaired low surrogate in raw JSON key/string");
        }
    }

    static byte[] encode(JsonObject root, Gson gson) throws IOException {
        return encode(root, gson, MAX_RAW_BYTES);
    }

    /** Internal small-bound controls only; this overload cannot raise the fixed production limit. */
    static byte[] encode(JsonObject root, Gson gson, int byteLimit) throws IOException {
        if (byteLimit < 1 || byteLimit > MAX_RAW_BYTES) throw new IllegalArgumentException("Invalid raw byte limit");
        checkTree(root);
        BoundedBytes sink = new BoundedBytes(byteLimit);
        try (Writer utf8 = new OutputStreamWriter(sink, StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
             JsonWriter writer = gson.newJsonWriter(utf8)) {
            gson.toJson(root, writer);
            writer.flush();
            utf8.write('\n');
        }
        return sink.payload();
    }

    /** Chunk allocation and final payload are individually bounded; no giant Gson String or growing >cap buffer. */
    private static final class BoundedBytes extends OutputStream {
        private static final int CHUNK = 8192;
        private final int limit;
        private final List<byte[]> chunks = new ArrayList<>();
        private int size, used;
        BoundedBytes(int limit) { this.limit = limit; }
        private byte[] chunk() {
            if (chunks.isEmpty() || used == chunks.get(chunks.size() - 1).length) {
                chunks.add(new byte[Math.min(CHUNK, limit - size)]);
                used = 0;
            }
            return chunks.get(chunks.size() - 1);
        }
        @Override public void write(int b) throws IOException {
            if (size == limit) throw new IOException("Complete raw UTF8 byte bound exceeded (including final LF)");
            chunk()[used++] = (byte) b; size++;
        }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length > limit - size) throw new IOException("Complete raw UTF8 byte bound exceeded (including final LF)");
            while (length > 0) {
                byte[] target = chunk();
                int count = Math.min(length, target.length - used);
                System.arraycopy(bytes, offset, target, used, count);
                offset += count; length -= count; used += count; size += count;
            }
        }
        byte[] payload() {
            if (size == 0) throw new IllegalStateException("Empty raw UTF8 payload");
            byte[] result = new byte[size];
            int offset = 0;
            for (byte[] part : chunks) {
                int count = Math.min(part.length, size - offset);
                System.arraycopy(part, 0, result, offset, count);
                offset += count;
            }
            return result;
        }
    }
}
