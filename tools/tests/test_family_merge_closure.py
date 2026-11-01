"""Synthetic source-lock fault controls, never native observations or approval."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'authored'))
from merge_family_reviews import merge_family_reviews

class FamilyMergeClosureTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(dir='/home/dev/.tmp')
        self.addCleanup(self.tmp.cleanup)
        self.path=Path(self.tmp.name)/'explicit-approved.json'
        self.source=Path(self.tmp.name)/'primary.bin';self.source.write_bytes(b'synthetic-primary-source')
        digest=hashlib.sha256(self.source.read_bytes()).hexdigest()
        self.family={'schema':'better_cooking:approved_source_family_reviews.v1','status':'SOURCE_ONLY',
            'foods':{'test:food':{'role':'ingredient','work_class':'none'}},'routes':{'test:recipe':{'disposition':'deferred'}},
            'source_inputs':{str(self.source):digest},'review_origins':[],
            'source_route_candidates':{'test:artifact_only':{'disposition':'deferred'}}}
        self.doc={'families':[],'foods':{},'routes':{},'source_inputs':{}}
        self.frozen={'foods':{'test:food':{}},'recipes':{'test:recipe':{}},'manifest':{}}
        self.bind()
    def bind(self):
        self.path.write_text(json.dumps(self.family))
        self.lock={**self.family['source_inputs'],str(self.path):hashlib.sha256(self.path.read_bytes()).hexdigest()}
        self.frozen['manifest']=dict(self.lock)
    def merge(self):return merge_family_reviews(self.doc,self.frozen,self.path,self.lock)
    def test_closed_explicit_source_merge_is_copy_and_candidates_stay_separate(self):
        result=self.merge()
        self.assertNotIn('test:food',self.doc['foods'])
        self.assertIn('test:food',result['foods'])
        self.assertNotIn('test:artifact_only',result['routes'])
        self.assertFalse(result['source_family_reviews']['native_acceptance'])
    def test_changed_approved_document_requires_explicit_review(self):
        self.path.write_text(self.path.read_text()+' ')
        with self.assertRaisesRegex(ValueError,'explicit re-review'):self.merge()
    def test_actual_primary_byte_drift_fails_even_with_old_lock_manifest(self):
        self.source.write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError,'actual source bytes changed'):self.merge()
    def test_missing_frozen_or_lock_binding_fails(self):
        del self.frozen['manifest'][str(self.source)]
        with self.assertRaisesRegex(ValueError,'not locked and frozen'):self.merge()
    def test_source_only_route_cannot_be_promoted(self):
        self.family['routes']['test:artifact_only']={'disposition':'deferred'};self.bind()
        with self.assertRaisesRegex(ValueError,'cannot be promoted'):self.merge()
    def test_duplicate_food_or_route_never_silently_overwrites(self):
        self.doc['foods']['test:food']={}
        with self.assertRaisesRegex(ValueError,'overlaps prior'):self.merge()
        self.doc['foods'].clear();self.doc['routes']['test:recipe']={}
        with self.assertRaisesRegex(ValueError,'overlaps prior'):self.merge()
    def test_missing_declared_food_never_becomes_namespace_approval(self):
        self.family['foods']['test:unregistered']={'role':'ingredient','work_class':'none'};self.bind()
        with self.assertRaisesRegex(ValueError,'food not declared'):self.merge()

if __name__=='__main__':unittest.main()
