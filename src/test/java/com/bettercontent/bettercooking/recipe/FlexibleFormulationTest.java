package com.bettercontent.bettercooking.recipe;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** No Minecraft bootstrap, fake registry or mocked ItemStack. */
class FlexibleFormulationTest {
    private static FlexibleFormulation flexible() {
        return new FlexibleFormulation(FlexibleFormulation.FLEXIBLE, "kaleidoscope_cookery:flex_pot/blaze_lamb_chop",
            "kaleidoscope_cookery:flex_pot", List.of("kc_source:item/quality/QualityEvaluator.java", "kc_source:blockentity/kitchen/PotBlockEntity.java#applyFlexRecipe"));
    }
    @Test void defaultsAreFixedAndCanonicalRoundTripsBothModes() {
        assertFalse(FlexibleFormulation.fromJson(null).flexible());
        for (var value : List.of(FlexibleFormulation.fixed(), flexible())) {
            assertEquals(value, FlexibleFormulation.parse(value.fingerprint()));
            assertEquals(value.fingerprint(), FlexibleFormulation.parse(value.fingerprint()).fingerprint());
        }
    }
    @Test void onlyPinnedOriginalFiftyFiveRecipesAreAcceptedNotMigrationOrProfile() {
        assertEquals(55, FlexibleFormulation.SOURCE_RECIPES.size());
        assertEquals(27, FlexibleFormulation.SOURCE_RECIPES.stream().filter(s -> s.contains(":flex_pot/")).count());
        assertEquals(28, FlexibleFormulation.SOURCE_RECIPES.stream().filter(s -> s.contains(":flex_stockpot/")).count());
        for (String id : List.of("better_cooking:migration/kaleidoscope_cookery/flex_pot/blaze_lamb_chop", "kaleidoscope_cookery:pot/blaze_lamb_chop", "kaleidoscope_cookery:flex_pot/not_a_real_recipe"))
            assertThrows(IllegalArgumentException.class, () -> new FlexibleFormulation(FlexibleFormulation.FLEXIBLE, id, "kaleidoscope_cookery:flex_pot", List.of("evidence")));
    }
    @Test void modeSeedBasisInheritanceAndGroupContractCannotSilentlyDrift() {
        for (String key : List.of("mode", "seed_source", "input_basis", "inheritance", "source_recipe_type", "source_recipe_id")) {
            JsonObject json = flexible().toJson(); json.addProperty(key, "wrong");
            assertThrows(IllegalArgumentException.class, () -> FlexibleFormulation.fromJson(json), key);
        }
        for (String key : List.of("debit", "ratios", "required_groups")) {
            JsonObject json = flexible().toJson(); json.getAsJsonObject("formulation").addProperty(key, "wrong");
            assertThrows(IllegalArgumentException.class, () -> FlexibleFormulation.fromJson(json), key);
        }
    }
    @Test void nineIsExactNumericBoundAndNoUnknownIgnoredFields() {
        for (String bad : List.of("0", "8", "10", "9.0", "9.5")) {
            JsonObject json = flexible().toJson(); json.getAsJsonObject("formulation").add("max_native_units", com.google.gson.JsonParser.parseString(bad));
            assertThrows(IllegalArgumentException.class, () -> FlexibleFormulation.fromJson(json));
        }
        JsonObject json = flexible().toJson(); json.addProperty("unknown_field", true);
        assertThrows(IllegalArgumentException.class, () -> FlexibleFormulation.fromJson(json));
        JsonObject fixed = FlexibleFormulation.fixed().toJson(); fixed.addProperty("source_recipe_id", flexible().sourceRecipeId());
        assertThrows(IllegalArgumentException.class, () -> FlexibleFormulation.fromJson(fixed));
    }
    @Test void evidenceIsImmutableAndSignatureBindsBehavioralSemantics() {
        assertNotEquals(flexible().fingerprint(), FlexibleFormulation.fixed().fingerprint());
        assertThrows(UnsupportedOperationException.class, () -> flexible().evidence().add("changed"));
        var other = new FlexibleFormulation(FlexibleFormulation.FLEXIBLE, "kaleidoscope_cookery:flex_pot/braised_beef", "kaleidoscope_cookery:flex_pot", flexible().evidence());
        assertNotEquals(flexible().fingerprint(), other.fingerprint());
        var reordered = new FlexibleFormulation(flexible().mode(), flexible().sourceRecipeId(), flexible().sourceRecipeType(), List.of(flexible().evidence().get(1), flexible().evidence().get(0)));
        assertEquals(flexible().fingerprint(), reordered.fingerprint());
    }
    @Test void acceptedMetadataIsBoundedForFullNetworkPayload() {
        assertTrue(flexible().fingerprint().length() < 8192);
        assertThrows(IllegalArgumentException.class, () -> new FlexibleFormulation(FlexibleFormulation.FLEXIBLE,
            flexible().sourceRecipeId(), flexible().sourceRecipeType(), java.util.stream.IntStream.range(0, 16).mapToObj(i -> ("x".repeat(510) + i)).toList()));
    }
    @Test void eachGroupMustHaveDistinctPaymentAndEveryUnitMustBeAllowed() {
        assertFalse(FlexibleFormulation.covers(new boolean[][]{{true}, {true}}));
        assertFalse(FlexibleFormulation.covers(new boolean[][]{{true, false}, {true, false}}));
        assertTrue(FlexibleFormulation.covers(new boolean[][]{{true, true}, {true, false}}));
        assertFalse(FlexibleFormulation.covers(new boolean[][]{{true, false}}));
        assertFalse(FlexibleFormulation.covers(new boolean[][]{{true}, {false}}));
    }
    @Test void variableRatiosUpToNineDoNotRequireExactDefaultCounts() {
        for (int count = 1; count <= 9; count++) {
            boolean[][] one = new boolean[1][count]; java.util.Arrays.fill(one[0], true);
            assertTrue(FlexibleFormulation.covers(one));
        }
        assertFalse(FlexibleFormulation.covers(new boolean[1][10]));
        assertFalse(FlexibleFormulation.covers(new boolean[0][0]));
        assertFalse(FlexibleFormulation.covers(new boolean[1][0]));
    }
    @Test void allSmallMatricesMatchIndependentDistinctAssignmentOracle() {
        for (int groups = 1; groups <= 4; groups++) for (int units = 1; units <= 4; units++) {
            for (int mask = 0; mask < (1 << (groups * units)); mask++) {
                boolean[][] matrix = new boolean[groups][units]; boolean allUnitsAllowed = true;
                for (int unit = 0; unit < units; unit++) {
                    boolean any = false;
                    for (int group = 0; group < groups; group++) { matrix[group][unit] = (mask & (1 << (group * units + unit))) != 0; any |= matrix[group][unit]; }
                    allUnitsAllowed &= any;
                }
                boolean expected = allUnitsAllowed && assign(matrix, 0, new boolean[units]);
                if (expected != FlexibleFormulation.covers(matrix)) fail("Mismatch groups=" + groups + " units=" + units + " mask=" + mask);
            }
        }
    }
    private static boolean assign(boolean[][] matrix, int group, boolean[] paid) {
        if (group == matrix.length) return true;
        for (int unit = 0; unit < paid.length; unit++) if (!paid[unit] && matrix[group][unit]) {
            paid[unit] = true;
            if (assign(matrix, group + 1, paid)) return true;
            paid[unit] = false;
        }
        return false;
    }
}
