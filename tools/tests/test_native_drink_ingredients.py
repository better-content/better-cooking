import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from native_drink_ingredients import capture
from native_import import load_native, import_native
from native_json import read_bounded, parse_raw
from native_import_v2 import validate_snapshot
from test_native_import_v2 import observation
from test_native_import import native
from test_policy import fixture


def inputs():
    frozen = fixture()
    frozen['nonstandard_consumables'] = {'consumables': {}, 'ingredient_inputs': {
        'exact_item_ids': ['minecraft:milk_bucket', 'minecraft:water_bucket'],
        'live_item_tags': ['test:flowers'], 'source_tag_candidates': {'test:flowers': ['test:old']}}}
    # UNIT-ONLY synthetic protocol data; never an actual native snapshot/receipt.
    row = {'id': 'minecraft:milk_bucket', 'input_template': '{Count:1b,id:"minecraft:milk_bucket"}',
           'observation_kind': 'observed_template', 'template_basis': 'native_default',
           'item_class': 'unit.synthetic.Item', 'is_edible': False, 'food_properties': None, 'gaps': {},
           'nutrients': None, 'sugar': None, 'alcohol': 0, 'spice': None,
           'paid_intake': {'complete': False, 'nutrients': None, 'sugar': None, 'alcohol': None, 'spice': None, 'reason': 'unavailable'}}
    snapshot = {'native_drink_ingredients': [row], 'item_tags': {'test:flowers': ['test:live']},
                'native_drink_ingredients_context': {'schema': 'better_cooking:native_drink_ingredients.v2',
                    'status': 'observed_getters_unbound_runtime', 'qualification': False, 'auto_install': False}}
    return frozen, snapshot, row


