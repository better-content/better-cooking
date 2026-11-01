package com.bettercontent.bettercooking.fixture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** SYNTHETIC pure observations only. No Minecraft bootstrap, registries, provider or binding capability. */
class NativeRecipeIdentityTableTest {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    private static NativeRecipeIdentityTable<Object, String> types(Function<Object, String> builtin,
            Function<Object, String> forge, BiPredicate<Object, String> builtinActive, BiPredicate<Object, String> forgeActive) {
        return new NativeRecipeIdentityTable<>("t", List.of(
                new NativeRecipeIdentityTable.Probe<>("builtin", builtin, builtinActive),
                new NativeRecipeIdentityTable.Probe<>("forge", forge, forgeActive)), Function.identity());
    }

    private static NativeRecipeIdentityTable<Object, String> serializers(Function<Object, String> key,
            BiPredicate<Object, String> active) {
        return new NativeRecipeIdentityTable<>("s", List.of(
                new NativeRecipeIdentityTable.Probe<>("forge", key, active)), Function.identity());
    }

    private static JsonObject node(NativeRecipeIdentityTable<?, ?> table, int ordinal) {
        return table.nodes().get(ordinal).getAsJsonObject();
    }

    @Test void registeredMeansOwnerKeysNotApproval() {
        Object actual = new Object();
        var table = types(value -> "test:type", value -> "test:type", (value, key) -> value == actual, (value, key) -> value == actual);
        JsonArray gaps = new JsonArray();
        JsonObject observed = table.observe("test:a", actual, gaps), node = node(table, 0);
        assertEquals(Set.of("getter", "state", "ref"), observed.keySet());
        assertEquals("returned_object", observed.get("getter").getAsString());
        assertEquals("registered", observed.get("state").getAsString());
        assertEquals("t0", observed.get("ref").getAsString());
        assertEquals(Set.of("ref", "class", "first_recipe_id", "state", "registered_key", "lookups", "active_binding"), node.keySet());
        assertEquals(actual.getClass().getName(), node.get("class").getAsString());
        assertEquals("test:a", node.get("first_recipe_id").getAsString());
        assertEquals("test:type", node.get("registered_key").getAsString());
        assertEquals(Set.of("builtin", "forge"), node.getAsJsonObject("lookups").keySet());
        for (String api : List.of("builtin", "forge")) {
            JsonObject lookup = node.getAsJsonObject("lookups").getAsJsonObject(api);
            assertEquals(Set.of("outcome", "key", "error"), lookup.keySet());
            assertEquals("key", lookup.get("outcome").getAsString());
            assertTrue(lookup.get("error").isJsonNull());
            assertEquals("same_object", node.getAsJsonObject("active_binding").get(api).getAsString());
        }
        assertEquals(0, gaps.size());
        assertFalse(node.has("qualification"));
        assertFalse(node.has("backend"));
    }

    @Test void successfulNullKeysAreKnownAbsenceAndNeverRoundtripped() {
        AtomicInteger lookups = new AtomicInteger();
        Function<Object, String> absent = value -> { lookups.incrementAndGet(); return null; };
        BiPredicate<Object, String> forbidden = (value, key) -> { throw new AssertionError("No absent-key roundtrip"); };
        var table = types(absent, absent, forbidden, forbidden);
        JsonArray gaps = new JsonArray();
        assertEquals("actually_unregistered", table.observe("test:a", new Object(), gaps).get("state").getAsString());
        JsonObject node = node(table, 0);
        assertEquals(2, lookups.get());
        assertTrue(node.get("registered_key").isJsonNull());
        for (String api : List.of("builtin", "forge")) {
            JsonObject lookup = node.getAsJsonObject("lookups").getAsJsonObject(api);
            assertEquals("absent", lookup.get("outcome").getAsString());
            assertTrue(lookup.get("key").isJsonNull());
            assertTrue(lookup.get("error").isJsonNull());
            assertEquals("not_sampled", node.getAsJsonObject("active_binding").get(api).getAsString());
        }
        assertEquals(3, gaps.size());
        assertTrue(gaps.get(0).getAsJsonObject().get("reason").getAsString().contains("known absence"));
    }

