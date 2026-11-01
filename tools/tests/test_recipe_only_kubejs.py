import json
from pathlib import Path
import shutil
import subprocess
import unittest

SCRIPT = Path('/home/dev/better-content-modpack/kubejs/server_scripts/progression/84_cooking_recipes.js')


@unittest.skipUnless(shutil.which('node'), 'Node required')
class RecipeOnlyConsumerTest(unittest.TestCase):
    def run_policy(self, policy):
        runner = '''const fs=require('fs'),vm=require('vm');const policy=JSON.parse(process.argv[1]);let calls=[];let error=null;
const wrap=(x)=>({toJSON:()=>x,getAsJsonObject:(k)=>wrap(k===undefined?x:x[k]),getAsJsonArray:(k)=>wrap(x[k]),get:(k)=>wrap(x[k])});
const pending=Object.entries((policy&&policy.mock_pending_sources)||{}).map(([id,json])=>({json,removed:false,getId:()=>id,serialize:()=>{}}));
const context={Java:{loadClass:()=>({equals:(a,b)=>require('util').isDeepStrictEqual(JSON.parse(JSON.stringify(a)),JSON.parse(JSON.stringify(b)))})},JsonIO:{of:(x)=>x,toString:JSON.stringify,readJson:()=>wrap(policy),read:(p)=>p.endsWith('cooking_recipes.json')?policy:null},console:{info:()=>{}},ServerEvents:{recipes:(f)=>{try{f({addedRecipes:pending,forEachRecipe:(filter,callback)=>{const raw=(policy.live_sources||policy.expected_sources||{})[filter.id];if(raw)callback({json:raw})},custom:(j)=>({id:(id)=>calls.push(['define',id])}),remove:(x)=>calls.push(['remove',x.id])})}catch(e){error=String(e)}}}};
vm.runInNewContext(fs.readFileSync(process.argv[2],'utf8'),context);process.stdout.write(JSON.stringify(Object.assign({calls,error},policy&&policy.mock_pending_sources?{pending_removed:pending.filter(x=>x.removed).map(x=>x.getId())}:{})));'''
        result = subprocess.run(['node', '-e', runner, json.dumps(policy), str(SCRIPT)], capture_output=True, text=True, check=True)
        return json.loads(result.stdout)

    def base(self):
        return {'schema': 'better_cooking:recipe_policy.v1', 'enabled': True, 'catalogue': {},
                'source_recipe_hashes': {'a:old': 'test'}, 'expected_sources': {'a:old': {'type': 'minecraft:crafting_shapeless'}}, 
                'routes': {'a:old': {'action': 'replace', 'reason': 'canonical', 'definitions': [{'id': 'a:new', 'json': {'type': 'minecraft:crafting_shapeless'}}]}},
                'definitions': [{'id': 'a:new', 'json': {'type': 'minecraft:crafting_shapeless'}}], 'remove_ids': ['a:old']}

    def test_absent_policy_changes_nothing(self):
        self.assertEqual(self.run_policy(None), {'calls': [], 'error': None})

    def test_definitions_precede_removals(self):
        self.assertEqual(self.run_policy(self.base()), {'calls': [['define', 'a:new'], ['remove', 'a:old']], 'error': None})

    def test_duplicate_rejected_before_mutation(self):
        p = self.base(); p['definitions'] *= 2
        result = self.run_policy(p)
        self.assertEqual(result['calls'], []); self.assertIsNotNone(result['error'])

    def test_unmatched_removal_rejected_before_mutation(self):
        p = self.base(); p['remove_ids'].append('a:other')
        result = self.run_policy(p)
        self.assertEqual(result['calls'], []); self.assertIsNotNone(result['error'])

    def test_changed_live_source_rejected(self):
        p = self.base(); p['live_sources'] = {'a:old': {'type': 'minecraft:smelting'}}
        result = self.run_policy(p)
        self.assertEqual(result['calls'], []); self.assertIsNotNone(result['error'])

    def test_missing_live_source_rejected(self):
        p = self.base(); p['live_sources'] = {}
        result = self.run_policy(p)
        self.assertEqual(result['calls'], []); self.assertIsNotNone(result['error'])

    def test_orphan_definition_rejected(self):
        p = self.base(); p['definitions'].append({'id': 'a:orphan', 'json': {'type': 'minecraft:crafting_shapeless'}})
        result = self.run_policy(p)
        self.assertEqual(result['calls'], []); self.assertIsNotNone(result['error'])

    def test_replacement_collision_rejected(self):
        p = self.base(); p['live_sources'] = dict(p['expected_sources'], **{'a:new': {'type': 'minecraft:crafting_shapeless'}})
        result = self.run_policy(p)
        self.assertEqual(result['calls'], []); self.assertIsNotNone(result['error'])

    def test_replacement_payload_mismatch_rejected(self):
        p = self.base(); p['definitions'][0]['json']['extra'] = True
        result = self.run_policy(p)
        self.assertEqual(result['calls'], []); self.assertIsNotNone(result['error'])

    def test_pending_source_replaced_exactly(self):
        p = self.base(); p['pending_source_ids'] = ['a:old']; p['live_sources'] = {}
        p['mock_pending_sources'] = p['expected_sources']
        result = self.run_policy(p)
        self.assertIsNone(result['error']); self.assertEqual(result['pending_removed'], ['a:old'])

    def test_pending_source_mismatch_leaves_queue_untouched(self):
        p = self.base(); p['pending_source_ids'] = ['a:old']; p['live_sources'] = {}
        p['mock_pending_sources'] = {'a:old': {'type': 'minecraft:smelting'}}
        result = self.run_policy(p)
        self.assertIsNotNone(result['error']); self.assertEqual(result['pending_removed'], []); self.assertEqual(result['calls'], [])

    def test_pending_replacement_collision_rejected(self):
        p = self.base(); p['mock_pending_sources'] = {'a:new': {'type': 'minecraft:smelting'}}
        result = self.run_policy(p)
        self.assertIsNotNone(result['error']); self.assertEqual(result['pending_removed'], []); self.assertEqual(result['calls'], [])

    def test_numeric_shaped_keys_preserved(self):
        p = self.base()
        p['expected_sources']['a:old'] = {'type': 'minecraft:crafting_shaped', 'key': {'0': {'item': 'a:block'}}, 'pattern': ['0']}
        self.assertIsNone(self.run_policy(p)['error'])

    def test_custom_work_recipe_rejected(self):
        p = self.base(); p['definitions'][0]['json']['type'] = 'better_cooking:preparation'
        result = self.run_policy(p)
        self.assertEqual(result['calls'], []); self.assertIsNotNone(result['error'])
