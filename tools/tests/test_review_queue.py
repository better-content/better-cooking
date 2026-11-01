import sys
from pathlib import Path
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from report_review_queue import review_queue

class ReviewQueueTests(unittest.TestCase):
    def test_only_full_source_role_work_review_leaves_index(self):
        p={'foods':{'m:done':{'classification':'authored_source_reviewed_pending_native'},'m:held':{'classification':'partially_authored_review_pending_native'},'n:new':{'classification':'inferred'}}}
        q=review_queue(p,'exact-byte-digest')
        self.assertEqual(q['by_namespace'],{'m':['m:held'],'n':['n:new']})
        self.assertEqual(q['partially_reviewed_food_ids'],['m:held'])
        self.assertEqual(q['unreviewed_food_count'],2)
    def test_derived_index_never_approval_or_native_evidence(self):
        q=review_queue({'foods':{}},'sha')
        self.assertFalse(q['native_acceptance'])
        self.assertIn('NOT_APPROVAL',q['status'])
        self.assertEqual(q['policy_sha256'],'sha')

if __name__=='__main__':unittest.main()
