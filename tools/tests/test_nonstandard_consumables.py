import copy
import json
import sys
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from test_authored_reviews import reviewed_fixture
from compile_policy import _compile_policy_model as compile_policy
from nonstandard_consumables import attach_source, import_rows


def setup():
    frozen, reviews = reviewed_fixture()
    row = {'id': 'test:cup', 'kind': 'native_whole_drink', 'is_edible': False, 'food_properties': None,
           'item_class': 'test.NativeCup', 'use_animation': 'drink', 'use_duration_ticks': 32,
           'remainder': 'minecraft:glass_bottle', 'source_selected_effects': [], 'evidence': ['review-source#finish'],
           'source_finish_food_mutation': {'kind': 'none', 'normalized_target': {'hunger': 0, 'saturation_modifier': 0}}}
    document = {'schema': 'better_cooking:nonstandard_consumables.v1', 'source_inputs': copy.deepcopy(reviews['source_inputs']), 'consumables': {'test:cup': row}}
    return frozen, reviews, document


class NonstandardConsumableTests(unittest.TestCase):
    def test_source_profile_not_fake_food_properties_or_water(self):
        frozen, reviews, document = setup(); policy = compile_policy(attach_source(frozen, document), authored=reviews)
        food = policy['foods']['test:cup']
        self.assertEqual(food['role'], 'drink'); self.assertEqual(food['quantity_basis'], 'native_intake')
        self.assertEqual(food['nutrition']['hunger'], 0)  # normalized no FoodData operation, NOT getter
        self.assertIsNone(food['native_food']['nutrition']); self.assertIsNone(food['native_food_properties'])
        self.assertEqual(food['moisture'], {'hydration': None, 'quenching': None})
        self.assertEqual(food['servings'], 1); self.assertFalse(food['serving_metadata']['serving_count_proven'])
        self.assertIn('native_nonstandard_getters_not_enumerated', food['unresolved'])
        self.assertIn('native_nonstandard_sugar_dose_not_enumerated', food['unresolved'])
        self.assertEqual(policy['qualification'], 'unqualified')

    def test_source_hash_mismatch_refused(self):
        frozen, _, document = setup(); document['source_inputs']['review-source'] = 'b' * 64
        with self.assertRaisesRegex(ValueError, 'not bound'): attach_source(frozen, document)

    def test_null_getter_not_zero_food_object(self):
        frozen, _, document = setup(); document['consumables']['test:cup']['food_properties'] = {'hunger': 0}
        with self.assertRaisesRegex(ValueError, 'null food properties'): attach_source(frozen, document)

    def native(self, nutrients='absent'):
        frozen, _, document = setup(); frozen = attach_source(frozen, document); frozen['native_import_gaps'] = []
        row = {k: copy.deepcopy(document['consumables']['test:cup'][k]) for k in ('id', 'item_class', 'is_edible', 'food_properties', 'use_animation', 'use_duration_ticks', 'remainder')}
        row.update(sugar=None, alcohol=0, spice=None, native_effect_declarations=[])
        if nutrients != 'absent': row['nutrients'] = nutrients
        return frozen, row

    def test_absent_nutrients_not_fabricated_empty_map(self):
        frozen, row = self.native(); foods = {}; import_rows(frozen, {'native_consumables': [row]}, foods, 'snapshot')
        self.assertFalse(foods['test:cup']['nutrients_resolved']); self.assertIsNone(foods['test:cup']['nutrients'])

    def test_explicit_empty_native_nutrients_remain_distinct(self):
        frozen, row = self.native({}); foods = {}; import_rows(frozen, {'native_consumables': [row]}, foods, 'snapshot')
        self.assertTrue(foods['test:cup']['nutrients_resolved']); self.assertEqual(foods['test:cup']['nutrients'], {})
        self.assertIsNone(foods['test:cup']['native_consumption']['sugar'])
        self.assertEqual(foods['test:cup']['native_consumption']['alcohol'], 0)

    def test_wrong_getter_fails_closed(self):
        frozen, row = self.native(); row['is_edible'] = True
        with self.assertRaisesRegex(ValueError, 'getter/source mismatch'): import_rows(frozen, {'native_consumables': [row]}, {}, 'snapshot')

    def test_missing_snapshot_section_preserves_source_candidates_with_gap(self):
        frozen, _ = self.native(); foods = {}; import_rows(frozen, {}, foods, 'snapshot')
        self.assertIn('test:cup', foods)
        self.assertFalse(foods['test:cup'].get('native_nonstandard_base_getters_observed', False))
        self.assertTrue(frozen['native_import_gaps'])

    def test_duplicate_native_ids_refused(self):
        frozen, row = self.native()
        with self.assertRaisesRegex(ValueError, 'duplicate'): import_rows(frozen, {'native_consumables': [row, row]}, {}, 'snapshot')

    def test_new_snapshot_cannot_reuse_old_getter_or_diet_truth(self):
        frozen, row = self.native({}); foods = {}; import_rows(frozen, {'native_consumables': [row]}, foods, 'old')
        frozen['foods'].update(foods); fresh = {}; import_rows(frozen, {}, fresh, 'new')
        self.assertFalse(fresh['test:cup'].get('native_nonstandard_base_getters_observed', False))
        self.assertFalse(fresh['test:cup']['nutrients_resolved']); self.assertNotIn('native_consumption', fresh['test:cup'])

    def test_empty_source_binding_not_approved(self):
        frozen, _, document = setup(); document['source_inputs'] = {}
        with self.assertRaisesRegex(ValueError, 'source inputs required'): attach_source(frozen, document)

    def test_source_null_and_native_edible_declaration_cannot_collide(self):
        frozen, row = self.native()
        with self.assertRaisesRegex(ValueError, 'contradicts native FoodProperties'): import_rows(frozen, {'native_consumables': [row]}, {'test:cup': {}}, 'snapshot')

    def test_native_getters_do_not_claim_finish_or_clear_water_gap(self):
        frozen, row = self.native({}); foods = {}; import_rows(frozen, {'native_consumables': [row]}, foods, 'snapshot')
        frozen['foods'].update(foods); food = compile_policy(frozen)['foods']['test:cup']
        self.assertNotIn('native_nonstandard_getters_not_enumerated', food['unresolved'])
        self.assertTrue(any('require_connected_acceptance' in gap for gap in food['unresolved']))
        self.assertIsNone(food['moisture']['hydration'])

    def test_exact_nine_source_records_and_real_production_bases(self):
        document = json.loads((TOOLS / 'authored/nonstandard_consumables.json').read_text()); rows = document['consumables']
        self.assertEqual(len(rows), 9)
        self.assertTrue(all(row['food_properties'] is None and row['is_edible'] is False for row in rows.values()))
        self.assertEqual(rows['kaleidoscope_cookery:barley_tea']['source_production']['output_count'], 12)
        self.assertEqual(rows['kaleidoscope_cookery:mystery_tea']['source_production']['output_count'], 4)
        milk = rows['kaleidoscope_cookery:clay_pot_milk_tea']['source_production']
        self.assertEqual(milk['output_counts'], list(range(1, 10))); self.assertIsNone(milk['fluid_mb'])
        self.assertTrue(all(row['hydration'] is None and row['quenching'] is None for row in rows.values()))
        self.assertTrue(all(row['servings_per_item'] == 1 for row in rows.values()))

    def test_exact_input_selectors_and_live_tag_contract(self):
        document = json.loads((TOOLS / 'authored/nonstandard_consumables.json').read_text())
        selectors = document['ingredient_inputs']
        self.assertEqual(set(selectors['exact_item_ids']), {
            'kaleidoscope_cookery:barley_tea_bag', 'kaleidoscope_cookery:biluochun_tea_bag',
            'kaleidoscope_cookery:butter_tea_bag', 'kaleidoscope_cookery:oolong_tea_bag',
            'kaleidoscope_cookery:sakura_fubuki_tea_bag', 'kaleidoscope_cookery:tieguanyin_tea_bag',
            'minecraft:milk_bucket', 'minecraft:water_bucket'})
        self.assertEqual(selectors['live_item_tags'], ['kaleidoscope_cookery:flower_tea_ingredients'])
        self.assertTrue(selectors['source_tag_candidates_are_not_live_membership'])
        self.assertNotIn('kaleidoscope_cookery:flower_tea_ingredient', selectors['exact_item_ids'])

    def test_explicit_legacy_balance_not_native_getter_or_nutrition(self):
        f = json.loads((TOOLS / 'frozen/catalogue_inputs.json').read_text())
        document = json.loads((TOOLS / 'authored/nonstandard_consumables.json').read_text())
        policy = compile_policy(attach_source(f, document))
        mystery = policy['foods']['kaleidoscope_cookery:mystery_tea']
        self.assertEqual(mystery['moisture'], {'hydration': 0, 'quenching': 0})
        self.assertIsNone(mystery['nonstandard_consumable']['nutrients'])
        self.assertIsNone(mystery['native_food_properties'])
        water = policy['foods']['kaleidoscope_cookery:barley_tea']
        self.assertEqual(water['moisture'], {'hydration': 16 / 12, 'quenching': 20 / 12})
        milk = policy['foods']['kaleidoscope_cookery:clay_pot_milk_tea']
        self.assertEqual(milk['moisture'], {'hydration': 12 / 9, 'quenching': 16 / 9})
        self.assertTrue(milk['nonstandard_consumable']['legacy_balance_fallback']['invalid_or_unpaid_new_receipt_must_not_fallback'])
        self.assertEqual(policy['qualification'], 'unqualified')

    def test_source_native_assemble_twelve_not_resource_prototype_one(self):
        frozen, _, document = setup(); row = document['consumables']['test:cup']
        row['source_production'] = {'kind': 'native_teapot', 'recipe_ids': ['test:brew'], 'output_count': 12}
        frozen['recipes']['test:brew'] = {'outputs': [{'id': 'test:cup', 'kind': 'item', 'count': 1}], 'issues': []}
        value = attach_source(frozen, document)['recipes']['test:brew']
        self.assertEqual(value['outputs'][0]['count'], 12)
        self.assertEqual(value['native_declared_result_prototype'][0]['count'], 1)
        self.assertIn('source_native_assemble_yield_requires_connected_transaction_receipt', value['issues'])


if __name__ == '__main__': unittest.main()