    @Test void missingGetterHasNoObjectOrLookups() {
        Function<Object, String> forbidden = value -> { throw new AssertionError("Missing getter has no lookup"); };
        var table = types(forbidden, forbidden, (value, key) -> true, (value, key) -> true);
        JsonArray gaps = new JsonArray();
        JsonObject row = table.observe("test:a", null, gaps);
        assertEquals("returned_null", row.get("getter").getAsString());
        assertEquals("missing", row.get("state").getAsString());
        assertTrue(row.get("ref").isJsonNull());
        assertEquals(0, table.nodes().size());
        assertEquals(1, gaps.size());
        assertEquals("native_recipe_type", gaps.get(0).getAsJsonObject().get("field").getAsString());
    }

    @Test void oneKeyOneNullRemainsUnknownButSamplesTheActualPresentRoundtrip() {
        Object actual = new Object(); AtomicInteger roundtrips = new AtomicInteger();
        var table = types(value -> "test:owner", value -> null, (value, key) -> {
            assertSame(actual, value); assertEquals("test:owner", key); roundtrips.incrementAndGet(); return true;
        }, (value, key) -> { throw new AssertionError("Absent Forge key"); });
        JsonObject row = table.observe("test:a", actual, new JsonArray()), node = node(table, 0);
        assertEquals("unknown", row.get("state").getAsString());
        assertTrue(node.get("registered_key").isJsonNull());
        assertEquals(1, roundtrips.get());
        assertEquals("same_object", node.getAsJsonObject("active_binding").get("builtin").getAsString());
        assertEquals("not_sampled", node.getAsJsonObject("active_binding").get("forge").getAsString());
    }

    @Test void conflictingNonnullKeysHaveNoPreferredOwnerAndBothRoundtripsSurvive() {
        AtomicInteger roundtrips = new AtomicInteger();
        var table = types(value -> "test:one", value -> "test:two", (value, key) -> {
            assertEquals("test:one", key); roundtrips.incrementAndGet(); return true;
        }, (value, key) -> { assertEquals("test:two", key); roundtrips.incrementAndGet(); return false; });
        JsonArray gaps = new JsonArray();
        table.observe("test:a", new Object(), gaps);
        JsonObject node = node(table, 0);
        assertEquals("unknown", node.get("state").getAsString());
        assertTrue(node.get("registered_key").isJsonNull());
        assertEquals(2, roundtrips.get());
        assertEquals("test:one", node.getAsJsonObject("lookups").getAsJsonObject("builtin").get("key").getAsString());
        assertEquals("test:two", node.getAsJsonObject("lookups").getAsJsonObject("forge").get("key").getAsString());
        assertEquals("mismatch", node.getAsJsonObject("active_binding").get("forge").getAsString());
        assertEquals(2, gaps.size());
    }

    @Test void inactiveOwnerRemainsRegisteredOwnerNeverActiveApproval() {
        var table = types(value -> "test:owner", value -> "test:owner", (value, key) -> false, (value, key) -> false);
        JsonArray gaps = new JsonArray();
        table.observe("test:a", new Object(), gaps);
        JsonObject node = node(table, 0);
        assertEquals("registered", node.get("state").getAsString());
        assertEquals("test:owner", node.get("registered_key").getAsString());
        assertEquals(2, gaps.size());
        for (JsonElement gap : gaps) assertEquals("native_recipe_type.active_binding", gap.getAsJsonObject().get("field").getAsString());
    }

    @Test void sameReferenceReusesFirstEncounterFactsWithoutContinuousSampling() {
        Object actual = new Object(); AtomicInteger samples = new AtomicInteger();
        AtomicReference<String> key = new AtomicReference<>("test:first");
        var table = serializers(value -> { samples.incrementAndGet(); return key.get(); }, (value, owner) -> true);
        JsonObject first = table.observe("test:a", actual, new JsonArray());
        key.set("test:later");
        JsonObject second = table.observe("test:b", actual, new JsonArray());
        assertEquals(first, second);
        assertEquals(1, table.nodes().size());
        assertEquals(1, samples.get());
        assertEquals("test:a", node(table, 0).get("first_recipe_id").getAsString());
        assertEquals("test:first", node(table, 0).get("registered_key").getAsString());
        assertEquals("s0", second.get("ref").getAsString());
    }

