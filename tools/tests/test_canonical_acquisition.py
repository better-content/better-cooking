import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from acquisition_policy import acquisition_plan
from extract_catalogue import resource_recipe
from compile_policy import _compile_policy_model as compile_policy
from recipe_migration import canonical_bulk_definition
from test_policy import fixture


class CanonicalRecipesTests(unittest.TestCase):
    def frozen(self, raw):
        f = fixture()
        f['items'].extend(['create:wheat_flour', 'kaleidoscope_cookery:flour', 'create:dough', 'kaleidoscope_cookery:raw_dough'])
        f['recipes']['example:bulk'] = resource_recipe('example:bulk', raw, 'test')
        return f
    def test_exact_canonical_output_rewrite_count_unchanged(self):
        p = compile_policy(self.frozen({'type': 'create:milling', 'ingredients': [{'item': 'minecraft:apple'}], 'results': [{'item': 'create:wheat_flour', 'count': 3}]}))
        definition = next(r for r in p['migration']['recipes'] if r['id'] == 'example:bulk')
        self.assertEqual(definition['json']['results'], [{'item': 'kaleidoscope_cookery:flour', 'count': 3}])
        self.assertNotIn('example:bulk', p['migration']['remove_ids'])
    def test_nonfood_consumer_accepts_canonical_or_old_not_both(self):
        p = compile_policy(self.frozen({'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'create:wheat_flour'}, {'item': 'minecraft:apple'}], 'result': {'item': 'example:nonfood'}}))
        d = next(r for r in p['migration']['recipes'] if r['id'] == 'example:bulk')
        self.assertEqual(len(d['json']['ingredients']), 2)
        self.assertEqual(d['json']['ingredients'][0], [{'item': 'create:wheat_flour'}, {'item': 'kaleidoscope_cookery:flour'}])
    def test_shaped_consumer_preserves_pattern(self):
        p = compile_policy(self.frozen({'type': 'minecraft:crafting_shaped', 'pattern': ['AA'], 'key': {'A': {'item': 'create:dough'}}, 'result': {'item': 'example:nonfood', 'count': 7}}))
        d = next(r for r in p['migration']['recipes'] if r['id'] == 'example:bulk')
        self.assertEqual(d['json']['pattern'], ['AA']); self.assertEqual(d['json']['result']['count'], 7)
        self.assertEqual(d['json']['key']['A'][1], {'item': 'kaleidoscope_cookery:raw_dough'})
    def test_counted_custom_consumer_requires_adapter_not_lossy_flatten(self):
        p = compile_policy(self.frozen({'type': 'create:mixing', 'ingredients': [{'item': 'create:dough', 'count': 4}], 'results': [{'item': 'example:nonfood'}]}))
        self.assertTrue(any(e['error'] == 'canonical_consumer_counted_or_custom_matcher_requires_adapter' for e in p['migration']['errors']))
        self.assertFalse(any(d['id'] == 'example:bulk' for d in p['migration']['recipes']))
    def test_no_resource_json_explicit_error(self):
        f = self.frozen({'type': 'create:milling', 'ingredients': [{'item': 'minecraft:apple'}], 'results': [{'item': 'create:wheat_flour'}]})
        del f['recipes']['example:bulk']['raw']
        p = compile_policy(f)
        self.assertTrue(any(e['source_id'] == 'example:bulk' for e in p['migration']['errors']))
    def test_output_chance_preserved(self):
        p = compile_policy(self.frozen({'type': 'create:milling', 'ingredients': [{'item': 'minecraft:apple'}], 'results': [{'item': 'create:wheat_flour', 'count': 2, 'chance': .3}]}))
        d = next(r for r in p['migration']['recipes'] if r['id'] == 'example:bulk')
        self.assertEqual(d['json']['results'][0]['chance'], .3)
    def test_no_flour_dough_conflation(self):
        p = compile_policy(self.frozen({'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'minecraft:apple'}], 'result': {'item': 'create:dough'}}))
        d = next(r for r in p['migration']['recipes'] if r['id'] == 'example:bulk')
        self.assertEqual(d['json']['result']['item'], 'kaleidoscope_cookery:raw_dough')


class AcquisitionTests(unittest.TestCase):
    def test_exact_refs_and_no_heuristic_deletion(self):
        f = fixture(); f['items'].extend(['kaleidoscope_cookery:tomato', 'farmersdelight:tomato'])
        f['acquisition_resources'] = {'kc.jar#data/kaleidoscope_cookery/loot_tables/blocks/tomato_crop.json': {'pools': [{'entries': [{'type': 'minecraft:item', 'name': 'kaleidoscope_cookery:tomato'}]}]}}
        plan = acquisition_plan(f)
        self.assertEqual(plan['status'], 'unresolved'); self.assertEqual(plan['actions_authorized'], [])
        self.assertEqual(len(plan['rows'][0]['known_resource_references']), 1)
        self.assertEqual(plan['rows'][0]['finite_found_stock'], 'retain')
        self.assertIn('villager_wandering_trader_and_other_trade_sources_not_closed', plan['rows'][0]['unresolved'])
    def test_source_recipe_ids_recorded(self):
        f = fixture(); f['items'].append('kaleidoscope_cookery:tomato_seed')
        f['recipes']['example:salad']['outputs'][0]['id'] = 'kaleidoscope_cookery:tomato_seed'
        self.assertEqual(acquisition_plan(f)['rows'][0]['known_recipe_output_ids'], ['example:salad'])
    def test_no_duplicate_crop_in_scope_no_unknown_crop_claim(self): self.assertEqual(acquisition_plan(fixture())['status'], 'closed')


if __name__ == '__main__': unittest.main()
