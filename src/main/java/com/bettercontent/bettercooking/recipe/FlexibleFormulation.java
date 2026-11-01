package com.bettercontent.bettercooking.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Explicit source-proven recipe semantics; never inferred from an output food profile. */
public record FlexibleFormulation(String mode, String sourceRecipeId, String sourceRecipeType, List<String> evidence) {
    public static final String FIXED = "fixed_standard_unless_inherited_preparation";
    public static final String FLEXIBLE = "retained_seeded_flexible_evaluator";
    public static final String INHERITANCE = "worst_inherited_no_reroll_or_compound";
    public static final int MAX_UNITS = 9;
    // Actual recipes in pinned KC1.6.0 JAR SHA256 0eee5579b36746825d6586c65051d9e68fc84178a19dec4372fc9d64b8629d62.
    // These IDs, not the migration IDs or food quality labels, seed KC's public evaluator.
    public static final Set<String> SOURCE_RECIPES = Set.of(
        "kaleidoscope_cookery:flex_pot/blaze_lamb_chop",
        "kaleidoscope_cookery:flex_pot/braised_beef",
        "kaleidoscope_cookery:flex_pot/braised_pork_ribs",
        "kaleidoscope_cookery:flex_pot/candied_potato",
        "kaleidoscope_cookery:flex_pot/chorus_fried_egg",
        "kaleidoscope_cookery:flex_pot/crystal_lamb_chop",
        "kaleidoscope_cookery:flex_pot/dongpo_pork",
        "kaleidoscope_cookery:flex_pot/donkey_burger",
        "kaleidoscope_cookery:flex_pot/egg_fried_rice",
        "kaleidoscope_cookery:flex_pot/fish_flavored_shredded_pork",
        "kaleidoscope_cookery:flex_pot/fondant_pie",
        "kaleidoscope_cookery:flex_pot/fondant_spider_eye",
        "kaleidoscope_cookery:flex_pot/fried_caterpillar",
        "kaleidoscope_cookery:flex_pot/fried_spring_roll",
        "kaleidoscope_cookery:flex_pot/frost_lamb_chop",
        "kaleidoscope_cookery:flex_pot/oil_splashed_fish",
        "kaleidoscope_cookery:flex_pot/pan_seared_knight_steak",
        "kaleidoscope_cookery:flex_pot/scramble_egg_with_tomatoes",
        "kaleidoscope_cookery:flex_pot/slime_ball_meal",
        "kaleidoscope_cookery:flex_pot/spicy_chicken",
        "kaleidoscope_cookery:flex_pot/spicy_rabbit_head",
        "kaleidoscope_cookery:flex_pot/stargazy_pie",
        "kaleidoscope_cookery:flex_pot/sticky_candy",
        "kaleidoscope_cookery:flex_pot/stir_fried_pork_with_peppers",
        "kaleidoscope_cookery:flex_pot/stuffed_tiger_skin_pepper",
        "kaleidoscope_cookery:flex_pot/sweet_and_sour_ender_pearls",
        "kaleidoscope_cookery:flex_pot/sweet_and_sour_pork",
        "kaleidoscope_cookery:flex_stockpot/beef_meatball_soup",
        "kaleidoscope_cookery:flex_stockpot/beef_noodle",
        "kaleidoscope_cookery:flex_stockpot/borscht",
        "kaleidoscope_cookery:flex_stockpot/braised_beef_with_potatoes",
        "kaleidoscope_cookery:flex_stockpot/brown_mushroom_pot_soup",
        "kaleidoscope_cookery:flex_stockpot/buddha_jumps_over_the_wall",
        "kaleidoscope_cookery:flex_stockpot/chicken_and_mushroom_stew",
        "kaleidoscope_cookery:flex_stockpot/crimson_fungus_pot_soup",
        "kaleidoscope_cookery:flex_stockpot/dough_drop_soup",
        "kaleidoscope_cookery:flex_stockpot/fearsome_thick_soup",
        "kaleidoscope_cookery:flex_stockpot/four_joy_meatball_soup",
        "kaleidoscope_cookery:flex_stockpot/hot_dry_noodles",
        "kaleidoscope_cookery:flex_stockpot/hui_noodle",
        "kaleidoscope_cookery:flex_stockpot/laba_congee",
        "kaleidoscope_cookery:flex_stockpot/lamb_and_radish_soup",
        "kaleidoscope_cookery:flex_stockpot/numbing_spicy_chicken",
        "kaleidoscope_cookery:flex_stockpot/pork_bone_soup",
        "kaleidoscope_cookery:flex_stockpot/pufferfish_soup",
        "kaleidoscope_cookery:flex_stockpot/pufferfish_soup_entity",
        "kaleidoscope_cookery:flex_stockpot/red_mushroom_pot_soup",
        "kaleidoscope_cookery:flex_stockpot/seafood_miso_soup",
        "kaleidoscope_cookery:flex_stockpot/seafood_miso_soup_cod_entity",
        "kaleidoscope_cookery:flex_stockpot/seafood_miso_soup_salmon_entity",
        "kaleidoscope_cookery:flex_stockpot/seafood_miso_soup_tropical_entity",
        "kaleidoscope_cookery:flex_stockpot/spicy_blood_stew",
        "kaleidoscope_cookery:flex_stockpot/udon_noodle",
        "kaleidoscope_cookery:flex_stockpot/warped_fungus_pot_soup",
        "kaleidoscope_cookery:flex_stockpot/wild_mushroom_rabbit_soup");

