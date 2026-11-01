import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('recipe_catalogue', Path(__file__).resolve().parents[1] / 'recipe_catalogue.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class RecipeCatalogueTest(unittest.TestCase):
    def setUp(self):
        self.edible = {'snapshot_id': 'fresh', 'items': {'a:food': {'nutrition': 3}}}
        self.registry = {'items': {'a:food': {}, 'a:vessel': {}}}
        self.old = {'foods': {'a:food': {'role': 'unresolved'}, 'a:vessel': {}, 'a:removed': {}}}
        self.roles = {'groups': [{'classification': 'ingredient', 'reason': 'native ingredient', 'items': ['a:food']}]}

    def test_exact_live_coverage_and_historical_dispositions(self):
        result = m.build(self.edible, self.registry, self.old, self.roles)
        self.assertEqual(set(result['items']), {'a:food'})
        self.assertEqual(result['items']['a:food']['classification'], 'ingredient')
        self.assertTrue(result['items']['a:food']['no_change'])
        self.assertEqual(result['historical_only']['a:vessel']['status'], 'registered_nondefault_food_context')
        self.assertEqual(result['historical_only']['a:removed']['status'], 'absent_from_current_registry')

    def test_unknown_is_not_automatically_classified(self):
        with self.assertRaisesRegex(ValueError, 'missing explicit'):
            m.build(self.edible, self.registry, self.old, {'groups': []})

    def test_duplicate_authorship_rejected(self):
        self.roles['groups'] *= 2
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            m.build(self.edible, self.registry, self.old, self.roles)

    def test_stale_override_rejected(self):
        self.roles['groups'][0]['items'].append('a:absent')
        with self.assertRaisesRegex(ValueError, 'absent'):
            m.build(self.edible, self.registry, self.old, self.roles)
