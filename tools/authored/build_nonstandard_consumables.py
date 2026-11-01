#!/usr/bin/env python3
"""Build nine explicitly reviewed KC1.6.0 native DRINK inputs, not edible food guesses."""
import argparse
import copy
import hashlib
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ['item/TeacupItem.java', 'item/ClayPotMilkTeaItem.java', 'api/item/IHasContainer.java',
           'init/ModItems.java', 'init/registry/CommonRegistry.java', 'init/registry/TeacupRegistry.java',
           'crafting/recipe/TeapotRecipe.java', 'blockentity/kitchen/TeapotBlockEntity.java',
           'blockentity/kitchen/StockpotBlockEntity.java', 'init/ModSoupBases.java']
TEAS = {
    'mystery_tea': [('minecraft:blindness', 300)],
    'barley_tea': [('kaleidoscope_cookery:vitality', 9600)],
    'tieguanyin': [('kaleidoscope_cookery:instant_smelting', 7200)],
    'biluochun': [('kaleidoscope_cookery:projectile_dodge', 7200)],
    'oolong': [('minecraft:slow_falling', 7200), ('minecraft:jump_boost', 7200)],
    'sakura_fubuki': [('kaleidoscope_cookery:hinder', 7200)],
    'butter_tea': [('kaleidoscope_cookery:projectile_dodge', 900)],
    'flower_tea': [('minecraft:regeneration', 400)],
}