    private static final class EqualObject {
        int equalsCalls, hashCalls;
        @Override public boolean equals(Object other) { equalsCalls++; return other instanceof EqualObject; }
        @Override public int hashCode() { hashCalls++; return 0; }
        @Override public String toString() { throw new AssertionError("Object toString is not native identity"); }
    }

    @Test void sameClassEqualDistinctObjectsNeverMerge() {
        EqualObject first = new EqualObject(), second = new EqualObject();
        var table = serializers(value -> "test:shared_owner", (value, key) -> true);
        JsonObject a = table.observe("test:a", first, new JsonArray());
        JsonObject b = table.observe("test:b", second, new JsonArray());
        assertEquals("s0", a.get("ref").getAsString());
        assertEquals("s1", b.get("ref").getAsString());
        assertEquals(2, table.nodes().size());
        assertEquals(node(table, 0).get("class"), node(table, 1).get("class"));
        assertEquals(0, first.equalsCalls + second.equalsCalls + first.hashCalls + second.hashCalls);
    }

    @Test void tablesAndInvocationsAreSeparateWithoutStaticIdentityCaches() {
        Object actual = new Object();
        AtomicReference<String> key = new AtomicReference<>("test:first");
        var types = types(value -> key.get(), value -> key.get(), (value, owner) -> true, (value, owner) -> true);
        var serializers = serializers(value -> key.get(), (value, owner) -> true);
        assertEquals("t0", types.observe("test:a", actual, new JsonArray()).get("ref").getAsString());
        assertEquals("s0", serializers.observe("test:a", actual, new JsonArray()).get("ref").getAsString());
        key.set("test:new_invocation");
        var fresh = serializers(value -> key.get(), (value, owner) -> true);
        assertEquals("s0", fresh.observe("test:a", actual, new JsonArray()).get("ref").getAsString());
        assertEquals("test:new_invocation", node(fresh, 0).get("registered_key").getAsString());
        assertEquals("test:first", node(serializers, 0).get("registered_key").getAsString());
    }

    @Test void missingSerializerAndKnownAbsentSerializerRemainDifferent() {
        var table = serializers(value -> null, (value, key) -> { throw new AssertionError("No null-key lookup"); });
        assertEquals("missing", table.observe("test:a", null, new JsonArray()).get("state").getAsString());
        JsonObject row = table.observe("test:b", new Object(), new JsonArray());
        assertEquals("actually_unregistered", row.get("state").getAsString());
        assertEquals("s0", row.get("ref").getAsString());
        assertEquals(Set.of("forge"), node(table, 0).getAsJsonObject("lookups").keySet());
        assertEquals("test:b", node(table, 0).get("first_recipe_id").getAsString());
    }

    @Test void duplicateAndUnsortedRowsAbortRatherThanInventOrdinals() {
        var duplicate = serializers(value -> null, (value, key) -> false);
        duplicate.observe("test:a", null, new JsonArray());
        assertThrows(IllegalArgumentException.class, () -> duplicate.observe("test:a", new Object(), new JsonArray()));
        var unsorted = serializers(value -> null, (value, key) -> false);
        unsorted.observe("test:b", new Object(), new JsonArray());
        assertThrows(IllegalArgumentException.class, () -> unsorted.observe("test:a", new Object(), new JsonArray()));
        assertEquals(1, unsorted.nodes().size());
    }

    @Test void lookupAndRoundtripFailuresPropagateTheOriginalThrowable() {
        RuntimeException failure = new IllegalStateException("synthetic terminal lookup failure");
        var lookup = serializers(value -> { throw failure; }, (value, key) -> true);
        assertSame(failure, assertThrows(RuntimeException.class, () -> lookup.observe("test:a", new Object(), new JsonArray())));
        assertEquals(0, lookup.nodes().size());
        var roundtrip = serializers(value -> "test:owner", (value, key) -> { throw failure; });
        assertSame(failure, assertThrows(RuntimeException.class, () -> roundtrip.observe("test:a", new Object(), new JsonArray())));
        assertEquals(0, roundtrip.nodes().size());
    }

