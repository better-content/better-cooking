"""Finite source decisions only; source assembly is not qualified pastry finishing."""
import json
from pathlib import Path
import sys
import unittest

TOOLS=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(TOOLS))
from compile_policy import _compile_policy_model as compile_policy

class UndergardenTconstructReviewTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.approved=json.loads((TOOLS/'authored/family-reviews.json').read_text())
        cls.frozen=json.loads((TOOLS/'frozen/catalogue_inputs.json').read_text())
        cls.policy=compile_policy(cls.frozen,authored=json.loads((TOOLS/'authored/decisions.json').read_text()))
    def food(self,name):return self.policy['foods'][name]
    def raw(self,rid):return self.approved['routes'][rid]['original_artifact_resource']
    def test_exact_finite_membership_and_holds(self):
        for prefix,count,work,roles in [('undergarden:',19,11,2),('tconstruct:',16,8,1)]:
            rows={k:v for k,v in self.approved['foods'].items() if k.startswith(prefix)}
            self.assertEqual(len(rows),count)
            self.assertEqual(sum(r['work_class']=='unresolved' for r in rows.values()),work)
            self.assertEqual(sum(r['role']=='unresolved' for r in rows.values()),roles)
    def test_semantic_utility_role_holds_not_special_or_zero_exemptions(self):
        for item in ['undergarden:blood_globule','undergarden:goo_ball','tconstruct:jeweled_apple']:
            p=self.food(item)
            self.assertEqual(p['role'],'unresolved')
            self.assertEqual(p['authored_review']['reviewed'],[])
            self.assertIn('food_role_requires_review',p['unresolved'])
            self.assertIn('authored_work_class_requires_explicit_decision',p['unresolved'])
    def test_accessible_basic_staples_not_forced_to_meal(self):
        for item in ['undergarden:cooked_gwibling','undergarden:dweller_steak','undergarden:roasted_underbeans','tconstruct:bacon','tconstruct:meat_soup']:
            self.assertEqual((self.food(item)['role'],self.food(item)['work_class']),('staple','none'))
    def test_undergarden_heat_retains_all_four_literal_pairs(self):
        for item in ['dweller_meat','gloomper_leg','gwibling','underbeans']:
            for method,ticks in [('smelt',200),('smoke',100)]:
                raw=self.raw('undergarden:'+method+'_'+item)
                self.assertEqual(raw['cookingtime'],ticks)
                self.assertEqual(raw['experience'],.35)
    def test_mushroom_stews_repeated_inputs_not_water_heat(self):
        for color in ['bloody','indigo','inky','veiled']:
            raw=self.raw('undergarden:'+color+'_stew')
            self.assertEqual(len(raw['ingredients']),4)
            self.assertEqual(raw['ingredients'][0],{'item':'minecraft:bowl'})
            self.assertEqual(raw['ingredients'][1:],3*[raw['ingredients'][1]])
            self.assertNotIn('cookingtime',raw)
            self.assertEqual(self.food('undergarden:'+color+'_stew')['authored_review']['work_class'],'unresolved')
    def test_rotten_berry_real_explosion_before_finish_is_not_harmless(self):
        r=self.approved['foods']['undergarden:rotten_blisterberry']
        self.assertIn('ExplosionInteraction.NONE BEFORE',r['reason'])
        self.assertEqual(r['work_class'],'unresolved')
        self.assertNotIn('adapter',r['reviewed'])
    def test_featherweight_is_multiplier_not_additive_knockback(self):
        r=self.approved['foods']['undergarden:veiled_stew']
        self.assertIn('MULTIPLIES original knockback strength by (amplifier+2)',r['reason'])
        self.assertIn('fmul',r['p4_semantic_clarification'])
    def test_cakes_and_pie_hold_canonical_finish_not_cold_pastry_approval(self):
        names=['undergarden:gloomgourd_pie']+['tconstruct:'+s+'_cake' for s in ['blood','earth','ender','ichor','magma','sky']]
        for item in names:
            p=self.food(item)
            self.assertIn('canonical_raw_pastry_typed_bake_finish_clock_portion_carrier_requires_separate_root_decision',p['unresolved'])
            self.assertIn('not canonical cold-finished pastry approval',p['authored_review']['reason'])
            self.assertNotIn('adapter',p['authored_review']['reviewed'])
    def test_blood_cake_honey_and_ichor_inversion_not_guessed_milk(self):
        blood=self.raw('tconstruct:gadgets/cake/blood')
        self.assertEqual(blood['key']['M'],{'item':'tconstruct:honey_bucket'})
        self.assertEqual(blood['pattern'],['MMM','SES','WWW'])
        self.assertEqual(self.raw('tconstruct:gadgets/cake/ichor')['pattern'],['WWW','SES','MMM'])
    def test_six_cake_block_intakes_are_not_observed_item_servings(self):
        for kind in ['blood','earth','ender','ichor','magma','sky']:
            s=self.approved['foods']['tconstruct:'+kind+'_cake']['source_review_findings']['source_declarations']
            self.assertFalse(s['native_getters_observed'])
            self.assertFalse(s['native_effects_or_doses_observed'])
            self.assertIsNone(s['observed_servings'])
    def test_cheese_four_to_one_packing_not_nine_or_qualified_cure(self):
        self.assertEqual(self.raw('tconstruct:common/cheese_block_from_ingot')['pattern'],['##','##'])
        self.assertEqual(self.raw('tconstruct:common/cheese_ingot_from_block')['result']['count'],4)
        for item in ['cheese_ingot','cheese_block']:
            r=self.approved['foods']['tconstruct:'+item]
            self.assertEqual(r['work_class'],'unresolved')
            if item=='cheese_block':
                self.assertIn('THREE cheese ingots',json.dumps(r['utility_review']))
    def test_fixed_bottling_units_not_observed_intake_or_NBT(self):
        for kind in ['earth','sky','ender','ichor']:
            p=self.food('tconstruct:'+kind+'_slime_bottle')
            self.assertEqual(p['role'],'drink')
            self.assertEqual(p['authored_review']['work_class'],'unresolved')
            raw=self.raw('tconstruct:smeltery/casting/slime/'+kind+'/bottle')
            self.assertEqual((raw['fluid']['amount'],raw['cooling_time']),(250,1))
            self.assertEqual(raw['cast'],{'item':'minecraft:glass_bottle'})
            self.assertTrue(raw['cast_consumed'])
    def test_artifact_candidates_outside_effective_membership(self):
        for rid in ['blisterbomb','goo_block','sticky_piston_from_goo_ball','underbean_on_a_stick','undergarden_lead']:
            rid='undergarden:'+rid
            self.assertIn(rid,self.approved['source_route_candidates'])
            self.assertNotIn(rid,self.policy['routes'])
    def test_no_tool_signed_native_adapter_or_new_plan_authority(self):
        for item,r in self.approved['foods'].items():
            if item.startswith(('undergarden:','tconstruct:')):
                self.assertNotIn('adapter',r['reviewed'])
                self.assertNotIn('signed_native_retention',r)
                self.assertTrue(self.food(item)['unresolved'])
        for rid,r in self.approved['routes'].items():
            if rid.startswith(('undergarden:','tconstruct:')):
                self.assertEqual(r['disposition'],'deferred')
                self.assertIsNone(self.policy['routes'][rid]['replacement'])
        self.assertEqual(self.policy['qualification'],'unqualified')

if __name__=='__main__':unittest.main()
