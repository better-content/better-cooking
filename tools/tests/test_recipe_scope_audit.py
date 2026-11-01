import importlib.util
import pathlib
import unittest

path = pathlib.Path(__file__).resolve().parents[1] / 'recipe_scope_audit.py'
spec = importlib.util.spec_from_file_location('recipe_scope_audit', path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RecipeScopeAuditTest(unittest.TestCase):
    def setUp(self):
        self.edible = {'schema': 'bc.edible_items.v1', 'snapshot_id': 'e', 'item_count': 2,
                       'items': {'a:fruit': {'nutrition': 2}, 'a:stew': {'nutrition': 6}}}
        self.recipes = {'snapshot_id': 'r', 'recipe_count': 1,
                        'recipes': [{'id': 'a:stew', 'type': 'minecraft:crafting', 'serializer': 'minecraft:crafting_shapeless'}]}
        self.policy = {'schema': 'better_cooking:culinary_policy.v1',
                       'foods': {'a:fruit': {'role': 'ingredient'}, 'a:stew': {'role': 'unresolved'}},
                       'routes': {'a:stew': {'disposition': 'replace', 'replacement': {'id': 'a:new_stew'}, 'unresolved': []},
                                  'a:old': {'disposition': 'retain_bulk', 'unresolved': []}}}

    def test_no_false_installable_status_or_missing_route_approval(self):
        report = module.audit(self.edible, self.recipes, self.policy, edible_sha256='e', recipes_sha256='r')
        self.assertEqual(report['status'], 'review_required_not_installable')
        self.assertEqual(report['catalogue_count'], 2)
        self.assertEqual(report['historical_routes_not_live'], 1)
        self.assertEqual(report['catalogue']['a:stew']['status'], 'review_required')
        self.assertTrue(any('old_preparation_serializer_not_recipe_only_approved' in row.get('reasons', [])
                            for row in report['review_queue']))

    def test_duplicate_live_ids_refused(self):
        self.recipes['recipes'].append(dict(self.recipes['recipes'][0]))
        self.recipes['recipe_count'] += 1
        with self.assertRaisesRegex(ValueError, 'duplicate live recipe ID'):
            module.audit(self.edible, self.recipes, self.policy, edible_sha256='e', recipes_sha256='r')

    def test_invalid_count_refused(self):
        self.edible['item_count'] = 3
        with self.assertRaisesRegex(ValueError, 'invalid live edible inventory'):
            module.audit(self.edible, self.recipes, self.policy, edible_sha256='e', recipes_sha256='r')


if __name__ == '__main__':
    unittest.main()
