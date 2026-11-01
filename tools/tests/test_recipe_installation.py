import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_recipe_installation import verify


class InstallationTest(unittest.TestCase):
    def setUp(self):
        self.raw = {'type': 'minecraft:crafting_shapeless', 'result': {'item': 'a:food'}}
        self.baseline = {'recipes': {'a:recipe': self.raw}, 'edible_items': {'a:food': {'nutrition': 1}}}
        self.policy = {'remove_ids': [], 'definitions': [{'id': 'a:recipe', 'json': self.raw}]}
        self.captured = {'recipes': {'a:recipe': self.raw}, 'errors': []}
        self.live = {'snapshot_id': 'fresh', 'recipes': [{'id': 'a:recipe', 'serializer': self.raw['type'], 'outputs': [{'id': 'a:food'}]}]}
        self.native = {'schema': 'bc.recipe_policy_verification.v1', 'status': 'passed', 'definition_count': 1,
                       'definitions': [{'id': 'a:recipe', 'status': 'passed', 'expected_network_sha256': 'same', 'actual_network_sha256': 'same'}]}
        self.edible = {'items': self.baseline['edible_items']}
        self.tags = {'item_tags': {'forge:flour/wheat': ['kaleidoscope_cookery:flour'], 'forge:dough/wheat': ['kaleidoscope_cookery:raw_dough']}}

    def run_check(self):
        return verify(self.baseline, self.policy, self.captured, self.live, self.edible, self.tags, self.native)

    def test_native_different_bytes_rejected(self):
        self.native['definitions'][0]['actual_network_sha256'] = 'different'
        with self.assertRaisesRegex(ValueError, 'native serialized recipe differs'): self.run_check()

    def test_success(self):
        self.assertEqual(self.run_check()['status'], 'passed')

    def test_missing_installed_recipe(self):
        self.captured['recipes'] = {}
        with self.assertRaisesRegex(ValueError, 'installed recipes differ'): self.run_check()

    def test_food_property_change_rejected(self):
        self.edible = {'items': {'a:food': {'nutrition': 2}}}
        with self.assertRaisesRegex(ValueError, 'food properties changed'): self.run_check()

    def test_wrong_runtime_output_rejected(self):
        self.live['recipes'][0]['outputs'] = [{'id': 'a:wrong'}]
        with self.assertRaisesRegex(ValueError, 'loaded outputs differ'): self.run_check()

    def test_wrong_runtime_output_count_rejected(self):
        self.live['recipes'][0]['outputs'][0]['count'] = 2
        with self.assertRaisesRegex(ValueError, 'output quantities differ'): self.run_check()

    def test_missing_compatibility_tag_rejected(self):
        self.tags['item_tags']['forge:dough/wheat'] = []
        with self.assertRaisesRegex(ValueError, 'canonical ingredient absent'): self.run_check()
