import copy
import json
from pathlib import Path
import sys
import unittest

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from extract_catalogue import encoded, historical_recipe, resource_recipe, merge_tag
from compile_policy import _compile_policy_model as compile_policy, validate, resolve_tags, report, review_tables
from recipe_migration import recipe_pair, forge_ingredients, build_plan


def fixture():
    recipes = {
        'example:salad': resource_recipe('example:salad', {
            'type': 'minecraft:crafting_shapeless',
            'ingredients': [{'item': 'minecraft:apple'}, {'item': 'farmersdelight:cabbage'}, {'item': 'minecraft:bowl'}],
            'result': {'item': 'example:salad', 'count': 2}}, 'test'),
        'example:milk': resource_recipe('example:milk', {
            'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'minecraft:milk_bucket'}],
            'result': {'item': 'example:milk', 'count': 4}}, 'test')}
    return {'snapshot': {'snapshot_id': 'test'}, 'manifest': {}, 'limitations': ['not current'],
            'items': ['minecraft:apple', 'farmersdelight:cabbage', 'minecraft:bowl', 'minecraft:milk_bucket', 'example:salad', 'example:milk'],
            'item_tags': {'forge:crops/cabbage': ['farmersdelight:cabbage'], 'diet:proteins': ['example:not_food']},
            'resource_tags': {}, 'servings': {}, 'recipes': recipes,
            'foods': {i: {'food': {'nutrition': 4, 'saturation_modifier': .3}, 'effects': None, 'evidence': ['test']} for i in ['minecraft:apple', 'farmersdelight:cabbage', 'example:salad', 'example:milk']}}


