import importlib.util
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from recipe_routes import redirect, retention


class RecipeRoutesTest(unittest.TestCase):
    def test_probabilities_nbt_fluids_and_duration_survive(self):
        raw = {'type': 'create:mixing', 'ingredients': [{'item': 'create:wheat_flour'}, {'fluid': 'minecraft:water', 'amount': 100}],
               'results': [{'item': 'create:dough', 'count': 3, 'nbt': '{test:1}', 'chance': 0.25}], 'processingTime': 200}
        value, changes = redirect(raw)
        self.assertEqual(value['ingredients'][0], [{'item': 'create:wheat_flour'}, {'item': 'kaleidoscope_cookery:flour'}])
        self.assertEqual(value['ingredients'][1], raw['ingredients'][1])
        self.assertEqual(value['results'][0], {'item': 'kaleidoscope_cookery:raw_dough', 'count': 3, 'nbt': '{test:1}', 'chance': 0.25})
        self.assertEqual(value['processingTime'], 200)
        self.assertEqual(raw['results'][0]['item'], 'create:dough')
        self.assertEqual(len(changes), 2)

    def test_constrained_ingredients_fail_closed(self):
        with self.assertRaisesRegex(ValueError, 'non-simple'):
            redirect({'ingredients': [{'item': 'create:dough', 'nbt': '{}'}]})

    def test_special_cutting_is_retained_not_miscompiled(self):
        family, reason = retention({'type': 'farmersdelight:cutting'}, {})
        self.assertEqual(family, 'complementary_native_cutting')
        self.assertIn('chance', reason)

    def test_nonfood_sequence_preserves_outputs_and_loops(self):
        raw = {'type': 'create:sequenced_assembly', 'loops': 2,
               'transitionalItem': {'item': 'a:unfinished'},
               'results': [{'item': 'a:circuit', 'chance': 100}],
               'sequence': [{'type': 'create:deploying', 'ingredients': [{'item': 'a:unfinished'}, {'item': 'create:dough'}], 'results': [{'item': 'a:unfinished'}]}]}
        changed, changes = redirect(raw, outputs=False)
        self.assertEqual(changed['results'], raw['results'])
        self.assertEqual(changed['transitionalItem'], raw['transitionalItem'])
        self.assertEqual(changed['loops'], 2)
        self.assertEqual(changed['sequence'][0]['results'], raw['sequence'][0]['results'])
        self.assertEqual(changed['sequence'][0]['ingredients'][1], [{'item': 'create:dough'}, {'item': 'kaleidoscope_cookery:raw_dough'}])
        self.assertEqual(len(changes), 1)

    def test_heat_finish_remains_native(self):
        family, reason = retention({'type': 'minecraft:smoking'}, {})
        self.assertEqual(family, 'native_heat_finish')
        self.assertIn('duration', reason)
