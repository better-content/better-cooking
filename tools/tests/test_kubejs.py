"""Run actual KubeJS script under a tiny Node event stub, not Minecraft acceptance."""
import copy
import json
from pathlib import Path
import shutil
import subprocess
import sys
import unittest

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from compile_policy import _compile_policy_model as compile_policy
from recipe_migration import build_plan
from test_policy import fixture

SCRIPT = Path('/home/dev/better-content-modpack/kubejs/server_scripts/progression/83_culinary_policy.js')
NODE = r'''
const fs = require('fs'), vm = require('vm');
const args = JSON.parse(process.argv[1]), events = [];
const context = {
  Platform: { isLoaded: name => args.providers.includes(name) },
  JsonIO: { read: path => { events.push(['read', path]); return args.policy; } },
  console: { warn: text => events.push(['warning', text]) },
  ServerEvents: { recipes: handler => handler({
    custom: value => { events.push(['custom', value]); return { id: id => events.push(['id', id]) }; },
    remove: value => events.push(['remove', value])
  }) }
};
let error = null;
try { vm.runInNewContext(fs.readFileSync(args.script, 'utf8'), context); }
catch (e) { error = String(e); }
process.stdout.write(JSON.stringify({events, error}));
'''


@unittest.skipUnless(shutil.which('node'), 'Node unavailable; JS event stub is not native acceptance')
class KubeJsTests(unittest.TestCase):
    def policy(self): return compile_policy(fixture())
    def run_js(self, policy, providers=('better_cooking', 'kaleidoscope_cookery')):
        out = subprocess.run(['node', '-e', NODE, json.dumps({'script': str(SCRIPT), 'policy': policy, 'providers': providers})], capture_output=True, text=True, check=True)
        return json.loads(out.stdout)
    def resolved_fixture(self):
        # Deliberately synthetic event-stub test only. Never write this policy to disk.
        p = self.policy(); p['qualification'] = 'qualified'; p['gaps'] = []; p['native_qualification'] = {'test_only': True}
        for f in p['foods'].values(): f['unresolved'] = []
        for r in p['routes'].values():
            r['unresolved'] = []
            if r['disposition'] == 'unresolved': r['disposition'] = 'retain_bulk'
        p['migration'] = build_plan(p)
        return p
    def test_absent_provider_no_policy_read_or_mutation(self):
        r = self.run_js(None, providers=()); self.assertEqual(r, {'events': [], 'error': None})
    def test_unqualified_no_removals_or_emission(self):
        r = self.run_js(self.policy()); self.assertIsNone(r['error'])
        self.assertFalse(any(e[0] in ('custom', 'remove') for e in r['events']))
    def test_missing_policy_errors_before_mutation(self):
        r = self.run_js(None); self.assertIsNotNone(r['error']); self.assertFalse(any(e[0] == 'remove' for e in r['events']))
    def test_missing_native_provider_errors(self):
        r = self.run_js(self.resolved_fixture(), providers=('better_cooking',)); self.assertIn('Missing native', r['error'])
    def test_missing_receipt_errors(self):
        p = self.resolved_fixture(); del p['native_qualification']
        self.assertIn('receipt', self.run_js(p)['error'])
    def test_global_gaps_no_removal(self):
        p = self.resolved_fixture(); p['gaps'] = ['gap']; r = self.run_js(p)
        self.assertIsNotNone(r['error']); self.assertFalse(any(e[0] == 'remove' for e in r['events']))
    def test_food_gap_no_emission(self):
        p = self.resolved_fixture(); p['foods']['example:salad']['unresolved'] = ['gap']; r = self.run_js(p)
        self.assertIsNotNone(r['error']); self.assertFalse(any(e[0] == 'custom' for e in r['events']))
    def test_route_gap_no_emission(self):
        p = self.resolved_fixture(); p['routes']['example:salad']['unresolved'] = ['gap']; r = self.run_js(p)
        self.assertIsNotNone(r['error']); self.assertFalse(any(e[0] == 'custom' for e in r['events']))
    def test_missing_replacement_no_emission(self):
        p = self.resolved_fixture(); p['migration']['recipes'] = []; r = self.run_js(p)
        self.assertIsNotNone(r['error']); self.assertFalse(any(e[0] == 'custom' for e in r['events']))
    def test_duplicate_generated_id_no_emission(self):
        p = self.resolved_fixture(); p['migration']['recipes'] *= 2; r = self.run_js(p)
        self.assertIsNotNone(r['error']); self.assertFalse(any(e[0] == 'custom' for e in r['events']))
    def test_every_emission_precedes_any_removal(self):
        r = self.run_js(self.resolved_fixture()); self.assertIsNone(r['error'])
        kinds = [e[0] for e in r['events']]
        self.assertLess(max(i for i,k in enumerate(kinds) if k == 'custom'), min(i for i,k in enumerate(kinds) if k == 'remove'))
    def test_removal_exact_id_only(self):
        r = self.run_js(self.resolved_fixture())
        self.assertEqual([e[1] for e in r['events'] if e[0] == 'remove'], [{'id': 'example:salad'}])


if __name__ == '__main__': unittest.main()
