"""UNIT-only source-retention controls. No native getter or acceptance fixture."""
import copy
import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from compile_policy import _compile_policy_model as compile_policy, validate
from signed_native_retention import SIGNED_ROWS, ARTIFACTS, reviewed_retention


def inputs(item='fallout_wastelands_:coffee'):
    h,s,always,role=SIGNED_ROWS[item]
    native={'nutrition':h,'saturation_modifier':s,'always_edible':always}
    review={'role':role,'work_class':'unresolved','reviewed':['role'] if role=='drink' else [],
        'reason':'UNIT_ONLY exact source signed retention; NOT getters',
        'evidence':[json.dumps({'artifact_sha256':ARTIFACTS[role],'entry':'unit/Source.class','entry_sha256':'a'*64})],
        'unresolved':['UNIT_ONLY_source_work_and_native_intake_HELD'],
        'signed_native_retention':{'basis':'literal_source_values_not_observed_getters','native_food':copy.deepcopy(native),
            'role_work_normalization_authorized':False,'native_acceptance':False}}
    frozen={'snapshot':{'snapshot_id':'UNIT_ONLY'},'manifest':{'UNIT_ONLY':'a'*64},'limitations':['UNIT_ONLY_NOT_NATIVE'],
        'items':[item],'item_tags':{},'resource_tags':{},'servings':{},'recipes':{},
        'foods':{item:{'food':native,'effects':None,'evidence':['UNIT_ONLY']}}}
    authored={'schema':'better_cooking:authored_decisions.v1','source_inputs':{'UNIT_ONLY':'a'*64},'families':[], 'foods':{item:review},'routes':{}}
    return frozen,authored

class SignedNativeRetentionTests(unittest.TestCase):
    def test_exact_thirteen_source_rows_preserve_role_values_and_holds(self):
        self.assertEqual(len(SIGNED_ROWS),13)
        for item,(h,s,_,role) in SIGNED_ROWS.items():
            with self.subTest(item=item):
                f,a=inputs(item);p=compile_policy(f,authored=a);food=p['foods'][item]
                self.assertEqual((food['role'],food['nutrition']['hunger'],food['nutrition']['saturation_modifier']),(role,h,s))
                self.assertEqual((food['quantity_basis'],food['nutrition_basis']),('native_intake','retained_native'))
                self.assertIn('authored_work_class_requires_explicit_decision',food['unresolved'])
                self.assertEqual(p['qualification'],'unqualified');self.assertTrue(food['unresolved'])
                self.assertNotEqual(food['classification'],'authored_source_reviewed_pending_native')
    def test_unknown_identity_cannot_borrow_marker(self):
        f,a=inputs();r=next(iter(a['foods'].values()))
        with self.assertRaisesRegex(ValueError,'finite reviewed identity'): reviewed_retention('fallout_wastelands_:other',r,f['foods']['fallout_wastelands_:coffee']['food'])
    def test_no_namespace_exemption_without_marker(self):
        f,a=inputs();del a['foods']['fallout_wastelands_:coffee']['signed_native_retention']
        with self.assertRaisesRegex(ValueError,'rebalances harmful'): compile_policy(f,authored=a)
    def test_source_literal_change_is_not_new_authorization(self):
        f,a=inputs();f['foods']['fallout_wastelands_:coffee']['food']['saturation_modifier']=0.0
        a['foods']['fallout_wastelands_:coffee']['signed_native_retention']['native_food']['saturation_modifier']=0.0
        with self.assertRaisesRegex(ValueError,'source literals'): compile_policy(f,authored=a)
    def test_role_work_or_acceptance_claim_mutations_rejected(self):
        for change in ('role','work','reviewed','acceptance','missing_evidence','missing_holds'):
            with self.subTest(change=change):
                f,a=inputs();r=a['foods']['fallout_wastelands_:coffee']
                if change=='role':r['role']='special'
                elif change=='work':r['work_class']='none'
                elif change=='reviewed':r['reviewed'].append('work_class')
                elif change=='acceptance':r['signed_native_retention']['native_acceptance']=True
                elif change=='missing_evidence':r['evidence']=['UNIT_ONLY_unbound']
                else:r['unresolved']=[]
                with self.assertRaises(ValueError): compile_policy(f,authored=a)
    def test_clamp_or_meal_or_resolve_profile_rejected(self):
        for change in ('clamp','meal','basis','resolved','water','nutrients'):
            with self.subTest(change=change):
                f,a=inputs();p=compile_policy(f,authored=a);r=p['foods']['fallout_wastelands_:coffee']
                if change=='clamp':r['nutrition']['saturation_modifier']=0
                elif change=='meal':r['role']='meal';r['quantity_basis']='per_portion'
                elif change=='basis':r['nutrition_basis']='work_class_standard'
                elif change=='resolved':r['unresolved']=[]
                elif change=='water':r['moisture']['hydration']=-1
                else:r['nutrition']['nutrients']={'proteins':-1}
                with self.assertRaises(ValueError):validate(p)
    def test_negative_dose_never_covered(self):
        f,a=inputs();p=compile_policy(f,authored=a)
        self.assertEqual(p['foods']['fallout_wastelands_:coffee']['native_food']['saturation_modifier'],-.4)
        self.assertIsNone(p['foods']['fallout_wastelands_:coffee']['native_consumption'])
        self.assertEqual(p['foods']['fallout_wastelands_:coffee']['nutrition']['nutrients'],{})

if __name__=='__main__': unittest.main()