def build(frozen, kc_sources, kc_jar):
    manifest = frozen['manifest']; inputs = {}
    for path in [kc_jar] + [kc_sources / 'com/github/ysbbbbbb/kaleidoscopecookery' / p for p in SOURCES]:
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if manifest.get(str(path.resolve())) != digest: raise ValueError('nonstandard source changed or is not frozen: ' + str(path))
        inputs[str(path.resolve())] = digest
    result = {'schema': 'better_cooking:nonstandard_consumables.v1', 'source_inputs': inputs,
              'status': 'source_reviewed_pending_native', 'consumables': {}}
    flower_tag = 'kaleidoscope_cookery:flower_tea_ingredients'
    with zipfile.ZipFile(kc_jar) as archive:
        flower_candidates = json.loads(archive.read('data/kaleidoscope_cookery/tags/items/flower_tea_ingredients.json'))['values']
    result['ingredient_inputs'] = {
        'exact_item_ids': sorted('kaleidoscope_cookery:' + name + '_tea_bag' for name in
                                ('barley', 'biluochun', 'butter', 'oolong', 'sakura_fubuki', 'tieguanyin')) + ['minecraft:milk_bucket', 'minecraft:water_bucket'],
        'live_item_tags': [flower_tag], 'source_tag_candidates': {flower_tag: flower_candidates},
        'source_tag_candidates_are_not_live_membership': True,
        'default_variant': 'actual_count1_ItemStack_save_nativeSNBT_with_exact_tag_absence_or_contents',
        'fluid_bucket_proxies_are_not_fluid_item_ids': True,
        'status': 'source_selectors_only_no_frozen_getter_facts'}
    for name in [*TEAS, 'clay_pot_milk_tea']:
        milk = name == 'clay_pot_milk_tea'; cls = 'ClayPotMilkTeaItem' if milk else 'TeacupItem'
        item = 'kaleidoscope_cookery:' + name
        evidence = ['kc1.6.0:' + cls + '#finishUsingItem', 'kc1.6.0:IHasContainer#returnContainerToEntity',
                    'kc1.6.0:ModItems#CLAY_POT_MILK_TEA' if milk else 'kc1.6.0:CommonRegistry#onBlockRegistryEvent',
                    'kc1.6.0:TeacupRegistry#init/' + name] if not milk else [
                        'kc1.6.0:ClayPotMilkTeaItem#finishUsingItem', 'kc1.6.0:IHasContainer#returnContainerToEntity', 'kc1.6.0:ModItems#CLAY_POT_MILK_TEA']
        if milk:
            production = {'kind': 'native_stockpot', 'recipe_ids': ['kaleidoscope_cookery:stockpot/clay_pot_milk_tea_count_' + str(n) for n in range(1, 10)],
                          'output_counts': list(range(1, 10)), 'paid_soup_base_items': 1, 'fluid_mb': None,
                          'dose_basis': 'actual_paid_soup_base_profile_divided_by_actual_realized_item_yield',
                          'evidence': ['kc1.6.0:StockpotBlockEntity#addSoupBase', 'kc1.6.0:ModSoupBases#registerAll']}
        elif name == 'mystery_tea':
            production = {'kind': 'native_teapot_unmatched_fallback_not_recipe_id', 'recipe_ids': [],
                          'source_action_id': 'kc1.6.0:TeapotBlockEntity#makeTea/unmatched',
                          'output_count': 4, 'fluid_mb': 1000,
                          'dose_basis': 'actual_paid_fluid_profile_divided_by_four_realized_cups'}
        else:
            production = {'kind': 'native_teapot', 'recipe_ids': ['kaleidoscope_cookery:teapot/' + name],
                          'output_count': 12, 'fluid_mb': 1000, 'fluid_id': 'minecraft:milk' if name == 'butter_tea' else 'minecraft:water',
                          'dose_basis': 'actual_paid_fluid_profile_divided_by_twelve_realized_cups',
                          'evidence': ['kc1.6.0:TeapotRecipe#assemble', 'kc1.6.0:TeapotBlockEntity#addTeaFluid']}
        production['creative_filling'] = 'unpaid_receipt_required_never_fallback_to_positive_legacy_entitlement'
        production['source_safety_status'] = 'actual_source_NBT_not_a_proof_of_purity_effect_delivery_or_heat_purification'
        production['source_safety_requires_native_closure'] = True
        if name == 'mystery_tea':
            production['accepted_source_fluids'] = 'any_registered_fluid_at_least_1000mB_including_lava_and_mod_fluids'
            production['dripstone_native_sources'] = ['minecraft:water', 'minecraft:lava']
            legacy_h, legacy_q, numerator, denominator = 0, 0, None, None
        elif milk: legacy_h, legacy_q, numerator, denominator = 12 / 9, 16 / 9, 1000, 9
        elif name == 'butter_tea': legacy_h, legacy_q, numerator, denominator = 12 / 12, 16 / 12, 1000, 12
        else: legacy_h, legacy_q, numerator, denominator = 16 / 12, 20 / 12, 1000, 12
        result['consumables'][item] = {'id': item, 'kind': 'native_whole_drink',
            'item_class': 'com.github.ysbbbbbb.kaleidoscopecookery.item.' + cls,
            'is_edible': False, 'food_properties': None, 'use_animation': 'drink', 'use_duration_ticks': 32,
            'remainder': 'minecraft:flower_pot' if milk else 'kaleidoscope_cookery:empty_cup',
            'completed_item_debit': 1, 'servings_per_item': 1, 'placed_group_max_is_not_servings': True,
            'source_finish_food_mutation': {'kind': 'none', 'basis': 'source_finish_has_no_FoodData_eat_call',
                'normalized_target': {'hunger': 0, 'saturation_modifier': 0},
                'status': 'source_only_not_observed_finish', 'evidence': [evidence[0]]},
            'source_selected_effects': [{'id': eid, 'duration': ticks, 'amplifier': 0, 'chance': 1} for eid, ticks in TEAS.get(name, [])],
            'source_utility': 'remove_all_active_harmful_effects_not_an_added_effect_dose' if milk else 'native_declared_tea_effects',
            'source_production': production, 'hydration': None, 'quenching': None, 'nutrients': None,
            'legacy_balance_fallback': {'hydration': legacy_h, 'quenching': legacy_q,
                'class': 'root_explicit_conservative_legacy_native_drink_balance',
                'reference_mb_numerator': numerator, 'yield_denominator': denominator,
                'authorization': 'Root wJ:p7 explicit 2026-10-28T13 R8-release decision; Hydro wR:p5 confirmed Water16/20 Milk12/16 per1000mB',
                'basis': 'oldstock_undercredit_policy_not_physical_paid_entitlement_or_native_getter',
                'new_receipt_overrides_legacy': True, 'invalid_or_unpaid_new_receipt_must_not_fallback': True,
                'unknown_nutrition_not_zero': True},
            'creative_policy': 'preserve_native_creative_action_no_paid_completion_credit',
            'intake_owner': 'better_cooking_native_whole_completion_not_Water_paid_reserve', 'evidence': evidence,
            'status': 'source_reviewed_pending_native'}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, default=ROOT / 'frozen/catalogue_inputs.json')
    parser.add_argument('--kc-sources', type=Path, required=True); parser.add_argument('--kc-jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=ROOT / 'authored/nonstandard_consumables.json')
    args = parser.parse_args()
    value = build(json.loads(args.input.read_text()), args.kc_sources.resolve(), args.kc_jar.resolve())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(value, sort_keys=True, ensure_ascii=False, indent=2) + '\n')
    print('source-reviewed nine nonedible native DRINK records; no native acceptance or invented water volume')


if __name__ == '__main__': main()