    @Test void unexpectedErrorsAreNotConvertedIntoErrorNodesOrFallbackKeys() {
        AssertionError original = new AssertionError("synthetic unexpected error");
        var table = serializers(value -> { throw original; }, (value, key) -> true);
        JsonArray gaps = new JsonArray();
        assertSame(original, assertThrows(AssertionError.class, () -> table.observe("test:a", new Object(), gaps)));
        assertEquals(0, table.nodes().size());
        assertEquals(0, gaps.size());
    }

    @Test void invalidOwnerKeyDoesNotBecomeAnInventedResourceLocation() {
        var table = serializers(value -> "Descriptive class, not owner", (value, key) -> { throw new AssertionError("Invalid key"); });
        assertThrows(IllegalArgumentException.class, () -> table.observe("test:a", new Object(), new JsonArray()));
        assertEquals(0, table.nodes().size());
    }

    @Test void exportedNodeMutationCannotChangeCachedFirstEncounterFacts() {
        Object actual = new Object();
        var table = serializers(value -> "test:owner", (value, key) -> true);
        table.observe("test:a", actual, new JsonArray());
        node(table, 0).addProperty("state", "unknown");
        assertEquals("registered", table.observe("test:b", actual, new JsonArray()).get("state").getAsString());
        assertEquals("registered", node(table, 0).get("state").getAsString());
    }

    @Test void onlyFixedRoleApiSetsAreAllowed() {
        assertThrows(IllegalArgumentException.class, () -> new NativeRecipeIdentityTable<Object, String>("t", List.of(), Function.identity()));
        assertThrows(IllegalArgumentException.class, () -> new NativeRecipeIdentityTable<Object, String>("backend", List.of(), Function.identity()));
        assertThrows(IllegalArgumentException.class, () -> new NativeRecipeIdentityTable<Object, String>("s", List.of(
                new NativeRecipeIdentityTable.Probe<>("builtin", value -> null, (value, key) -> false)), Function.identity()));
    }

    @Test void allExplicitNullMembersAndCurrentFormattingSurviveOneFinalLf() throws IOException {
        JsonObject root = new JsonObject(), food = new JsonObject();
        root.add("identity_unknown", JsonNull.INSTANCE); food.add("spice", JsonNull.INSTANCE);
        root.add("nonidentity_food", food); root.addProperty("zero", 0); root.add("empty", new JsonArray());
        byte[] actual = NativeRecipeIdentityTable.encode(root, JSON, 4096);
        assertArrayEquals((JSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8), actual);
        String text = new String(actual, StandardCharsets.UTF_8);
        assertTrue(text.contains("\"identity_unknown\": null"));
        assertTrue(text.contains("\"spice\": null"));
        assertTrue(text.endsWith("}\n")); assertFalse(text.endsWith("\n\n"));
    }

