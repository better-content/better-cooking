import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('recipe_policy', Path(__file__).resolve().parents[1] / 'recipe_policy.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class RecipePolicyTest(unittest.TestCase):
    def setUp(self):
        self.raw = {'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'a:raw'}], 'result': {'item': 'a:food', 'count': 2}}
        self.baseline = {'schema': 'better_cooking:recipe_baseline.v1', 'edible_items': {'a:food': {}},
                         'source_identity': {'run': 'test'}, 'recipes': {'a:food': self.raw},
                         'affected_recipe_ids': ['a:food'], 'serializers': ['minecraft:crafting_shapeless']}
        self.decisions = {'schema': 'better_cooking:recipe_decisions.v1', 'baseline_sha256': m.fingerprint(self.baseline),
                          'foods': {'a:food': {'classification': 'staple', 'no_change': True, 'reason': 'retain native staple'}},
                          'routes': {'a:food': {'action': 'retain', 'reason': 'native staple', 'source_sha256': m.fingerprint(self.raw)}}}

    def test_retention_needs_no_physiology_qualification(self):
        result = m.compile_policy(self.baseline, self.decisions)
        self.assertEqual(result['schema'], m.SCHEMA)
        self.assertEqual(result['remove_ids'], [])
        self.assertNotIn('qualification', result)

    def test_stale_baseline_rejected(self):
        self.baseline['source_identity']['run'] = 'changed'
        with self.assertRaisesRegex(ValueError, 'stale baseline'):
            m.compile_policy(self.baseline, self.decisions)

    def test_missing_catalogue_row_rejected(self):
        self.decisions['foods'].clear()
        with self.assertRaisesRegex(ValueError, 'catalogue'):
            m.compile_policy(self.baseline, self.decisions)

    def test_missing_route_rejected(self):
        self.decisions['routes'].clear()
        with self.assertRaisesRegex(ValueError, 'route decisions'):
            m.compile_policy(self.baseline, self.decisions)

    def test_remove_without_canonical_rejected(self):
        self.decisions['routes']['a:food']['action'] = 'remove'
        with self.assertRaisesRegex(ValueError, 'canonical'):
            m.compile_policy(self.baseline, self.decisions)

    def test_same_id_redirect_preserves_json(self):
        row = self.decisions['routes']['a:food']; row['action'] = 'redirect'
        row['definitions'] = [{'id': 'a:food', 'json': copy.deepcopy(self.raw), 'preservation_review': 'same count, native item remainder, no extra outputs'}]
        result = m.compile_policy(self.baseline, self.decisions)
        self.assertEqual(result['definitions'][0]['json'], self.raw)
        self.assertEqual(result['remove_ids'], [])

    def test_custom_preparation_rejected(self):
        row = self.decisions['routes']['a:food']; row['action'] = 'replace'
        row['definitions'] = [{'id': 'a:new', 'json': {'type': 'better_cooking:preparation'}, 'preservation_review': 'test'}]
        with self.assertRaisesRegex(ValueError, 'serializer'):
            m.compile_policy(self.baseline, self.decisions)

    def test_collision_rejected(self):
        row = self.decisions['routes']['a:food']; row['action'] = 'replace'
        row['definitions'] = [{'id': 'a:food', 'json': self.raw, 'preservation_review': 'test'}]
        with self.assertRaisesRegex(ValueError, 'colliding'):
            m.compile_policy(self.baseline, self.decisions)

    def test_protected_drying_rejected(self):
        self.baseline['protected_recipe_ids'] = ['a:food']
        self.decisions['baseline_sha256'] = m.fingerprint(self.baseline)
        row = self.decisions['routes']['a:food']; row['action'] = 'redirect'
        row['definitions'] = [{'id': 'a:food', 'json': self.raw, 'preservation_review': 'test'}]
        with self.assertRaisesRegex(ValueError, 'protected'):
            m.compile_policy(self.baseline, self.decisions)
