import copy
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from recipe_station_migration import lower_cutting, build_cutting_decisions, lower_cooking
from compile_station_plan import annotate
from recipe_policy import fingerprint


class SourceOutputComparisonTest(unittest.TestCase):
    def test_primary_output_does_not_hide_a_distinct_byproduct(self):
        meat = {'item': 'a:meat', 'count': 2}
        baseline = {'recipes': {
            'a:fd': {'type': 'farmersdelight:cutting', 'result': [meat, {'item': 'a:bone'}]},
            'a:kc': {'type': 'kaleidoscope_cookery:chopping_board', 'result': [meat]}}}
        live = {'recipes': [{'id': key, 'outputs': [{'id': 'a:meat', 'count': 2}]} for key in baseline['recipes']]}
        decisions = {'routes': {'a:fd': {'action': 'redirect'}, 'a:kc': {'action': 'retain'}}}
        annotate(decisions, baseline, live, {})
        self.assertEqual(decisions['routes']['a:fd']['same_output_vector_routes'], [])
        self.assertEqual(decisions['routes']['a:fd']['source_output_vector'][1]['id'], 'a:bone')


class NativeCookingTest(unittest.TestCase):
    def setUp(self):
        self.raw = {'type': 'farmersdelight:cooking', 'ingredients': [{'item': 'a:meat'}], 'result': {'item': 'a:soup', 'count': 2, 'nbt': '{quality:1}'}}
        self.base = {'recipes': {'a:cook': self.raw}, 'protected_recipe_ids': []}
        self.choice = {'serializer': 'kaleidoscope_cookery:stockpot', 'soup_base': 'minecraft:water', 'source_sha256': fingerprint(self.raw)}
        self.facts = {'cooking': {'a:cook': {'container': 'minecraft:bowl', 'container_count': 1, 'container_nbt': None, 'cookingtime': 200}},
                      'items': {'a:meat': {'crafting_remainder': None, 'remainder_count': 0, 'remainder_nbt': '{Damage:0}', 'kaleidoscope_container': 'minecraft:air'},
                                'a:soup': {'kaleidoscope_container': 'minecraft:bowl'}}}

    def lower(self):
        self.choice['source_sha256'] = fingerprint(self.raw)
        return lower_cooking('a:cook', self.raw, self.choice, self.base, {}, self.facts)

    def test_portions_nbt_carrier_time_preserved(self):
        result = self.lower()
        self.assertEqual(result['result'], self.raw['result'])
        self.assertEqual(result['ingredients'], self.raw['ingredients'])
        self.assertEqual(result['carrier'], {'item': 'minecraft:bowl'})
        self.assertEqual(result['time'], 200)

    def test_output_over_native_portion_cap_rejected(self):
        self.raw['result']['count'] = 10
        with self.assertRaisesRegex(ValueError, 'capacity'): self.lower()

    def test_nonempty_nbt_remainder_rejected(self):
        self.facts['items']['a:meat'].update(crafting_remainder='a:vessel', remainder_count=1, remainder_nbt='{special:1}', kaleidoscope_container='a:vessel')
        with self.assertRaisesRegex(ValueError, 'remainder differs'): self.lower()

    def test_stir_fry_container_mismatch_rejected(self):
        self.choice['serializer'] = 'kaleidoscope_cookery:pot'
        self.facts['items']['a:soup']['kaleidoscope_container'] = 'minecraft:air'
        with self.assertRaisesRegex(ValueError, 'serving carrier differs'): self.lower()

    def test_overlapping_native_formulation_rejected(self):
        self.base['recipes']['a:existing'] = {'type': 'kaleidoscope_cookery:stockpot', 'ingredients': [{'item': 'a:meat'}], 'soup_base': 'minecraft:water'}
        with self.assertRaisesRegex(ValueError, 'ambiguous native formulation'): self.lower()


class NativeCuttingTest(unittest.TestCase):
    def setUp(self):
        self.raw = {'type': 'farmersdelight:cutting', 'ingredients': [{'item': 'a:pie'}],
                    'result': [{'item': 'a:slice', 'count': 7, 'nbt': '{flavour:1}'}, {'item': 'a:seed'}],
                    'tool': {'tag': 'forge:tools/knives'}, 'conditions': [{'type': 'forge:mod_loaded', 'modid': 'a'}]}
        self.base = {'protected_recipe_ids': [], 'recipes': {'a:cut': self.raw}}
        self.choice = {'model_item': 'a:pie', 'source_sha256': fingerprint(self.raw), 'reason': 'native portioning'}

    def lower(self):
        return lower_cutting('a:cut', self.raw, self.choice, self.base, {})

    def test_all_counts_nbt_byproducts_conditions_preserved(self):
        result = self.lower()
        self.assertEqual(result['result'], self.raw['result'])
        self.assertEqual(result['conditions'], self.raw['conditions'])
        self.assertEqual(result['ingredient'], self.raw['ingredients'][0])
        self.assertEqual(result['type'], 'kaleidoscope_cookery:chopping_board')

    def test_chance_output_not_discarded(self):
        self.raw['result'][1]['chance'] = 0.5
        with self.assertRaisesRegex(ValueError, 'probabilistic'): self.lower()

    def test_native_input_collision_rejected(self):
        self.base['recipes']['a:native'] = {'type': 'kaleidoscope_cookery:chopping_board', 'ingredient': {'item': 'a:pie'}}
        with self.assertRaisesRegex(ValueError, 'ambiguous'): self.lower()

    def test_protected_acquisition_rejected(self):
        self.raw['ingredients'][0] = {'item': 'a:wild_carrots'}
        with self.assertRaisesRegex(ValueError, 'wild-crop'): self.lower()

    def test_stale_authorship_rejected(self):
        self.choice['source_sha256'] = 'old'
        with self.assertRaisesRegex(ValueError, 'stale'): build_cutting_decisions(self.base, {}, {'a:cut': self.choice})

    def test_native_fd_knife_action_is_a_recognized_knife_predicate(self):
        self.raw['tool'] = [{'type': 'farmersdelight:tool_action', 'action': 'knife_dig'}, {'tag': 'forge:tools/knives'}]
        self.assertEqual(self.lower()['type'], 'kaleidoscope_cookery:chopping_board')

    def test_mixed_knife_and_axe_cannot_lose_tool_alternatives(self):
        self.raw['tool'] = [{'tag': 'forge:tools/knives'}, {'tag': 'forge:tools/axes'}]
        with self.assertRaisesRegex(ValueError, 'knife'): self.lower()

    def test_nonknife_tool_rejected(self):
        self.raw['tool'] = {'tag': 'forge:tools/axes'}
        with self.assertRaisesRegex(ValueError, 'knife'): self.lower()