    public FlexibleFormulation {
        Objects.requireNonNull(mode); Objects.requireNonNull(sourceRecipeId); Objects.requireNonNull(sourceRecipeType);
        evidence = List.copyOf(evidence);
        if (FIXED.equals(mode)) {
            if (!sourceRecipeId.isEmpty() || !sourceRecipeType.isEmpty() || !evidence.isEmpty()) throw new IllegalArgumentException("Fixed recipe has flexible metadata");
        } else if (FLEXIBLE.equals(mode)) {
            if (!SOURCE_RECIPES.contains(sourceRecipeId) || !sourceRecipeType.equals(sourceRecipeId.substring(0, sourceRecipeId.indexOf('/')))
                    || evidence.isEmpty() || evidence.size() > 16 || evidence.stream().mapToInt(String::length).sum() > 2048
                    || evidence.stream().anyMatch(s -> s.isBlank() || s.length() > 512)
                    || evidence.stream().distinct().count() != evidence.size()) throw new IllegalArgumentException("Unproven flexible recipe semantics");
            evidence = evidence.stream().sorted().toList();
        } else throw new IllegalArgumentException("Unknown native quality mode " + mode);
    }
    public static FlexibleFormulation fixed() { return new FlexibleFormulation(FIXED, "", "", List.of()); }
    public boolean flexible() { return FLEXIBLE.equals(mode); }
    private static void keys(JsonObject value, String... expected) {
        if (!value.keySet().equals(Set.of(expected))) throw new IllegalArgumentException("Unexpected native_quality fields " + value.keySet());
    }
    private static String string(JsonObject value, String key) {
        if (!value.has(key) || !value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isString()) throw new IllegalArgumentException("Expected native_quality string " + key);
        return value.get(key).getAsString();
    }
    private static void exact(JsonObject value, String key, String expected) {
        if (!string(value, key).equals(expected)) throw new IllegalArgumentException("Unsupported native_quality " + key);
    }
    public static FlexibleFormulation fromJson(JsonObject json) {
        if (json == null) return fixed();
        String mode = string(json, "mode");
        exact(json, "inheritance", INHERITANCE);
        if (FIXED.equals(mode)) { keys(json, "mode", "inheritance"); return fixed(); }
        if (!FLEXIBLE.equals(mode)) throw new IllegalArgumentException("Unknown native quality mode " + mode);
        keys(json, "mode", "inheritance", "input_basis", "seed_source", "source_recipe_id", "source_recipe_type", "formulation", "evidence");
        exact(json, "input_basis", "actual_paid_counted_inputs"); exact(json, "seed_source", "actual_runtime_world_seed");
        JsonObject formulation = json.getAsJsonObject("formulation");
        keys(formulation, "debit", "max_native_units", "ratios", "required_groups");
        exact(formulation, "debit", "one_count1_unit_per_occupied_input_slot");
        exact(formulation, "ratios", "native_user_selectable"); exact(formulation, "required_groups", "each_covered");
        if (!formulation.get("max_native_units").isJsonPrimitive() || !formulation.getAsJsonPrimitive("max_native_units").isNumber()
                || !formulation.get("max_native_units").getAsString().equals("9")) throw new IllegalArgumentException("Flexible maximum must be exactly nine");
        if (json.getAsJsonArray("evidence").size() > 16) throw new IllegalArgumentException("Too many quality evidence entries");
        List<String> evidence = new ArrayList<>();
        for (var entry : json.getAsJsonArray("evidence")) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid quality evidence");
            evidence.add(entry.getAsString());
        }
        return new FlexibleFormulation(mode, string(json, "source_recipe_id"), string(json, "source_recipe_type"), evidence);
    }
    public JsonObject toJson() {
        JsonObject json = new JsonObject(); json.addProperty("mode", mode); json.addProperty("inheritance", INHERITANCE);
        if (flexible()) {
            json.addProperty("input_basis", "actual_paid_counted_inputs"); json.addProperty("seed_source", "actual_runtime_world_seed");
            json.addProperty("source_recipe_id", sourceRecipeId); json.addProperty("source_recipe_type", sourceRecipeType);
            JsonObject f = new JsonObject(); f.addProperty("debit", "one_count1_unit_per_occupied_input_slot"); f.addProperty("max_native_units", MAX_UNITS);
            f.addProperty("ratios", "native_user_selectable"); f.addProperty("required_groups", "each_covered"); json.add("formulation", f);
            var list = new com.google.gson.JsonArray(); evidence.forEach(list::add); json.add("evidence", list);
        }
        return json;
    }
    public String fingerprint() { return toJson().toString(); }
    public static FlexibleFormulation parse(String encoded) { return fromJson(JsonParser.parseString(encoded).getAsJsonObject()); }

    /** Unit-capacity augmenting-path max flow: every required group gets a DISTINCT paid unit. */
    public static boolean covers(boolean[][] matches) {
        if (matches.length < 1 || matches.length > MAX_UNITS || matches[0].length < 1 || matches[0].length > MAX_UNITS) return false;
        int units = matches[0].length;
        for (boolean[] row : matches) if (row.length != units) throw new IllegalArgumentException("Nonrectangular matching matrix");
        for (int unit = 0; unit < units; unit++) {
            boolean allowed = false; for (boolean[] row : matches) allowed |= row[unit];
            if (!allowed) return false;
        }
        int[] owner = new int[units]; Arrays.fill(owner, -1);
        for (int group = 0; group < matches.length; group++) if (!augment(group, matches, owner, new boolean[units])) return false;
        return true;
    }
    private static boolean augment(int group, boolean[][] matches, int[] owner, boolean[] visited) {
        for (int unit = 0; unit < owner.length; unit++) if (matches[group][unit] && !visited[unit]) {
            visited[unit] = true;
            if (owner[unit] < 0 || augment(owner[unit], matches, owner, visited)) { owner[unit] = group; return true; }
        }
        return false;
    }
}