class NativeDrinkIngredientTests(unittest.TestCase):
    def test_source_candidates_never_automatically_install(self):
        f, s, _ = inputs(); result = capture(f, s)
        self.assertIsNone(result['binding'])
        self.assertEqual(result['status'], 'observed_getters_unbound_runtime')
        self.assertTrue(result['unresolved'])

    def test_missing_rows_do_not_reuse_stale_defaults(self):
        f, s, _ = inputs(); f['native_drink_ingredients'] = capture(f, s)
        self.assertEqual(capture(f, {})['native_drink_ingredients'], [])

    def test_raw_null_not_empty_and_not_paid_milk_profile(self):
        f, s, row = inputs()
        row['paid_intake'] = {'complete': True, 'nutrients': {'dairy': .06, 'fats': .06}, 'sugar': 0, 'alcohol': 0, 'spice': 0}
        result = capture(f, s)['native_drink_ingredients'][0]
        self.assertIsNone(result['nutrients'])
        self.assertEqual(result['paid_intake']['nutrients'], {'dairy': .06, 'fats': .06})

    def test_explicit_observed_empty_map_remains_empty(self):
        f, s, row = inputs(); row['nutrients'] = {}
        self.assertEqual(capture(f, s)['native_drink_ingredients'][0]['nutrients'], {})

    def test_live_tag_not_frozen_source_candidate_members(self):
        f, s, row = inputs(); row['id'] = 'test:old'
        with self.assertRaisesRegex(ValueError, 'explicit/live'): capture(f, s)
        row['id'] = 'test:live'; row['input_template'] = '{id:"test:live",Count:1b}'
        self.assertEqual(capture(f, s)['native_drink_ingredients'][0]['id'], 'test:live')

    def test_duplicate_default_id_refused(self):
        f, s, row = inputs(); s['native_drink_ingredients'].append(copy.deepcopy(row))
        with self.assertRaisesRegex(ValueError, 'duplicate'): capture(f, s)

    def test_full_native_snbt_preserved_no_tag_absence_heuristic(self):
        f, s, row = inputs(); row['input_template'] = '{tag:{foo:"quoted value"},id:"minecraft:milk_bucket",Count:1b}'
        self.assertEqual(capture(f, s)['native_drink_ingredients'][0]['input_template'], row['input_template'])

    def test_complete_requires_all_flat_actual_doses(self):
        f, s, row = inputs(); row['paid_intake'] = {'complete': True, 'nutrients': {}, 'sugar': 0, 'alcohol': 0, 'spice': None}
        with self.assertRaisesRegex(ValueError, 'complete paid spice'): capture(f, s)

    def test_complete_cannot_fake_missing_nutrients_empty(self):
        f, s, row = inputs(); row['paid_intake'] = {'complete': True, 'nutrients': None, 'sugar': 0, 'alcohol': 0, 'spice': 0}
        with self.assertRaisesRegex(ValueError, 'explicit nutrient map'): capture(f, s)

    def test_paid_ordinary_nutrients_cannot_double_credit_spice(self):
        f, s, row = inputs(); row['paid_intake'] = {'complete': True, 'nutrients': {'spice': 1}, 'sugar': 0, 'alcohol': 0, 'spice': 1}
        with self.assertRaisesRegex(ValueError, 'duplicates Spice'): capture(f, s)

    def test_bad_raw_numerics_not_accepted_as_facts(self):
        for value in (True, -1, float('nan'), float('inf')):
            f, s, row = inputs(); row['sugar'] = value
            with self.assertRaises(ValueError): capture(f, s)

    def test_complete_flag_is_boolean_not_truthy_string(self):
        f, s, row = inputs(); row['paid_intake']['complete'] = 'true'
        with self.assertRaisesRegex(ValueError, 'boolean'): capture(f, s)

    def test_v1_context_and_literal_default_stack_are_rejected(self):
        f, s, row = inputs(); s['native_drink_ingredients_context']['schema'] = 'better_cooking:native_drink_ingredients.v1'
        with self.assertRaisesRegex(ValueError, 'v2 collector context'): capture(f, s)
        f, s, row = inputs(); row['default_stack'] = row.pop('input_template')
        with self.assertRaisesRegex(ValueError, 'exact 13 fields'): capture(f, s)

    def test_water_is_provider_variant_only_and_never_paid_material(self):
        f, s, row = inputs(); row['id'] = 'minecraft:water_bucket'
        row['input_template'] = '{Count:1b,id:"minecraft:water_bucket",tag:{Purity:3}}'
        with self.assertRaisesRegex(ValueError, 'template_basis'): capture(f, s)
        row['template_basis'] = 'provider_purity3_variant'
        result = capture(f, s)
        self.assertEqual(result['native_drink_ingredients'], [row])
        self.assertIsNone(result['binding'])
        self.assertFalse(result['auto_install'])
        self.assertFalse(result['qualification'])

    def test_provider_variant_cannot_label_milk_or_other_defaults(self):
        f, s, row = inputs(); row['template_basis'] = 'provider_purity3_variant'
        with self.assertRaisesRegex(ValueError, 'template_basis'): capture(f, s)

    def test_entire_actual_row_projection_is_copy_not_selected_metadata(self):
        f, s, row = inputs(); row['gaps'] = {'spice': 'unit unknown'}
        result = capture(f, s)['native_drink_ingredients'][0]
        self.assertEqual(result, row)
        result['gaps']['spice'] = 'mutated'
        self.assertEqual(row['gaps']['spice'], 'unit unknown')

    def test_missing_or_extra_fields_and_observation_kind_rejected(self):
        for mutate in (lambda r:r.pop('gaps'),lambda r:r.update(extra=1),lambda r:r.update(observation_kind='default')):
            f, s, row = inputs(); mutate(row)
            with self.assertRaises(ValueError): capture(f, s)

    def test_partial_paid_unknown_cannot_be_zero_or_missing_or_reasonless(self):
        for mutate in (lambda p:p.update(sugar=0),lambda p:p.pop('spice'),lambda p:p.update(reason=''),lambda p:p.update(reason=None)):
            f, s, row = inputs(); mutate(row['paid_intake'])
            with self.assertRaises(ValueError): capture(f, s)

    def test_observation_context_cannot_claim_qualification_or_auto_install(self):
        for field in ('qualification','auto_install'):
            f, s, row = inputs(); s['native_drink_ingredients_context'][field] = True
            with self.assertRaisesRegex(ValueError, 'v2 collector context'): capture(f, s)

    def test_original_raw_hash_not_canonical_and_v1_never_imported(self):
        f, _, _ = inputs()
        raw = b'{"schema":"better_cooking:native_catalogue.v1","foods":[]}\n\n'
        with tempfile.TemporaryDirectory(dir=Path.home() / '.tmp') as temp:
            path = Path(temp) / 'snapshot.json'; path.write_bytes(raw)
            original = read_bounded(path); parsed = parse_raw(original)
            with self.assertRaises(ValueError): load_native(f, path)
        self.assertEqual(hashlib.sha256(original).hexdigest(), hashlib.sha256(raw).hexdigest())
        self.assertNotEqual(hashlib.sha256(original).hexdigest(), hashlib.sha256(json.dumps(parsed, sort_keys=True).encode()).hexdigest())
        with self.assertRaisesRegex(ValueError, 'schema'): import_native(f, parsed)

    def test_v2_structural_drink_rows_retained_not_installed(self):
        f, s, row = inputs(); full = observation(); full.update(s)
        before = copy.deepcopy(row); self.assertIs(validate_snapshot(full, f), full)
        self.assertEqual(full['native_drink_ingredients'][0], before)
        self.assertNotIn('native_drink_ingredients', f)
        with self.assertRaisesRegex(ValueError, 'binding'): import_native(f, full)

    def test_v2_drink_per_field_negatives_not_just_schema_rejection(self):
        changes = [lambda r:r.update(input_template=''), lambda r:r.update(sugar=True),
                   lambda r:r.update(sugar=-1), lambda r:r.update(template_basis='provider_purity3_variant'),
                   lambda r:r.pop('gaps'), lambda r:r.update(default_stack='fake'),
                   lambda r:r['paid_intake'].update(complete='true'),
                   lambda r:r['paid_intake'].update(sugar=0),
                   lambda r:r.update(food_properties={'hunger': True, 'saturation_modifier': 0})]
        for change in changes:
            f, s, row = inputs(); change(row); full = observation(); full.update(s)
            with self.assertRaises(ValueError): validate_snapshot(full, f)


if __name__ == '__main__': unittest.main()