    @Test void countNbtChanceOrderedMultioutputAndAirZeroAreConservedNotNormalized() throws IOException {
        // Synthetic raw facts only, not a dynamic recipe/yield or provider capability assertion.
        JsonObject root = new JsonObject(), first = new JsonObject(), air = new JsonObject();
        first.addProperty("id", "test:food"); first.addProperty("count", 3);
        first.addProperty("nbt", "{display:{Name:'original'},custom:[I;1,2,3]}");
        first.addProperty("chance", new BigDecimal("0.375"));
        air.addProperty("id", "minecraft:air"); air.addProperty("count", 0); air.addProperty("chance", 0);
        JsonArray outputs = new JsonArray(); outputs.add(first); outputs.add(air); root.add("results", outputs);
        root.add("unknown_cost", JsonNull.INSTANCE); root.add("empty_ingredients", new JsonArray());
        root.addProperty("recipe_stack_nbt_basis", "item_getTag_snbt_only_full_saved_stack_and_ForgeCaps_not_observed");
        JsonObject before = root.deepCopy();
        String encoded = new String(NativeRecipeIdentityTable.encode(root, JSON, 4096), StandardCharsets.UTF_8);
        assertEquals(JSON.toJson(before) + "\n", encoded);
        assertEquals(before, root);
        assertEquals(3, outputs.get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(new BigDecimal("0.375"), outputs.get(0).getAsJsonObject().get("chance").getAsBigDecimal());
        assertEquals("minecraft:air", outputs.get(1).getAsJsonObject().get("id").getAsString());
        assertEquals(0, outputs.get(1).getAsJsonObject().get("count").getAsInt());
        assertFalse(air.has("nbt"));
        assertTrue(root.get("unknown_cost").isJsonNull());
    }

    private static void assertByteBoundFailure(JsonObject root, int limit) {
        Exception failure = assertThrows(Exception.class, () -> NativeRecipeIdentityTable.encode(root, JSON, limit));
        assertTrue(failure instanceof IOException || failure instanceof JsonIOException, failure.toString());
    }

    @Test void encodedBytesIncludingLfNotCharacterCountDefineTheBound() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("\u03b1", "\u6c34" + new String(Character.toChars(0x1f600)) + "\n\"\\");
        byte[] expected = (JSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expected, NativeRecipeIdentityTable.encode(root, JSON, expected.length));
        assertByteBoundFailure(root, expected.length - 1);
        assertByteBoundFailure(root, expected.length - 2);
        assertTrue(expected.length > (JSON.toJson(root) + "\n").length());
    }

