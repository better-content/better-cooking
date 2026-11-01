"""Finite P4 source reviews, not a namespace classifier/native acceptance."""
import json
from pathlib import Path
import sys
import unittest

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from compile_policy import _compile_policy_model as compile_policy

class FamilyReviewTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.approved = json.loads((TOOLS/'authored/family-reviews.json').read_text())
        cls.frozen = json.loads((TOOLS/'frozen/catalogue_inputs.json').read_text())
        cls.policy = compile_policy(cls.frozen, authored=json.loads((TOOLS/'authored/decisions.json').read_text()))
    def test_finite_exact_ids_and_primary_sources(self):
        self.assertEqual(len(self.approved['foods']), 550)
        self.assertEqual(len(self.approved['routes']), 472)
        self.assertEqual(len(self.approved['source_route_candidates']), 42)
        for name in self.approved['source_inputs']:
            self.assertNotIn('review-queue', name)
            self.assertNotIn('culinary_policy.json', name)
            self.assertNotIn('review-drafts', name)
    def test_all_one_hundred_ten_work_holds_are_partial_not_free_exemptions(self):
        held = {k for k,v in self.approved['foods'].items() if v['work_class']=='unresolved'}
        self.assertEqual(len(held), 110)
        self.assertIn('fruitsdelight:hawberry_sheet', held)
        self.assertIn('alexscaves:hot_chocolate_bottle', held)
        for item in held:
            p = self.policy['foods'][item]
            self.assertEqual(p['classification'], 'partially_authored_review_pending_native')
            self.assertIn('authored_work_class_requires_explicit_decision', p['unresolved'])
    def test_no_adapter_or_moisture_approvals_smuggled_with_role(self):
        for item, decision in self.approved['foods'].items():
            self.assertNotIn('adapter', decision['reviewed'])
            self.assertNotIn('moisture', decision['reviewed'])
            self.assertTrue(decision['utility_review'])
            self.assertTrue(self.policy['foods'][item]['unresolved'])
    def test_nuggets_remain_paid_plain_portions_not_new_meal(self):
        p = self.policy['foods']['alexscaves:dinosaur_nugget']
        self.assertEqual((p['role'], p['work_class']), ('staple','none'))
        self.assertTrue(any('12_nuggets' in gap for gap in p['unresolved']))
    def test_architectural_storage_and_source_effects_not_meals_or_zero_effects(self):
        p = self.policy['foods']['alexscaves:gingerbarrel']
        self.assertEqual(p['role'], 'special')
        self.assertIn('SUGAR_RUSH', str(p['authored_review']['utility_review']))
        self.assertEqual(self.policy['foods']['alexscaves:jelly_bean']['role'], 'special')
    def test_fish_tag_exceptions_are_not_force_cooked_or_aliased(self):
        for suffix in ('black_eel','geode_eel','chorus_crab'):
            p = self.policy['foods']['starcatcher:'+suffix]
            findings = p['authored_review']['source_review_findings']
            self.assertEqual(findings['source_transform_recipe_ids'], [])
            self.assertFalse(findings['source_transform_tag_review']['literal_source_member'])
            self.assertEqual((p['role'],p['work_class']),('ingredient','none'))
    def test_suspicious_stew_is_not_a_free_renewable_utility_exemption(self):
        p = self.policy['foods']['minecraft:suspicious_stew']
        self.assertEqual((p['role'],p['work_class']),('meal','straightforward'))
        self.assertIn('P4 OVERRIDES',p['authored_review']['reason'])
        self.assertNotIn('adapter',p['authored_review']['reviewed'])
        self.assertIn('suspicious_stew_flower_effect_NBT_requires_actual_stack_effect_transaction',p['unresolved'])
    def test_plain_native_heat_and_bread_keep_no_meal_work(self):
        for item in ('minecraft:bread','minecraft:cooked_beef','minecraft:dried_kelp','starcatcher:cooked_starcaught_fish'):
            self.assertEqual((self.policy['foods'][item]['role'],self.policy['foods'][item]['work_class']),('staple','none'))
    def test_deep_role_holds_not_coerced_to_special_or_zero(self):
        for name in ('call_upon_the_hive','knightfish','voodoo_doll'):
            p=self.policy['foods']['the_deep_void:'+name]
            self.assertEqual(p['role'],'unresolved')
            self.assertEqual(p['authored_review']['reviewed'],[])
            self.assertIn('food_role_requires_review',p['unresolved'])
        self.assertEqual(self.policy['foods']['the_deep_void:knightfish']['nutrition']['hunger'],-99)
        self.assertEqual(self.policy['foods']['the_deep_void:voodoo_doll']['nutrition']['saturation_modifier'],-.4)
    def test_fallout_canned_beef_exact_spelling_and_batch_retained(self):
        r=self.approved['routes']['fallout_wastelands_:cannedbeefcraft']
        raw=r['original_artifact_resource']
        self.assertEqual(raw['result']['count'],8)
        self.assertEqual(raw['key']['b'],{'item':'fallout_wastelands_:brahim_meat'})
        self.assertEqual(raw['pattern'],['aaa','aba','aaa'])
        self.assertNotIn('fallout_wastelands_:brahim_meat',self.policy['aliases'])
    def test_fallout_rollingpin_is_paid_ordinary_ingredient_not_free_tool(self):
        for rid in ('mirelurk_cake_craft','strange_meat_patty_craft'):
            r=self.approved['routes']['fallout_wastelands_:'+rid]
            self.assertIn({'item':'fallout_wastelands_:rollingpin'},r['original_artifact_resource']['ingredients'])
            self.assertEqual(r['disposition'],'deferred')
    def test_deep_plain_heat_and_knife_free_four_slice_source(self):
        for name in ('cooked_bloodfin','cooked_void_fly_maggot','lamprey'):
            self.assertEqual(self.policy['foods']['the_deep_void:'+name]['work_class'],'none')
        raw=self.approved['routes']['the_deep_void:cooked_void_fly_maggot_smoker']['original_artifact_resource']
        self.assertEqual(raw['cookingtime'],30)
        raw=self.approved['routes']['the_deep_void:eye_cake_slice']['original_artifact_resource']
        self.assertEqual(raw['pattern'],['a']); self.assertEqual(raw['result']['count'],4)
        self.assertEqual(raw['key'],{'a':{'item':'the_deep_void:eye_cake'}})
    def test_born_eternal_candy_is_role_and_work_hold_not_free_device(self):
        p=self.policy['foods']['born_in_chaos_v1:eternal_candy']
        self.assertEqual(p['role'],'unresolved')
        self.assertEqual(p['authored_review']['work_class'],'unresolved')
        self.assertIn('authored_work_class_requires_explicit_decision',p['unresolved'])
        self.assertEqual(p['authored_review']['reviewed'],[])
        self.assertIn('NOT a condition',p['authored_review']['reason'])
        self.assertIn('removes ROTTEN_SMELL',p['authored_review']['reason'])
    def test_born_cookies_not_name_based_drink_or_one_output(self):
        self.assertEqual(self.policy['foods']['born_in_chaos_v1:creepy_cookies_with_milk']['role'],'meal')
        raw=self.approved['routes']['born_in_chaos_v1:creepy_cookies_with_milk_craft']['original_artifact_resource']
        self.assertEqual(raw['result']['count'],2)
        self.assertEqual(raw['ingredients'].count({'item':'minecraft:glass_bottle'}),2)
    def test_born_basic_heat_uses_literal_apparatus_clocks(self):
        for rid,clock in [('fried_maggot_k',100),('fried_maggot_k_2',50),('fried_maggot_k_3',160)]:
            self.assertEqual(self.approved['routes']['born_in_chaos_v1:'+rid]['original_artifact_resource']['cookingtime'],clock)
        self.assertEqual(self.policy['foods']['born_in_chaos_v1:fried_maggot']['work_class'],'none')
    def test_thal_egg_and_toxin_utility_not_harmless_ingredient_waivers(self):
        for name in ('hesperornis_egg','natural_toxin'):
            p=self.policy['foods']['thalassophobia:'+name]
            self.assertEqual(p['authored_review']['work_class'],'unresolved')
            self.assertIn('authored_work_class_requires_explicit_decision',p['unresolved'])
            self.assertEqual(p['authored_review']['reviewed'],['role'])
    def test_thal_stew_and_jellybread_combining_not_invented_water_heat(self):
        for name in ('fish_stew','jellyfish_jelly_bread'):
            p=self.policy['foods']['thalassophobia:'+name]
            self.assertEqual((p['role'],p['work_class']),('meal','straightforward'))
            self.assertNotIn('adapter',p['authored_review']['reviewed'])
        raw=self.approved['routes']['thalassophobia:fish_stew_recipe']['original_artifact_resource']
        self.assertEqual(raw['key']['e'],{'item':'minecraft:bowl'})
        self.assertNotIn('cookingtime',raw)
    def test_born_outside_scope_outputs_never_promoted(self):
        self.assertIn('born_in_chaos_v1:transmuting_elixirkraft',self.approved['source_route_candidates'])
        self.assertNotIn('born_in_chaos_v1:transmuting_elixirkraft',self.policy['routes'])
        self.assertNotIn('born_in_chaos_v1:transmuting_elixir',self.approved['foods'])
    def test_no_new_family_replacements_or_source_candidate_promotion(self):
        self.assertTrue(all(v['disposition']!='replace' for v in self.approved['routes'].values()))
        for rid in self.approved['source_route_candidates']:
            self.assertNotIn(rid, self.policy['routes'])
        self.assertEqual(self.policy['qualification'], 'unqualified')

if __name__ == '__main__': unittest.main()
