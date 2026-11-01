"""Migrated v1 admission tests: v1 refuses; v2 structure stays UNTRUSTED.

Food/effect/nutrient/tag/NBT/count/chance/unknown controls remain explicit. No
getter-to-profile installation is tested/approved in the all-quarantine phase.
"""
import copy
from decimal import Decimal
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from compile_policy import _compile_policy_model as compile_policy
from native_import import import_native
from native_import_v2 import validate_snapshot, derive_partition
from test_native_import_v2 import observation, provenance, baseline


def native():
    value = observation(); value['schema'] = 'better_cooking:native_catalogue.v1'
    return value  # Historical version rejection input ONLY; never relabel/import.


class NativeImportTests(unittest.TestCase):
    def setUp(self): self.source = baseline(); self.snapshot = observation()
    def check(self): return validate_snapshot(self.snapshot)
    def partition(self): return derive_partition(self.snapshot, provenance(), self.source)
    def test_exact_foods_do_not_replace_source_declarations(self):
        before = copy.deepcopy(self.source); self.check()
        self.assertEqual(self.source, before)
        with self.assertRaises(ValueError): import_native(self.source, self.snapshot)
    def test_same_class_not_kc_fixed_flex_identity_authority(self):
        row = self.snapshot['recipes'][0]
        row['resource_json'] = {'type': 'kaleidoscope_cookery:flex_pot'}
        self.assertEqual(self.partition()['admitted_recipe_ids'], [])
        self.assertEqual(row['native_recipe_type']['ref'], 't0')
    def test_exact_rows_do_not_preserve_historical_effective_graph(self):
        p = self.partition()
        self.assertEqual(p['source_recipe_ids'], ['example:historical'])
        self.assertEqual(p['quarantined_recipe_ids'], ['example:new_apple'])
        self.assertEqual(p['admitted_recipe_ids'], [])
    def test_resolves_no_native_profile_but_retains_exact_getters(self):
        self.check(); row = self.snapshot['foods'][0]
        self.assertEqual(row['hunger'], 5); self.assertEqual(row['nutrients'], {'fruits': Decimal('.5')})
        self.assertEqual(row['effects'][0]['chance'], Decimal('.2'))
    def test_no_qualification_from_dump_alone(self):
        self.assertFalse(self.partition()['qualification'])
        with self.assertRaises(ValueError): import_native(self.source, self.snapshot, binding={'validated': True})
    def test_input_snapshot_and_source_not_mutated(self):
        before = copy.deepcopy((self.source, self.snapshot)); self.partition()
        self.assertEqual((self.source, self.snapshot), before)
    def test_native_tags_preserved_without_replacing_source(self):
        self.source['item_tags'] = {'test:source': ['test:item']}; self.check()
        self.assertEqual(self.source['item_tags'], {'test:source': ['test:item']})
        self.assertEqual(self.snapshot['item_tags'], {'forge:fruits/apple': ['example:new_apple']})
    def test_native_ingredient_count_preserved(self):
        self.check(); self.assertEqual(self.snapshot['recipes'][0]['ingredients'][0]['count'], 4)
    def test_missing_nonfood_tag_coverage_rejected_not_defaulted(self):
        del self.snapshot['item_tags']
        with self.assertRaises(ValueError): self.check()
    def test_missing_registry_rejected_not_source_repaired(self):
        del self.snapshot['registered_items']
        with self.assertRaises(ValueError): self.check()
    def test_native_errors_never_erased(self):
        self.snapshot['gaps'] = [{'code': 'throwing_food_properties', 'item': 'example:broken'}]
        self.partition(); self.assertEqual(self.snapshot['gaps'][0]['code'], 'throwing_food_properties')
    def test_row_references_carry_actual_supplied_raw_hash(self):
        self.assertEqual(self.partition()['raw_sha256'], provenance()['raw_sha256'])
        self.assertEqual(self.partition()['ledger'][0]['ordinal'], 0)
    def test_signed_native_fact_is_retained_not_clamped_or_approved(self):
        self.snapshot['foods'][0]['hunger'] = -4; self.check()
        self.assertEqual(self.snapshot['foods'][0]['hunger'], -4)
        self.assertEqual(self.partition()['admitted_recipe_ids'], [])
    def test_reject_missing_effects_without_gap(self):
        del self.snapshot['foods'][0]['effects']
        with self.assertRaisesRegex(ValueError, 'effects'): self.check()
    def test_reject_missing_nutrients_without_gap(self):
        del self.snapshot['foods'][0]['nutrients']
        with self.assertRaisesRegex(ValueError, 'nutrients'): self.check()
    def test_reject_all_legacy_and_diagnostic_schemas(self):
        for schema in ('better_cooking:native_catalogue.v1', 'better_cooking:native_catalogue_recipe_identity_diagnostic.v1', 'example:old'):
            s = copy.deepcopy(self.snapshot); s['schema'] = schema
            with self.assertRaisesRegex(ValueError, 'schema'): import_native(self.source, s)
    def test_reject_duplicate_foods(self):
        self.snapshot['foods'].append(copy.deepcopy(self.snapshot['foods'][0]))
        with self.assertRaisesRegex(ValueError, 'duplicate native food'): self.check()
    def test_reject_duplicate_recipes(self):
        self.snapshot['recipes'].append(copy.deepcopy(self.snapshot['recipes'][0]))
        with self.assertRaisesRegex(ValueError, 'duplicate native recipe'): self.check()
    def test_reject_nonfinite_nutrition(self):
        for bad in (Decimal('Infinity'), Decimal('NaN'), True):
            self.snapshot['foods'][0]['saturation_modifier'] = bad
            with self.assertRaises(ValueError): self.check()
    def test_reject_bad_effect_probability(self):
        self.snapshot['foods'][0]['effects'][0]['chance'] = Decimal('1.5')
        with self.assertRaisesRegex(ValueError, 'probability'): self.check()
    def test_reject_fractional_boolean_or_decimal_hunger(self):
        for bad in (Decimal('1.5'), Decimal('5.0'), True):
            self.snapshot['foods'][0]['hunger'] = bad
            with self.assertRaisesRegex(ValueError, 'integer'): self.check()
    def test_unreviewed_machine_not_assumed_safe(self):
        self.snapshot['recipes'][0]['resource_json'] = {'type': 'create:mixing'}
        self.assertIn('backend_contract_not_approved', self.partition()['ledger'][0]['reasons'])
    def test_fd_carrier_fact_preserved_not_waived(self):
        self.snapshot['recipes'][0]['container'] = {'id': 'minecraft:bowl', 'count': 1}
        self.partition(); self.assertEqual(self.snapshot['recipes'][0]['container']['count'], 1)
    def test_no_unproven_serving_override(self):
        self.snapshot['servings'] = {'example:new_apple': {'portions': 8}}
        with self.assertRaises(ValueError): self.check()
    def test_even_evidenced_native_servings_outer_override_rejected(self):
        self.snapshot['servings'] = {'example:new_apple': {'portions': 3, 'evidence': ['synthetic:completions']}}
        with self.assertRaises(ValueError): self.check()
    def test_global_resource_gap_does_not_explain_food_unknown(self):
        del self.snapshot['foods'][0]['nutrients']; self.snapshot['gaps'] = [{'scope': 'resource_inputs', 'id': '*', 'field': 'hashes'}]
        with self.assertRaisesRegex(ValueError, 'nutrients'): self.check()
    def test_explained_missing_nutrients_remain_absent(self):
        del self.snapshot['foods'][0]['nutrients']; self.snapshot['gaps'] = [{'scope': 'nutrients', 'id': 'example:new_apple', 'reason': 'unavailable'}]
        self.check(); self.assertNotIn('nutrients', self.snapshot['foods'][0])
    def test_explained_null_effects_stay_unknown_not_empty(self):
        self.snapshot['foods'][0]['effects'] = None; self.snapshot['gaps'] = [{'scope': 'effects', 'id': 'example:new_apple', 'reason': 'throwing getter'}]
        self.check(); self.assertIsNone(self.snapshot['foods'][0]['effects'])
    def test_nonfood_block_mapping_not_recipe_drop_or_portion_inference(self):
        self.snapshot['block_items'] = {'minecraft:cake': {'block_id': 'minecraft:cake', 'block_class': 'synthetic.CakeBlock'}}
        self.snapshot['recipes'][0]['result'] = {'id': 'minecraft:cake', 'count': 1}
        self.assertEqual(self.partition()['recipe_count'], 1)
        self.assertNotIn('servings', self.snapshot)
    def test_tag_orientation_explicit_and_bad_members_reject(self):
        self.check(); self.snapshot['food_tags'] = {'forge:fruits/apple': ['NOT an ID']}
        with self.assertRaises(ValueError): self.check()
    def test_partial_recipe_retained_even_without_result(self):
        del self.snapshot['recipes'][0]['result']; self.snapshot['gaps'] = [{'scope': 'recipe', 'id': 'example:new_apple', 'field': 'result'}]
        self.assertEqual(self.partition()['recipe_count'], 1)
        self.assertNotIn('result', self.snapshot['recipes'][0])
    def test_unknown_recipe_fields_not_defaulted_without_gap(self):
        del self.snapshot['recipes'][0]['ingredients']; del self.snapshot['recipes'][0]['result']
        self.assertEqual(self.partition()['quarantined_recipe_ids'], ['example:new_apple'])
    def test_typed_multioutputs_chances_and_tag_nbt_preserved(self):
        row = self.snapshot['recipes'][0]
        row['results'] = [{'id': 'example:new_apple', 'count': 3, 'chance': Decimal('.5'), 'nbt': '{Utility:1b}'}, {'id': 'minecraft:apple', 'count': 1, 'chance': Decimal('.1')}]
        self.partition(); self.assertEqual(row['results'][0]['nbt'], '{Utility:1b}')
        self.assertEqual(row['results'][1]['chance'], Decimal('.1'))
    def test_teapot_count_not_assumed_one(self):
        row = self.snapshot['recipes'][0]; row.update(native_ingredient={'item': 'minecraft:apple'}, ingredient_count=12, native_output_count_constant=3)
        self.partition(); self.assertEqual(row['ingredient_count'], 12); self.assertEqual(row['native_output_count_constant'], 3)
    def test_spice_raw_group_retained_not_paid_ordinary_credit(self):
        self.snapshot['foods'][0]['nutrients']['spice'] = 5; self.check()
        self.assertEqual(self.snapshot['foods'][0]['nutrients']['spice'], 5)
        self.assertEqual(self.partition()['admitted_recipe_ids'], [])
    def test_no_native_portion_division_or_bowl_default(self):
        row = self.snapshot['recipes'][0]; row['container'] = None
        self.partition(); self.assertIsNone(row['container'])
        self.assertEqual(self.snapshot['foods'][0]['nutrients']['fruits'], Decimal('.5'))
    def test_cli_contract_has_all_three_native_flags_without_executing_cli(self):
        text = (Path(__file__).resolve().parents[1] / 'compile_policy.py').read_text()
        for flag in ('--native-snapshot', '--native-binding', '--native-binding-sha256'):
            self.assertIn(flag, text)
        self.assertIn('ALL-or-NONE', text)


if __name__ == '__main__': unittest.main()