class CompilerTests(unittest.TestCase):
    def setUp(self): self.frozen = fixture(); self.policy = compile_policy(self.frozen)

    def test_deterministic(self): self.assertEqual(encoded(self.policy), encoded(compile_policy(self.frozen)))
    def test_keyed_v3_shape(self):
        self.assertIsInstance(self.policy['foods'], dict); self.assertIsInstance(self.policy['routes'], dict)
        self.assertEqual(self.policy['foods']['example:salad']['nutrition']['hunger'], 6)
    def test_diet_tags_not_edibility(self): self.assertNotIn('example:not_food', self.policy['foods'])
    def test_missing_nutrient_observation_flag_is_not_known_empty_map(self):
        self.assertIn('native_nutrients_not_enumerated', self.policy['foods']['minecraft:apple']['unresolved'])
    def test_recipe_ingredient_closure_not_limited_to_edible_catalogue(self):
        route = self.policy['routes']['example:salad']
        self.assertIn('actual_paid_input_nutrition_water_and_carrier_facts_require_complete_resolution', route['unresolved'])
        self.assertTrue(route['ingredient_intake_contract']['edible_food_catalogue_is_not_complete_ingredient_facts'])
        self.assertEqual(route['ingredient_intake_contract']['unknown_profile_or_optional'], 'unresolved_never_zero_or_empty_map')

    def test_unqualified_without_native(self): self.assertEqual(self.policy['qualification'], 'unqualified')
    def test_no_fake_native_effects(self): self.assertIn('native_effects_not_enumerated', self.policy['foods']['example:salad']['unresolved'])
    def test_unknown_moisture_null(self): self.assertIsNone(self.policy['foods']['example:milk']['moisture']['hydration'])
    def test_exact_recipe_removal(self): self.assertEqual(self.policy['routes']['example:salad']['replaces'], ['example:salad'])
    def test_original_ratio_preserved(self):
        replacement = self.policy['routes']['example:salad']['replacement']
        self.assertEqual(replacement['output'], {'id': 'example:salad', 'count': 2})
        self.assertEqual(len(replacement['input_groups']), 3)
    def test_no_magic_eight_yield(self): self.assertEqual(self.policy['routes']['example:salad']['output']['count'], 2)
    def test_work_scales_portions(self): self.assertEqual(self.policy['routes']['example:salad']['stages'][0]['duration_ticks'], 150)
    def test_origin_gate_actual_ingredient(self):
        self.assertEqual(self.policy['routes']['example:salad']['origin_gates'][0]['canonical_members'], ['farmersdelight:cabbage'])
    def test_no_gate_is_synthetic_input(self): self.assertEqual(len(self.policy['routes']['example:salad']['input_groups']), 3)
    def test_double_credit_prohibited(self): self.assertIn('no_direct_tag_double_credit', self.policy['defaults']['derived_nutrients'])
    def test_water_not_quality_scaled(self): self.assertIn('water', self.policy['defaults']['quality_never_scales'])
    def test_age_carry(self): self.assertEqual(self.policy['defaults']['ingredient_age'], 'oldest_settled_input')
    def test_reject_duplicate_route(self):
        self.policy['routes']['example:other'] = copy.deepcopy(self.policy['routes']['example:salad'])
        self.policy['routes']['example:other']['source_id'] = 'example:other'
        with self.assertRaisesRegex(ValueError, 'duplicate route'): validate(self.policy)
    def test_reject_bad_output_ratio(self):
        self.policy['routes']['example:salad']['output']['count'] = 8
        with self.assertRaisesRegex(ValueError, 'ratio'): validate(self.policy, self.frozen)
    def test_reject_bad_input_ratio(self):
        self.policy['routes']['example:salad']['input_groups'][0][0]['count'] = 8
        with self.assertRaisesRegex(ValueError, 'ratios'): validate(self.policy, self.frozen)
    def test_reject_blanket_removal(self):
        self.policy['routes']['example:salad']['replaces'] = ['example:*']
        with self.assertRaisesRegex(ValueError, 'exact source'): validate(self.policy)
    def test_reject_alias_cycle(self):
        self.policy['aliases'] = {'example:a': 'example:b', 'example:b': 'example:a'}
        with self.assertRaisesRegex(ValueError, 'cycle'): validate(self.policy)
    def test_reject_invalid_food(self):
        self.policy['foods']['example:salad']['nutrition']['hunger'] = float('nan')
        with self.assertRaisesRegex(ValueError, 'food value'): validate(self.policy)
    def test_reject_premature_qualification(self):
        self.policy['qualification'] = 'qualified'
        with self.assertRaisesRegex(ValueError, 'unresolved'): validate(self.policy)
    def test_negative_native_foods_not_silently_changed(self):
        self.frozen['foods']['example:milk']['food']['nutrition'] = -4
        food = compile_policy(self.frozen)['foods']['example:milk']
        self.assertEqual(food['native_food']['nutrition'], -4)
        self.assertEqual(food['nutrition']['hunger'], -4)
        self.assertEqual(food['role'], 'special')
        self.assertEqual(food['quantity_basis'], 'native_intake')
    def test_unknown_reference_retained_as_gap(self):
        self.frozen['recipes']['example:salad']['input_groups'][0][0]['id'] = 'missing:apple'
        route = compile_policy(self.frozen)['routes']['example:salad']
        self.assertIn('unknown_item_reference:missing:apple', route['unresolved'])
    def test_empty_tag_reference_retained_as_gap(self):
        self.frozen['recipes']['example:salad']['input_groups'][0][0].update(kind='tag', id='missing:apples')
        route = compile_policy(self.frozen)['routes']['example:salad']
        self.assertIn('empty_or_unknown_tag_reference:missing:apples', route['unresolved'])
    def test_distinct_brewing_retained(self):
        self.frozen['recipes']['example:salad']['type'] = 'farmersrespite:brewing'
        self.assertEqual(compile_policy(self.frozen)['routes']['example:salad']['disposition'], 'retain_distinct_apparatus')
    def test_nonfood_cuts_retained(self):
        self.frozen['recipes']['example:salad']['outputs'][0]['id'] = 'example:plank'
        r = compile_policy(self.frozen)['routes']['example:salad']
        self.assertEqual(r['disposition'], 'retain_nonfood'); self.assertEqual(r['replaces'], [])
    def test_canonical_alias_keeps_old_stock_and_quantity(self):
        self.frozen['items'].extend(['kaleidoscope_cookery:tomato', 'farmersdelight:tomato'])
        self.frozen['recipes']['example:salad']['input_groups'][0] = [{'kind': 'item', 'id': 'kaleidoscope_cookery:tomato', 'count': 2}]
        p = compile_policy(self.frozen)
        group = p['routes']['example:salad']['replacement']['input_groups'][0]
        self.assertEqual(group, [{'kind': 'item', 'id': 'kaleidoscope_cookery:tomato', 'count': 2}, {'kind': 'item', 'id': 'farmersdelight:tomato', 'count': 2}])
        self.assertEqual(p['routes']['example:salad']['original_input_groups'][0], group[:1])
        validate(p, self.frozen)
    def test_review_unknown_food_rejected(self):
        with self.assertRaisesRegex(ValueError, 'unknown food'): compile_policy(self.frozen, {'foods': {'example:missing': {}}})
    def test_review_ratio_change_rejected(self):
        with self.assertRaisesRegex(ValueError, 'ratio'): compile_policy(self.frozen, {'routes': {'example:salad': {'replacement': {'output': {'id': 'example:salad', 'count': 8}}}}})
    def test_v3_review_shape_not_private_flat_shape(self):
        p = compile_policy(self.frozen, {'foods': {'example:salad': {'moisture': {'hydration': 1.5, 'quenching': 2}}}})
        self.assertEqual(p['foods']['example:salad']['moisture']['hydration'], 1.5)
    def test_managed_drink_intake_owner(self):
        self.frozen['managed_drinks'] = {'example:milk': {'hydration': 12, 'quenching': 16, 'serving_mb': 1000, 'hydration_class': 'JUICE_MILK', 'effects': []}}
        p = compile_policy(self.frozen)['foods']['example:milk']
        self.assertEqual(p['moisture'], {'hydration': 12, 'quenching': 16})
        self.assertFalse(p['culinary_food_completion_credit'])
        self.assertEqual(p['intake_owner'], 'better_drinking_water')


