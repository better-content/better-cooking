"""Compiler clock arithmetic only; no claim of native connected-player timing."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from compile_policy import active_ticks, _compile_policy_model as compile_policy, validate
from test_policy import fixture


class WorkClockTests(unittest.TestCase):
    def test_eight_portion_targets(self):
        self.assertEqual(active_ticks('straightforward', 8), 30 * 20)
        self.assertEqual(active_ticks('substantial', 8), 90 * 20)
        self.assertEqual(active_ticks('elaborate', 8), 240 * 20)
    def test_half_and_double_batch_proportional(self):
        for kind in ('straightforward', 'substantial', 'elaborate'):
            self.assertEqual(active_ticks(kind, 4) * 2, active_ticks(kind, 8))
            self.assertEqual(active_ticks(kind, 16), active_ticks(kind, 8) * 2)
    def test_whole_stack_noodle_plan_not_one_portion_clock(self):
        self.assertEqual(active_ticks('straightforward', 64), active_ticks('straightforward', 1) * 64)
    def test_individual_portions_positive(self):
        for kind in ('straightforward', 'substantial', 'elaborate'): self.assertGreater(active_ticks(kind, 1), 0)
    def test_reject_zero_yield(self):
        with self.assertRaises(ValueError): active_ticks('straightforward', 0)
    def test_reject_fractional_portion_count(self):
        with self.assertRaises(ValueError): active_ticks('straightforward', .5)
    def test_reject_boolean_count(self):
        with self.assertRaises(ValueError): active_ticks('straightforward', True)
    def test_reject_unknown_class(self):
        with self.assertRaises(ValueError): active_ticks('superb', 8)
    def test_nested_output_tamper_rejected(self):
        p = compile_policy(fixture()); p['routes']['example:salad']['replacement']['output']['count'] = 8
        with self.assertRaisesRegex(ValueError, 'ratio'): validate(p)
    def test_nonpositive_stage_rejected(self):
        p = compile_policy(fixture()); p['routes']['example:salad']['stages'][0]['duration_ticks'] = 0
        with self.assertRaisesRegex(ValueError, 'stage'): validate(p)
    def test_profile_per_item_route_total_batch(self):
        f = fixture(); f['servings']['example:salad'] = {'portions': 3, 'evidence': ['test']}
        p = compile_policy(f)
        self.assertEqual(p['foods']['example:salad']['servings'], 3)
        self.assertEqual(p['foods']['example:salad']['quantity_basis'], 'per_portion')
        self.assertEqual(p['foods']['example:salad']['serving_metadata']['placed_bite_multiplier'], 1)
        self.assertEqual(p['routes']['example:salad']['servings'], 6)
    def test_retained_special_nutrition_is_not_multiplied_twice(self):
        p = compile_policy(fixture())
        self.assertEqual(p['foods']['minecraft:apple']['quantity_basis'], 'native_intake')
    def test_native_flex_quality_uses_original_id_and_runtime_seed(self):
        f = fixture(); f['recipes']['example:salad']['type'] = 'kaleidoscope_cookery:flex_pot'
        p = compile_policy(f); q = p['routes']['example:salad']['native_quality']
        self.assertEqual(q['source_recipe_id'], 'example:salad')
        self.assertEqual(q['seed_source'], 'actual_runtime_world_seed')
        self.assertEqual(q['input_basis'], 'actual_paid_counted_inputs')
    def test_generated_preparation_carries_original_quality_identity(self):
        f = fixture(); f['recipes']['example:salad']['type'] = 'kaleidoscope_cookery:flex_stockpot'
        p = compile_policy(f)
        self.assertEqual(p['migration']['recipes'][0]['json']['native_quality']['source_recipe_id'], 'example:salad')
    def test_reject_quality_recipe_id_substitution(self):
        f = fixture(); f['recipes']['example:salad']['type'] = 'kaleidoscope_cookery:flex_pot'
        p = compile_policy(f); p['routes']['example:salad']['native_quality']['source_recipe_id'] = p['routes']['example:salad']['id']
        with self.assertRaisesRegex(ValueError, 'original flexible'): validate(p, f)
    def test_fixed_recipes_no_seeded_reroll(self):
        self.assertEqual(compile_policy(fixture())['routes']['example:salad']['native_quality']['mode'], 'fixed_standard_unless_inherited_preparation')
    def test_serving_work_not_output_count_mutation(self):
        f = fixture(); f['servings']['example:salad'] = {'portions': 3, 'evidence': ['test']}
        r = compile_policy(f)['routes']['example:salad']
        self.assertEqual(r['servings'], 6)
        self.assertEqual(r['replacement']['output']['count'], 2)
        self.assertEqual(r['stages'][0]['duration_ticks'], active_ticks('straightforward', 6))


if __name__ == '__main__': unittest.main()
