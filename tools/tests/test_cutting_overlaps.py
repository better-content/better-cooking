import copy
from pathlib import Path
import sys
import unittest
TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from extract_catalogue import resource_recipe
from report_cutting_overlaps import overlaps
from test_policy import fixture


class CuttingOverlapTests(unittest.TestCase):
    def test_distinct_same_input_is_reported_not_deleted_or_aliased(self):
        f = fixture()
        f['recipes'] = {
            'test:cut': resource_recipe('test:cut', {'type': 'farmersdelight:cutting', 'ingredients': [{'item': 'minecraft:beef'}], 'tool': {'tag': 'forge:tools/knives'}, 'result': [{'item': 'test:mince', 'count': 2, 'chance': .5}]}, 'source#cut'),
            'test:offal': resource_recipe('test:offal', {'type': 'kaleidoscope_cookery:chopping_board', 'ingredient': {'item': 'minecraft:beef'}, 'result': {'item': 'test:offal', 'count': 3}}, 'source#offal')}
        p = {'routes': {'test:cut': {'authored_review': {'adapter': 'native_bulk_cut'}}}}
        before = copy.deepcopy(f), copy.deepcopy(p)
        report = overlaps(f, p)
        self.assertEqual((f, p), before)
        pair = report['candidate_pairs'][0]
        self.assertEqual(pair['candidate_common_items'], ['minecraft:beef'])
        self.assertEqual(pair['reviewed_ordered_outputs'][0]['chance'], .5)
        self.assertEqual(pair['legacy_ordered_outputs'][0]['count'], 3)
        self.assertEqual(report['qualification'], 'unqualified')
        self.assertIn('NOT', pair['warning'])


if __name__ == '__main__': unittest.main()