class ExtractionTests(unittest.TestCase):
    def test_shape_slots_counted(self):
        r = resource_recipe('example:r', {'type': 'minecraft:crafting_shaped', 'pattern': ['AA', ' B'], 'key': {'A': {'item': 'minecraft:apple'}, 'B': {'tag': 'forge:fruits'}}, 'result': {'item': 'example:food', 'count': 3}}, 'test')
        self.assertEqual(len(r['input_groups']), 3); self.assertEqual(r['outputs'][0]['count'], 3)
    def test_alternatives_are_not_all_required(self):
        r = resource_recipe('example:r', {'type': 'minecraft:crafting_shapeless', 'ingredients': [[{'item': 'minecraft:apple'}, {'item': 'minecraft:carrot'}]], 'result': {'item': 'example:food'}}, 'test')
        self.assertEqual(len(r['input_groups']), 1); self.assertEqual(len(r['input_groups'][0]), 2)
    def test_custom_matchers_never_silently_flatten(self):
        r = resource_recipe('example:r', {'ingredients': [{'type': 'forge:nbt', 'item': 'minecraft:apple', 'nbt': '{}'}]}, 'test')
        self.assertIn('ingredient has additional matching constraints', r['issues'])
    def test_partial_retained(self):
        r = historical_recipe({'id': 'example:r', 'type': 'example:t', 'parsed': False, 'normalization': 'partial'})
        self.assertIn('partial historical normalization', r['issues'])
    def test_tag_additive_layers(self):
        t = {}; merge_tag(t, 'example:t', {'values': ['example:a']}); merge_tag(t, 'example:t', {'values': ['example:b']})
        self.assertEqual(t['example:t']['values'], ['example:a', 'example:b'])
    def test_tag_replace_layer(self):
        t = {'example:t': {'values': ['example:a']}}; merge_tag(t, 'example:t', {'replace': True, 'values': ['example:b']})
        self.assertEqual(t['example:t']['values'], ['example:b'])
    def test_nested_tags(self):
        tags, gaps = resolve_tags({'example:t': ['example:a']}, {'example:s': {'values': ['#example:t', 'example:b']}})
        self.assertEqual(tags['example:s'], ['example:a', 'example:b']); self.assertEqual(gaps, [])
    def test_tag_cycle_fails(self):
        with self.assertRaisesRegex(ValueError, 'tag cycle'): resolve_tags({}, {'example:a': {'values': ['#example:b']}, 'example:b': {'values': ['#example:a']}})
    def test_missing_optional_tag_not_error(self):
        self.assertEqual(resolve_tags({}, {'example:a': {'values': [{'id': '#example:no', 'required': False}]}})[1], [])
    def test_missing_required_tag_gap(self):
        self.assertEqual(len(resolve_tags({}, {'example:a': {'values': ['#example:no']}})[1]), 1)


