"""Finite source decisions/conservation only; never getters or native acceptance."""
import json
from pathlib import Path
import sys
import unittest

TOOLS=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(TOOLS))
from compile_policy import _compile_policy_model as compile_policy

class BrewEanimodReviewTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.approved=json.loads((TOOLS/'authored/family-reviews.json').read_text())
        cls.frozen=json.loads((TOOLS/'frozen/catalogue_inputs.json').read_text())
        cls.policy=compile_policy(cls.frozen,authored=json.loads((TOOLS/'authored/decisions.json').read_text()))
    def food(self,name): return self.policy['foods'][name]
    def raw(self,rid): return self.approved['routes'][rid]['original_artifact_resource']
    def test_unripe_wheels_roles_and_work_are_held_not_edible_zero(self):
        for name in ('unripe_flaxen_cheese_wheel','unripe_scarlet_cheese_wheel'):
            p=self.food('brewinandchewin:'+name)
            self.assertEqual(p['role'],'unresolved')
            self.assertEqual(p['authored_review']['work_class'],'unresolved')
            self.assertEqual(p['authored_review']['reviewed'],[])
            self.assertIn('food_role_requires_review',p['unresolved'])
    def test_cocoa_utility_not_none_or_invented_heating(self):
        p=self.food('brewinandchewin:cocoa_fudge')
        self.assertEqual(p['authored_review']['work_class'],'unresolved')
        self.assertIn('authored_work_class_requires_explicit_decision',p['unresolved'])
        raw=self.raw('brewinandchewin:fermenting/cocoa_fudge')
        self.assertEqual((raw['fermentingtime'],raw['temperature']),(9600,2))
        self.assertEqual(raw['basefluid']['count'],500)
    def test_four_native_fermented_staples_stay_accessible(self):
        for name in ('jerky','kimchi','kippers','pickled_pickles'):
            p=self.food('brewinandchewin:'+name)
            self.assertEqual((p['role'],p['work_class']),('staple','none'))
            r=self.raw('brewinandchewin:fermenting/'+name)
            self.assertEqual(r['fermentingtime'],9600)
            self.assertEqual(self.approved['routes']['brewinandchewin:fermenting/'+name]['disposition'],'deferred')
    def test_jerky_no_invented_base_and_pickles_exact_honey(self):
        self.assertNotIn('basefluid',self.raw('brewinandchewin:fermenting/jerky'))
        raw=self.raw('brewinandchewin:fermenting/pickled_pickles')
        self.assertEqual(raw['basefluid']['count'],250)
        self.assertIn('honey',str(raw['basefluid']))
    def test_pizza_reconstruction_candidate_not_promoted(self):
        rid='brewinandchewin:pizza_from_slices'
        self.assertIn(rid,self.approved['source_route_candidates'])
        self.assertNotIn(rid,self.policy['routes'])
    def test_ripe_wheels_ingredient_carriers_not_native_servings(self):
        for name in ('flaxen_cheese_wheel','scarlet_cheese_wheel'):
            p=self.food('brewinandchewin:'+name)
            self.assertEqual((p['role'],p['work_class']),('ingredient','none'))
            self.assertNotIn('servings',p['authored_review']['reviewed'])
            self.assertTrue(p['unresolved'])
    def test_seven_milk_utility_work_holds_not_live_zero(self):
        names=('fivesixths_milk_bucket','half_milk_bottle','half_milk_bucket','milk_bottle','onesixth_milk_bucket','onethird_milk_bucket','twothirds_milk_bucket')
        for name in names:
            p=self.food('eanimod:'+name);r=p['authored_review']
            self.assertEqual(p['role'],'drink')
            self.assertEqual(r['work_class'],'unresolved')
            self.assertEqual(r['reviewed'],['role'])
            self.assertIn('authored_work_class_requires_explicit_decision',p['unresolved'])
            s=r['source_review_findings']['source_declarations']
            self.assertIsNone(s['milk_mB'])
            self.assertFalse(s['native_getters_observed'])
    def test_milk_same_draw_and_no_base_finish_preserved(self):
        r=self.approved['foods']['eanimod:fivesixths_milk_bucket']['source_review_findings']['milk_source_callback']
        self.assertEqual(r['random_nextInt_bound_source'],6)
        self.assertEqual(r['same_random_predicate_for_cure_and_removeAllEffects'],'nonzero')
        self.assertFalse(r['finish_calls_base_finish'])
        self.assertIn('curePotionEffects',r['whole_finish_body'])
        self.assertIn('m_21219_',r['whole_finish_body'])
    def test_raw_chicken_risk_not_erased_by_ingredient_role(self):
        p=self.food('eanimod:rawchicken_dark')
        self.assertEqual((p['role'],p['work_class']),('ingredient','none'))
        findings=p['authored_review']['source_review_findings']
        self.assertIn('f_19612_',findings['source_declarations']['factory_body'])
        self.assertIn('sipush 600',findings['source_declarations']['factory_body'])
    def test_eanimod_campfire_missing_experience_stays_absent(self):
        r=self.raw('eanimod:campfirechicken_dark')
        self.assertEqual((r['type'],r['cookingtime']),('campfire_cooking',600))
        self.assertNotIn('experience',r)
    def test_weak_stew_combining_not_inferred_bowl_return(self):
        p=self.food('eanimod:rabbitstew_weak')
        self.assertEqual((p['role'],p['work_class']),('meal','straightforward'))
        self.assertNotIn('adapter',p['authored_review']['reviewed'])
        s=p['authored_review']['source_review_findings']['source_declarations']
        self.assertNotIn('BowlFoodItem',str(s['constructor_members']))
        self.assertEqual(len(s['constructor_members']),1)
        r=self.raw('eanimod:brownrabbitstew_weak')
        self.assertEqual(len(r['ingredients']),5)
        self.assertNotIn('cookingtime',r)
    def test_no_adapter_native_or_signed_exception_approval(self):
        for item,r in self.approved['foods'].items():
            if item.startswith(('brewinandchewin:','eanimod:')):
                self.assertNotIn('adapter',r['reviewed'])
                self.assertNotIn('signed_native_retention',r)
                self.assertTrue(self.food(item)['unresolved'])
        self.assertEqual(self.policy['qualification'],'unqualified')

if __name__=='__main__':unittest.main()