    @Test void chunkBoundaryMultibyteStringsAreLossless() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("text", "x".repeat(8191) + new String(Character.toChars(0x1f600)) + "\u6c34");
        byte[] expected = (JSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expected, NativeRecipeIdentityTable.encode(root, JSON, expected.length));
        assertByteBoundFailure(root, expected.length - 1);
    }

    @Test void internalSmallBoundsCannotRaiseProductionCap() {
        JsonObject root = new JsonObject();
        assertEquals(67_108_864, NativeRecipeIdentityTable.MAX_RAW_BYTES);
        assertEquals(64, NativeRecipeIdentityTable.MAX_DEPTH);
        assertEquals(2_000_000, NativeRecipeIdentityTable.MAX_NODES);
        assertThrows(IllegalArgumentException.class, () -> NativeRecipeIdentityTable.encode(root, JSON, 0));
        assertThrows(IllegalArgumentException.class, () -> NativeRecipeIdentityTable.encode(root, JSON, NativeRecipeIdentityTable.MAX_RAW_BYTES + 1));
    }

    @Test void unpairedSurrogatesInKeysAndValuesAbortBeforeEncoding() {
        for (char surrogate : new char[]{(char) 0xd800, (char) 0xdc00}) {
            String bad = new String(new char[]{surrogate});
            JsonObject value = new JsonObject(); value.addProperty("text", bad);
            assertThrows(IllegalArgumentException.class, () -> NativeRecipeIdentityTable.encode(value, JSON));
            JsonObject key = new JsonObject(); key.addProperty(bad, "value");
            assertThrows(IllegalArgumentException.class, () -> NativeRecipeIdentityTable.encode(key, JSON));
        }
        JsonObject wrongPair = new JsonObject();
        wrongPair.addProperty("text", new String(new char[]{(char) 0xd800, 'a'}));
        assertThrows(IllegalArgumentException.class, () -> NativeRecipeIdentityTable.checkTree(wrongPair));
    }

    private static final class LexemeNumber extends Number {
        private final String lexeme;
        LexemeNumber(String lexeme) { this.lexeme = lexeme; }
        @Override public int intValue() { throw new AssertionError("No number coercion"); }
        @Override public long longValue() { throw new AssertionError("No number coercion"); }
        @Override public float floatValue() { throw new AssertionError("No number coercion"); }
        @Override public double doubleValue() { throw new AssertionError("No number coercion"); }
        @Override public String toString() { return lexeme; }
    }

    @Test void finiteJsonNumberLexemesAreNotRoundedOrBlanketClamped() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("signed_source_fact", -13);
        root.addProperty("exact", new BigDecimal("0.12345678901234567890123456789"));
        root.addProperty("large_finite", new BigDecimal("1e500"));
        root.addProperty("valid_lexeme", new LexemeNumber("-0.00e+2"));
        root.addProperty("ordinary_string", "NaN");
        NativeRecipeIdentityTable.checkTree(root);
        String result = new String(NativeRecipeIdentityTable.encode(root, JSON, 4096), StandardCharsets.UTF_8);
        assertTrue(result.contains("0.12345678901234567890123456789"));
        assertTrue(result.contains("-0.00e+2"));
        assertTrue(result.contains("-13"));
    }

    @Test void nonfiniteAndInvalidNumberLexemesNeverBecomeNullOrZero() {
        for (Number invalid : List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                new LexemeNumber("01"), new LexemeNumber("+1"), new LexemeNumber(".1"),
                new LexemeNumber("1."), new LexemeNumber("1e"), new LexemeNumber(" 1"))) {
            JsonObject root = new JsonObject(); root.addProperty("number", invalid);
            assertThrows(IllegalArgumentException.class, () -> NativeRecipeIdentityTable.encode(root, JSON));
            assertFalse(root.get("number").isJsonNull());
        }
    }

    @Test void completeTreeCountsValuesNotKeysAndPreservesNulls() {
        JsonObject root = new JsonObject(); JsonArray array = new JsonArray();
        array.add(JsonNull.INSTANCE); array.add(true); array.add(3);
        root.add("array", array); root.addProperty("string", "value");
        assertEquals(new NativeRecipeIdentityTable.TreeMeasure(6, 2), NativeRecipeIdentityTable.checkTree(root));
    }

    private static JsonElement nested(int depth) {
        JsonElement value = JsonNull.INSTANCE;
        for (int i = 0; i < depth; i++) { JsonArray parent = new JsonArray(); parent.add(value); value = parent; }
        return value;
    }

    @Test void completeTreeDepth64Accepted65RejectedIncludingOpaqueSections() {
        assertEquals(64, NativeRecipeIdentityTable.checkTree(nested(64)).depth());
        assertThrows(IllegalArgumentException.class, () -> NativeRecipeIdentityTable.checkTree(nested(65)));
        JsonObject opaque = new JsonObject(); opaque.add("resource_json", nested(64));
        assertThrows(IllegalArgumentException.class, () -> NativeRecipeIdentityTable.encode(opaque, JSON));
    }

    @Test void exactlyTwoMillionValuesAcceptedOneMoreRejected() {
        // One bounded edge case, singleton nulls and preallocated references, not an uncapped matrix.
        JsonArray root = new JsonArray(NativeRecipeIdentityTable.MAX_NODES);
        for (int i = 1; i < NativeRecipeIdentityTable.MAX_NODES; i++) root.add(JsonNull.INSTANCE);
        assertEquals(NativeRecipeIdentityTable.MAX_NODES, NativeRecipeIdentityTable.checkTree(root).nodes());
        root.add(JsonNull.INSTANCE);
        assertThrows(IllegalArgumentException.class, () -> NativeRecipeIdentityTable.checkTree(root));
    }

    @Test void cyclicJsonElementCannotReachUnboundedSerialization() {
        JsonObject cycle = new JsonObject(); cycle.add("cycle", cycle);
        assertThrows(IllegalArgumentException.class, () -> NativeRecipeIdentityTable.encode(cycle, JSON));
    }

    @Test void oneBoundedFullProductionByteEdgeIncludesFinalLf() throws IOException {
        // Single exact64MiB case; no giant Gson String/expected-copy and no cap expansion.
        JsonObject root = new JsonObject(); root.addProperty("payload", "");
        int overhead = NativeRecipeIdentityTable.encode(root, JSON, 4096).length;
        root.addProperty("payload", "x".repeat(NativeRecipeIdentityTable.MAX_RAW_BYTES - overhead));
        byte[] exact = NativeRecipeIdentityTable.encode(root, JSON);
        assertEquals(NativeRecipeIdentityTable.MAX_RAW_BYTES, exact.length);
        assertEquals('\n', exact[exact.length - 1]);
        root.addProperty("payload", root.get("payload").getAsString() + "x");
        assertByteBoundFailure(root, NativeRecipeIdentityTable.MAX_RAW_BYTES);
    }
}