class MigrationTests(unittest.TestCase):
    def setUp(self): self.route = compile_policy(fixture())['routes']['example:salad']
    def test_registered_preparation_serializer(self): self.assertEqual(recipe_pair(self.route)[0]['json']['type'], 'better_cooking:preparation')
    def test_board_only_no_finish(self): self.assertEqual(len(recipe_pair(self.route)), 1)
    def test_integer_counted_group(self): self.assertEqual(forge_ingredients([[{'kind': 'item', 'id': 'minecraft:apple', 'count': 3}]])[0]['count'], 3)
    def test_alternative_count_mismatch_rejected(self):
        with self.assertRaisesRegex(ValueError, 'quantities differ'): forge_ingredients([[{'kind': 'item', 'id': 'minecraft:apple', 'count': 1}, {'kind': 'item', 'id': 'minecraft:carrot', 'count': 2}]])
    def test_noninteger_quantity_rejected(self):
        with self.assertRaises(ValueError): forge_ingredients([[{'kind': 'item', 'id': 'minecraft:apple', 'count': .5}]])
    def test_finish_requires_paid_batch(self):
        self.route['replacement']['surface'] = 'pot'
        finish = recipe_pair(self.route)[1]['json']
        self.assertEqual(finish['ingredients'][0], {'type': 'better_cooking:prepared_batch', 'route': self.route['replacement']['id']})
    def test_steamer_exact_serializer_key(self):
        self.route['replacement']['surface'] = 'steamer'
        self.assertIn('cook_tick', recipe_pair(self.route)[1]['json'])
    def test_baking_mat_requires_furnace_finish(self):
        self.route['replacement']['surface'] = 'baking_mat'
        pair = recipe_pair(self.route)
        self.assertEqual(pair[0]['json']['surface'], 'baking_mat'); self.assertEqual(pair[1]['json']['type'], 'better_cooking:baking')
    def test_teapot_never_twelve_batches(self):
        self.route['replacement']['surface'] = 'teapot'
        self.assertEqual(recipe_pair(self.route)[1]['json']['ingredient_count'], 1)
    def test_all_finishes_preserve_output(self):
        for surface in ('pot', 'stockpot', 'steamer', 'teapot', 'roasting', 'baking_mat'):
            route = copy.deepcopy(self.route)
            route['replacement']['surface'] = surface
            if surface == 'roasting':
                route['cook_ticks_basis'] = 'source_declared_or_native_getter'
                route['replacement']['cook_ticks'] = 200
            if surface == 'stockpot':
                # Exercise native serializer preservation only for an original
                # stockpot: a craft with no paid base cannot acquire WATER cost.
                route['source_type'] = 'kaleidoscope_cookery:stockpot'
                route['original_resource']['type'] = route['source_type']
            self.assertEqual(recipe_pair(route)[1]['json']['result'], {'item': 'example:salad', 'count': 2})
    def test_roasting_uses_real_campfire_subclass_serializer(self):
        self.route['replacement'].update(surface='roasting', cook_ticks=200)
        self.route['cook_ticks_basis'] = 'source_declared_or_native_getter'
        finish = recipe_pair(self.route)[1]['json']
        self.assertEqual(finish['type'], 'better_cooking:roasting')
        self.assertEqual(finish['cookingtime'], 200)
    def test_roasting_placeholder_clock_is_not_source_duration(self):
        self.route['replacement']['surface'] = 'roasting'
        with self.assertRaisesRegex(ValueError, 'not_placeholder'): recipe_pair(self.route)
    def test_stockpot_never_invents_water_for_crafting_source(self):
        self.route['replacement']['surface'] = 'stockpot'
        with self.assertRaisesRegex(ValueError, 'no_extra_cost_backend'): recipe_pair(self.route)
    def test_reviewed_crafting_stockpot_has_no_added_base(self):
        self.route.update(adapter_reviewed=True, adapter_decision='protected_stockpot_heat_finish')
        self.route['replacement']['surface'] = 'stockpot'
        prep, finish = (record['json'] for record in recipe_pair(self.route))
        self.assertEqual(finish['type'], 'better_cooking:prepared_stockpot')
        self.assertEqual(finish['route'], self.route['replacement']['id'])
        self.assertTrue(finish['empty_carrier'])
        self.assertNotIn('soup_base', finish)
        self.assertNotIn('carrier', finish)
        self.assertFalse(prep['stockpot_cost_contract']['native_acceptance'])
    def test_unreviewed_stockpot_adapter_does_not_opt_in(self):
        self.route.update(adapter_reviewed=False, adapter_decision='protected_stockpot_heat_finish')
        self.route['replacement']['surface'] = 'stockpot'
        with self.assertRaisesRegex(ValueError, 'explicit_reviewed_choice'): recipe_pair(self.route)
    def test_own_stockpot_prepays_fd_carrier_additively(self):
        self.route.update(adapter_reviewed=True, adapter_decision='protected_stockpot_heat_finish', source_type='farmersdelight:cooking')
        self.route['original_resource'].update(type='farmersdelight:cooking', container={'item': 'minecraft:bowl', 'count': 2})
        self.route['replacement']['surface'] = 'stockpot'
        prep, finish = (record['json'] for record in recipe_pair(self.route))
        bowls = [g['count'] for g in prep['ingredients'] if g['ingredient'] == {'item': 'minecraft:bowl'}]
        self.assertEqual(sum(bowls), 5)  # existing1 + two literal outputs * external2
        self.assertEqual(prep['source_external_container']['total_carrier_count'], 4)
        self.assertTrue(finish['empty_carrier'])
        self.assertNotIn('carrier', finish)
    def test_own_stockpot_fd_unknown_container_is_not_empty(self):
        self.route.update(adapter_reviewed=True, adapter_decision='protected_stockpot_heat_finish', source_type='farmersdelight:cooking')
        self.route['replacement']['surface'] = 'stockpot'
        for value in (None, {}, {'id': 'minecraft:air'}):
            self.route['native_semantics'] = {'container': value}
            with self.assertRaisesRegex(ValueError, 'implicit_output_container'): recipe_pair(self.route)
        self.route['native_semantics'] = {'container': {'id': 'minecraft:air', 'count': 0}}
        self.assertTrue(recipe_pair(self.route)[1]['json']['empty_carrier'])
    def test_own_stockpot_carrier_prep_respects_nine_groups(self):
        self.route.update(adapter_reviewed=True, adapter_decision='protected_stockpot_heat_finish', source_type='farmersdelight:cooking')
        self.route['original_resource']['container'] = {'item': 'minecraft:bowl'}
        self.route['replacement'].update(surface='stockpot', input_groups=[[{'kind': 'item', 'id': 'example:i'+str(i), 'count': 1}] for i in range(9)])
        with self.assertRaisesRegex(ValueError, 'nine_counted_input_groups'): recipe_pair(self.route)
    def test_stockpot_never_clamps_literal_output(self):
        self.route['replacement'].update(surface='stockpot', output={'id': 'example:salad', 'count': 10})
        with self.assertRaisesRegex(ValueError, 'capacity_9'): recipe_pair(self.route)
    def test_steamer_never_mints_original_external_bowl(self):
        self.route['replacement']['surface'] = 'steamer'
        self.route['original_resource']['container'] = {'item': 'minecraft:bowl'}
        with self.assertRaisesRegex(ValueError, 'source_payment_semantics_require_adapter'): recipe_pair(self.route)
    def test_fd_absent_container_field_is_not_proof_of_no_cost(self):
        self.route['source_type'] = 'farmersdelight:cooking'
        self.route['replacement']['surface'] = 'steamer'
        with self.assertRaisesRegex(ValueError, 'implicit_output_container'): recipe_pair(self.route)
    def test_fd_native_zero_container_is_distinct_from_missing(self):
        self.route['source_type'] = 'farmersdelight:cooking'
        self.route['replacement']['surface'] = 'steamer'
        self.route['native_semantics'] = {'container': {'id': 'minecraft:air', 'count': 0}}
        self.assertNotIn('source_external_container', recipe_pair(self.route)[0]['json'])
    def test_fd_native_implicit_container_can_be_prepaid(self):
        self.route['source_type'] = 'farmersdelight:cooking'
        self.route['replacement']['surface'] = 'steamer'
        self.route['native_semantics'] = {'container': {'id': 'minecraft:glass_bottle', 'count': 1}}
        prep = recipe_pair(self.route)[0]['json']
        self.assertIn({'ingredient': {'item': 'minecraft:glass_bottle'}, 'count': 2}, prep['ingredients'])
        self.assertEqual(prep['source_external_container']['total_carrier_count'], 2)
    def test_fd_steamer_prepays_exact_external_carriers_additively(self):
        self.route['replacement']['surface'] = 'steamer'
        self.route['source_type'] = 'farmersdelight:cooking'
        self.route['original_resource'].update(type='farmersdelight:cooking', container={'item': 'minecraft:bowl', 'count': 2})
        original = copy.deepcopy(self.route['replacement']['input_groups'])
        prep, finish = recipe_pair(self.route)
        bowls = [g for g in prep['json']['ingredients'] if g['ingredient'] == {'item': 'minecraft:bowl'}]
        # Original food groups already include one bowl; two outputs each need
        # TWO external bowls, so all five are paid, never just four or one.
        self.assertEqual(sum(g['count'] for g in bowls), 5)
        self.assertEqual(prep['json']['source_external_container']['total_carrier_count'], 4)
        self.assertEqual(self.route['replacement']['input_groups'], original)
        self.assertNotIn('carrier', finish['json'])
        self.assertEqual(finish['json']['result']['count'], 2)
    def test_fd_prepaid_container_respects_nine_groups(self):
        self.route['replacement'].update(surface='steamer', input_groups=[[{'kind': 'item', 'id': 'example:input_' + str(n), 'count': 1}] for n in range(9)])
        self.route['source_type'] = 'farmersdelight:cooking'
        self.route['original_resource']['container'] = {'item': 'minecraft:bowl'}
        with self.assertRaisesRegex(ValueError, 'nine_counted_input_groups'): recipe_pair(self.route)
    def test_native_carrier_mismatch_cannot_be_prepaid_from_stale_resource(self):
        self.route['replacement']['surface'] = 'steamer'; self.route['source_type'] = 'farmersdelight:cooking'
        self.route['original_resource']['container'] = {'item': 'minecraft:bowl'}
        self.route['native_semantics'] = {'container': {'id': 'minecraft:air', 'count': 0}}
        with self.assertRaisesRegex(ValueError, 'native_external_container_mismatch'): recipe_pair(self.route)
    def test_pot_preserves_original_external_bowl(self):
        self.route['replacement']['surface'] = 'pot'
        self.route['original_resource']['container'] = {'item': 'minecraft:bowl'}
        self.assertEqual(recipe_pair(self.route)[1]['json']['carrier'], {'item': 'minecraft:bowl'})
    def test_counted_container_cannot_become_single_ingredient(self):
        self.route['replacement']['surface'] = 'pot'
        self.route['original_resource']['container'] = {'item': 'minecraft:bowl', 'count': 2}
        with self.assertRaisesRegex(ValueError, 'counted_output_container'): recipe_pair(self.route)
    def test_role_review_does_not_approve_numeric_candidate_water(self):
        policy = compile_policy(fixture())
        food = policy['foods']['farmersdelight:cabbage']
        self.assertIsNotNone(food['moisture']['hydration'])
        self.assertIn('candidate_moisture_requires_explicit_review', food['unresolved'])
    def test_plan_exact_ids(self): self.assertEqual(build_plan(compile_policy(fixture()))['remove_ids'], ['example:salad'])
    def test_plan_reports_unresolved(self): self.assertIn('example:salad', build_plan(compile_policy(fixture()))['unresolved_source_ids'])


class FrozenContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.frozen = json.loads((TOOLS / 'frozen/catalogue_inputs.json').read_text())
        cls.policy = compile_policy(cls.frozen)
    def test_every_frozen_food_accounted(self): self.assertTrue(set(self.frozen['foods']) <= set(self.policy['foods']))
    def test_every_frozen_route_accounted(self): self.assertEqual(set(self.frozen['recipes']), set(self.policy['routes']))
    def test_no_native_acceptance_claim(self): self.assertEqual(self.policy['qualification'], 'unqualified'); self.assertTrue(self.policy['gaps'])
    def test_serving_counts_not_forced_eight(self): self.assertEqual(self.policy['servings']['kaleidoscope_cookery:golden_salad']['portions'], 6)
    def test_canonical_fd_crops(self): self.assertEqual(self.policy['aliases']['kaleidoscope_cookery:tomato'], 'farmersdelight:tomato')
    def test_canonical_kc_flour(self): self.assertEqual(self.policy['aliases']['create:wheat_flour'], 'kaleidoscope_cookery:flour')
    def test_all_registered_legacy_retained(self): self.assertEqual(self.policy['exceptions']['registered_legacy_items'], 'retain_ids_and_finite_found_stock')
    def test_all_replacements_original_ratios(self): validate(self.policy, self.frozen)
    def test_report_unresolved_counts_match_exact_id_lists(self):
        r = report(self.policy)
        self.assertEqual(r['unresolved_food_count'], len(r['unresolved_food_ids']))
        self.assertEqual(r['unresolved_route_count'], len(r['unresolved_route_ids']))
    def test_full_review_tables_deterministic_and_complete(self):
        foods, routes = review_tables(self.policy)
        self.assertEqual((foods, routes), review_tables(self.policy))
        self.assertEqual(len(foods.splitlines()), len(self.policy['foods']) + 1)
        self.assertEqual(len(routes.splitlines()), len(self.policy['routes']) + 1)
    def test_all_candidate_routes_need_review(self):
        self.assertTrue(all('candidate_route_requires_native_review' in r['unresolved'] for r in self.policy['routes'].values() if r['disposition'] == 'replace'))
    def test_all_candidates_never_promotable_by_absent_gaps(self): self.assertTrue(all(f['unresolved'] for f in self.policy['foods'].values()))


if __name__ == '__main__': unittest.main()
