"""UNTRUSTED planner/model negatives; these fixtures NEVER grant admission."""
import copy
import argparse
import ast
from pathlib import Path
import sys
import tempfile
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from native_import_v2 import derive_partition, require_bound_catalogue
from compile_policy import compile_policy, validate, _compile_policy_model, _native_output_parent, _publish_native, _canonical_cli_path
from recipe_migration import build_plan, validate_native_plan
from test_native_import_v2 import observation, provenance, baseline
from test_policy import fixture


def policy():
    p = derive_partition(observation(), provenance(), baseline())
    return {'qualification': 'unqualified', 'routes': {}, 'native_observation_partition': p,
            'native_unresolved_recipe_ids': list(p['quarantined_recipe_ids'])}


class QuarantineTests(unittest.TestCase):
    def test_pure_structural_partition_can_check_empty_plan_but_not_admit(self):
        p = policy(); result = build_plan(p)
        self.assertEqual(result['recipes'], []); self.assertEqual(result['remove_ids'], [])
        with self.assertRaises(ValueError): require_bound_catalogue(p)
        with self.assertRaises(ValueError): compile_policy(p)

    def test_all_three_planner_branches_guarded_before_iteration(self):
        for route in ({'adapter_decision': 'native_bulk_cut'}, {'disposition': 'retain_bulk'}, {'disposition': 'replace'}):
            p = policy(); p['routes']['example:new_apple'] = route; before = copy.deepcopy(p)
            with self.assertRaisesRegex(ValueError, 'ALL effective routes'): build_plan(p)
            self.assertEqual(p, before)

    def test_nonlive_historical_route_cannot_survive(self):
        p = policy(); p['routes']['example:historical'] = {'disposition': 'retain_nonfood'}
        with self.assertRaises(ValueError): build_plan(p)

    def test_unknown_new_authored_route_cannot_be_forced(self):
        p = policy(); p['routes']['example:invented'] = {'disposition': 'replace', 'unresolved': []}
        with self.assertRaises(ValueError): build_plan(p)

    def test_clear_gaps_and_unresolved_does_not_authorize(self):
        p = policy(); p.update(gaps=[], native_qualification=True); p['native_unresolved_recipe_ids'] = []
        with self.assertRaises(ValueError): build_plan(p)

    def test_missing_null_or_wrong_version_partition(self):
        for mutate in (lambda p:p.pop('native_observation_partition'), lambda p:p.update(native_observation_partition=None),
                       lambda p:p['native_observation_partition'].update(schema='better_cooking:native_observation_partition.v1')):
            p = policy(); mutate(p)
            with self.assertRaises(ValueError): build_plan(p)

    def test_malformed_counts_classification_and_lost_records(self):
        for mutate in (lambda q:q.update(recipe_count=True), lambda q:q.update(ledger=[]),
                       lambda q:q.update(quarantined_recipe_ids=[]), lambda q:q['ledger'][0].update(classification='admitted'),
                       lambda q:q['ledger'][0].update(reasons=[]), lambda q:q['ledger'][0].update(ordinal=False),
                       lambda q:q.update(admitted_recipe_ids=['example:new_apple'])):
            p = policy(); mutate(p['native_observation_partition'])
            with self.assertRaises(ValueError): build_plan(p)

    def test_precomputed_removals_definitions_rewrites_all_reject(self):
        for key in ('recipes', 'remove_ids', 'canonical_bulk_rewrite_ids', 'native_cut_rewrite_ids', 'unresolved_source_ids'):
            p = policy(); p['migration'] = build_plan(p); p['migration'][key] = ['example:historical']
            with self.assertRaisesRegex(ValueError, 'precomputed'): build_plan(p)

    def test_initial_qualified_always_refused_even_zero_vacuous_source(self):
        for p in ({'qualification': 'qualified', 'gaps': [], 'foods': {}, 'routes': {}, 'native_qualification': True}, policy()):
            p['qualification'] = 'qualified'
            with self.assertRaisesRegex(ValueError, 'no NativeAcceptance'): validate(p)

    def test_public_source_compile_requires_positive_origin_not_no_markers(self):
        source = fixture()
        with self.assertRaises(ValueError): compile_policy(source)
        self.assertEqual(_compile_policy_model(source)['qualification'], 'unqualified')

    def test_fully_and_partially_stripped_native_copies_lack_both_capabilities(self):
        for marker in (True, False):
            source = fixture()
            if marker: source['native_observation_partition'] = policy()['native_observation_partition']
            source['recipes'] = {}
            with self.assertRaises(ValueError): compile_policy(source)
            with self.assertRaises(ValueError): require_bound_catalogue(source)

    def test_no_model_can_publish_native_candidates_without_origin(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as td:
            parent = Path(td) / 'candidate'; output = parent / 'policy.json'; report = parent / 'report.json'
            with self.assertRaises(ValueError): _publish_native([(output, '{}')], output, report, frozen=fixture())
            self.assertFalse(parent.exists())
            self.assertEqual(list(Path(td).iterdir()), [])

    def test_original_cli_spelling_checked_before_path_construction_no_cli_execution(self):
        for spelling in ('/home/dev//candidate/p.json', '/home/dev/./candidate/p.json'):
            with self.assertRaises(argparse.ArgumentTypeError): _canonical_cli_path(spelling)
            with self.assertRaises(ValueError): _native_output_parent(spelling, '/home/dev/candidate/report.json')
        self.assertEqual(_canonical_cli_path('tools/authored/nonstandard_consumables.json'), Path('tools/authored/nonstandard_consumables.json'))
        tree = ast.parse((Path(__file__).resolve().parents[1] / 'compile_policy.py').read_text())
        names = {'--native-snapshot', '--native-binding', '--output', '--report'}
        checked = set()
        for call in ast.walk(tree):
            if isinstance(call, ast.Call) and isinstance(call.func, ast.Attribute) and call.func.attr == 'add_argument' and call.args and isinstance(call.args[0], ast.Constant) and call.args[0].value in names:
                kind = next(k.value for k in call.keywords if k.arg == 'type')
                self.assertIsInstance(kind, ast.Name); self.assertEqual(kind.id, '_canonical_cli_path'); checked.add(call.args[0].value)
        self.assertEqual(checked, names)

    def test_native_output_paths_same_fresh_home_parent_and_no_aliases(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as td:
            parent = Path(td) / 'fresh'
            self.assertEqual(_native_output_parent(parent / 'policy.json', parent / 'report.json'), parent)
            for output, report in ((parent / 'same.json', parent / 'same.json'), (parent / 'p.json', Path(td) / 'r.json'), (Path('relative/p.json'), Path('relative/r.json'))):
                with self.assertRaises(ValueError): _native_output_parent(output, report)
            parent.mkdir()
            with self.assertRaises(ValueError): _native_output_parent(parent / 'p.json', parent / 'r.json')


if __name__ == '__main__': unittest.main()
