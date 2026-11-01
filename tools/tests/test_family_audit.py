"""Integrity is not semantic approval; exact source IDs/resources remain bound."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from audit_family_review import audit

class FamilyAuditTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir='/home/dev/.tmp')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.jar = self.root/'fixture.jar'
        self.recipe = {'type':'minecraft:crafting_shapeless','ingredients':[{'item':'example:raw'}], 'result':{'item':'example:food','count':8}}
        self.support = {'pools':[{'rolls':1,'conditions':[{'condition':'example:guard'}]}]}
        with zipfile.ZipFile(self.jar,'w') as archive:
            archive.writestr('data/example/recipes/actual_recipe.json', json.dumps(self.recipe))
            archive.writestr('data/example/loot_tables/source.json', json.dumps(self.support))
        self.descriptor = self.root/'fixture.pw.toml'
        self.descriptor.write_text('filename = "fixture.jar"\n[download]\nhash-format = "sha256"\nhash = "'+self.sha(self.jar)+'"\n')
        self.draft = {'source_inputs':{str(p):self.sha(p) for p in (self.jar,self.descriptor)},
            'foods':[{'id':'example:food'}], 'routes':[{'id':'example:food', 'recipe_id':'example:actual_recipe', 'original_resource':self.recipe}],
            'source_resources':[{'source_entry':'data/example/loot_tables/source.json','original_resource':self.support}]}
    @staticmethod
    def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
    def run_audit(self):
        draft = self.root/'draft.json'; draft.write_text(json.dumps(self.draft))
        return audit(draft,self.jar,self.descriptor,self.sha(draft))[1]
    def test_exact_recipe_id_not_output_id_and_tier(self):
        report = self.run_audit()
        self.assertEqual(report['route_ids'],['example:actual_recipe'])
        self.assertFalse(report['native_acceptance'])
        self.assertIn('INTEGRITY_ONLY',report['status'])
        self.assertEqual(report['whole_supporting_resources_verified'],1)
    def test_both_brewing_evidence_shapes_are_hash_bound(self):
        with zipfile.ZipFile(self.jar) as archive:
            raw = archive.read('data/example/loot_tables/source.json')
        evidence = {'artifact':str(self.jar),'artifact_sha256':self.sha(self.jar),
            'entry':'data/example/loot_tables/source.json','entry_sha256':hashlib.sha256(raw).hexdigest()}
        self.draft['brewing_rules'] = [{'evidence':[evidence]}]
        self.draft['source_brewing_rules'] = [{'evidence':[dict(evidence)]}]
        report = self.run_audit()
        self.assertEqual(report['brewing_rule_citations_verified'],2)
        self.assertEqual(report['artifact_entry_citations_verified'],2)
        self.assertFalse(report['native_acceptance'])
    def test_brewing_hash_mutation_rejected(self):
        self.draft['source_brewing_rules'] = [{'evidence':[{'artifact':str(self.jar),
            'artifact_sha256':self.sha(self.jar),'member':'data/example/loot_tables/source.json','member_sha256':'0'*64}]}]
        with self.assertRaisesRegex(ValueError,'entry digest mismatch'): self.run_audit()
    def test_recipe_quantity_mutation_rejected(self):
        self.draft['routes'][0]['original_resource'] = dict(self.recipe, result={'item':'example:food','count':1})
        with self.assertRaisesRegex(ValueError,'original recipe changed'): self.run_audit()
    def test_supporting_guard_mutation_rejected(self):
        self.draft['source_resources'][0]['original_resource'] = {'pools':[]}
        with self.assertRaisesRegex(ValueError,'supporting resource changed'): self.run_audit()
    def test_utf8_claim_is_exact_not_semantic(self):
        self.draft['routes'][0]['original_resource_utf8'] = json.dumps(self.recipe,indent=2)
        with self.assertRaisesRegex(ValueError,'original recipe bytes changed'): self.run_audit()

if __name__ == '__main__': unittest.main()
