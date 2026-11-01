import copy
import gzip
import json
from pathlib import Path
import struct
import sys
import unittest

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from test_policy import fixture
from authored_decisions import expand_decisions, verify_source_inputs
from compile_policy import _compile_policy_model as compile_policy, active_ticks, work_stages
from build_acquisition_data import rewrite_loot, ALIASES
from extract_catalogue import resource_recipe
from recipe_migration import canonical_bulk_definition
from structure_nbt import Tag, encode, decode, get, canonical_finite_forage


def reviewed_fixture():
    frozen = fixture(); frozen['manifest']['review-source'] = 'bound-sha256'
    document = {'schema': 'better_cooking:authored_decisions.v1', 'source_inputs': {'review-source': 'bound-sha256'},
        'families': [{'id': 'explicit-example', 'items': ['example:salad'],
                      'decision': {'role': 'ingredient', 'work_class': 'none', 'adapter': 'retained_ingredient', 'reviewed': ['role', 'work_class']},
                      'reason': 'Exact source proves culinary ingredient rather than completed meal.', 'evidence': ['review-source#salad']}],
        'foods': {}, 'routes': {}}
    return frozen, document


class AuthoredReviewTests(unittest.TestCase):
    def test_review_applies_before_route_derivation(self):
        f, d = reviewed_fixture(); p = compile_policy(f, authored=d)
        self.assertEqual(p['foods']['example:salad']['role'], 'ingredient')
        self.assertEqual(p['foods']['example:salad']['nutrition']['hunger'], 4)
        self.assertEqual(p['routes']['example:salad']['disposition'], 'retain_bulk')
        self.assertNotIn('candidate_classification_requires_review', p['foods']['example:salad']['unresolved'])
        self.assertEqual(p['qualification'], 'unqualified')

    def test_role_review_does_not_approve_name_based_water(self):
        f, d = reviewed_fixture(); d['families'][0]['items'] = ['farmersdelight:cabbage']
        food = compile_policy(f, authored=d)['foods']['farmersdelight:cabbage']
        self.assertNotIn('candidate_classification_requires_review', food['unresolved'])
        self.assertIsNotNone(food['moisture']['hydration'])
        self.assertIn('candidate_moisture_requires_explicit_review', food['unresolved'])

    def test_review_does_not_invent_native_effects(self):
        f, d = reviewed_fixture(); food = compile_policy(f, authored=d)['foods']['example:salad']
        self.assertIn('native_effects_not_enumerated', food['unresolved'])
        self.assertEqual(food['effects'], [])

    def test_held_ingredient_work_is_not_approved_zero_work(self):
        f, d = reviewed_fixture(); dec = d['families'][0]['decision']
        dec.update(work_class='unresolved', reviewed=['role'], unresolved=['renewable_utility_work_contract_held'])
        food = compile_policy(f, authored=d)['foods']['example:salad']
        self.assertEqual(food['classification'], 'partially_authored_review_pending_native')
        self.assertEqual(food['authored_review']['work_class'], 'unresolved')
        self.assertIn('authored_work_class_requires_explicit_decision', food['unresolved'])
        self.assertIn('renewable_utility_work_contract_held', food['unresolved'])
        self.assertIn('candidate_classification_requires_review', food['unresolved'])
    def test_unresolved_work_cannot_be_marked_reviewed(self):
        f, d = reviewed_fixture(); d['families'][0]['decision']['work_class'] = 'unresolved'
        with self.assertRaisesRegex(ValueError, 'cannot approve unresolved work'): compile_policy(f, authored=d)
    def test_source_review_cannot_drop_food_specific_native_holds(self):
        f, d = reviewed_fixture(); d['families'][0]['decision']['unresolved'] = ['actual_mob_feeding_callback_must_survive']
        self.assertIn('actual_mob_feeding_callback_must_survive', compile_policy(f, authored=d)['foods']['example:salad']['unresolved'])
    def test_authored_moisture_does_not_close_composite_provenance(self):
        f, d = reviewed_fixture(); dec = d['families'][0]['decision']
        dec.update(role='meal', work_class='substantial', moisture={'hydration': 2, 'quenching': 2.5, 'class': 'explicit_salad_default'})
        food = compile_policy(f, authored=d)['foods']['example:salad']
        self.assertEqual(food['moisture'], {'hydration': 2, 'quenching': 2.5})
        self.assertIn('composite_ingredient_moisture_and_nutrients_require_resolved_yield', food['unresolved'])

    def test_source_binding_mismatch_rejects_review(self):
        f, d = reviewed_fixture(); f['manifest']['review-source'] = 'different'
        with self.assertRaisesRegex(ValueError, 'not bound'): compile_policy(f, authored=d)

    def test_missing_source_binding_rejects_review(self):
        f, d = reviewed_fixture(); d['source_inputs'] = {}
        with self.assertRaisesRegex(ValueError, 'source hash bindings'): compile_policy(f, authored=d)

    def test_no_approving_missing_heuristic_field(self):
        f, d = reviewed_fixture(); del d['families'][0]['decision']['role']
        with self.assertRaisesRegex(ValueError, 'missing field'): expand_decisions(d)

    def test_duplicate_family_approval_rejected(self):
        f, d = reviewed_fixture(); d['families'].append(copy.deepcopy(d['families'][0]))
        with self.assertRaisesRegex(ValueError, 'duplicate'): expand_decisions(d)

    def test_per_id_override_keeps_source_reason(self):
        f, d = reviewed_fixture(); d['foods']['example:salad'] = {'reason': 'Specific exact-ID exception.', 'evidence': ['review-source#exception']}
        review = compile_policy(f, authored=d)['foods']['example:salad']['authored_review']
        self.assertEqual(review['reason'], 'Specific exact-ID exception.')
        self.assertEqual(review['evidence'], ['review-source#exception'])

    def test_native_negative_intake_cannot_be_rebalanced(self):
        f, d = reviewed_fixture(); f['foods']['example:salad']['food']['nutrition'] = -2
        with self.assertRaisesRegex(ValueError, 'harmful native'): compile_policy(f, authored=d)

    def test_absent_current_review_is_gap_not_fabricated_food(self):
        f, d = reviewed_fixture(); d['families'][0]['items'] = ['example:no_longer_present']
        p = compile_policy(f, authored=d)
        self.assertNotIn('example:no_longer_present', p['foods'])
        self.assertTrue(any(g['code'] == 'authored_food_not_currently_declared' for g in p['gaps']))

    def test_raw_chain_is_deferred_not_baked_into_raw_output(self):
        f, d = reviewed_fixture(); d['routes']['example:salad'] = {'source_type': 'minecraft:crafting_shapeless',
            'disposition': 'deferred', 'adapter': 'raw_then_native_bake', 'reason': 'Exact raw recipe link.', 'evidence': ['review-source#raw'],
            'unresolved': ['transactional_baking_chain_required']}
        p = compile_policy(f, authored=d); route = p['routes']['example:salad']
        self.assertEqual(route['disposition'], 'deferred'); self.assertIsNone(route['replacement'])
        self.assertIn('transactional_baking_chain_required', route['unresolved'])
        self.assertNotIn('example:salad', p['migration']['remove_ids'])

    def test_authored_source_serializer_drift_is_error(self):
        f, d = reviewed_fixture(); d['routes']['example:salad'] = {'source_type': 'different:serializer', 'reason': 'Reviewed exact serializer.', 'evidence': ['review-source']}
        with self.assertRaisesRegex(ValueError, 'serializer changed'): compile_policy(f, authored=d)

    def test_native_stage_bounds_preserve_all_work(self):
        for work in ('straightforward', 'substantial', 'elaborate'):
            for portions in (1, 6, 8, 16, 64, 256):
                total = active_ticks(work, portions); stages = work_stages('knead', total, 'better_cooking:preparation_tools')
                self.assertEqual(sum(s['duration_ticks'] * s['repetitions'] for s in stages), total)
                self.assertTrue(all(1 <= s['duration_ticks'] <= 1200 and 1 <= s['repetitions'] <= 64 for s in stages))

    def test_ube_array_output_counts_and_chances_retained(self):
        raw = {'type': 'ubesdelight:baking_mat', 'ingredients': [{'item': 'minecraft:wheat'}],
               'result': [{'item': 'farmersdelight:wheat_dough', 'count': 3}, {'item': 'farmersdelight:wheat_dough', 'chance': .25}]}
        row = resource_recipe('test:dough', raw, 'test-source')
        self.assertEqual([o.get('count', 1) for o in row['outputs']], [3, 1])
        self.assertEqual(row['outputs'][1]['chance'], .25)
        route = {'outputs': row['outputs'], 'input_groups': row['input_groups'], 'original_resource': raw, 'disposition': 'retain_bulk'}
        rewritten = canonical_bulk_definition('test:dough', route)['json']
        self.assertEqual(rewritten['result'], [{'item': 'kaleidoscope_cookery:raw_dough', 'count': 3}, {'item': 'kaleidoscope_cookery:raw_dough', 'chance': .25}])

    def test_noncrafting_pattern_is_not_fake_shaped_recipe(self):
        row = resource_recipe('test:cast', {'type': 'tconstruct:casting_table', 'pattern': {'tag': 'test:casts'}, 'result': 'test:item'}, 'source')
        self.assertIn('custom pattern semantics require native adapter', row['issues'])


class NativeCuttingTests(unittest.TestCase):
    def route(self):
        return {'authored_review': {'serializer_contract_status': 'root_approved_pending_native', 'native_cut_count': 1, 'cut_function': 'paid_prepared_product_portion_cut'},
            'original_resource': {'type': 'farmersdelight:cutting', 'ingredients': [[{'item': 'test:old'}, {'tag': 'test:equivalent'}]],
                'tool': [{'type': 'farmersdelight:tool_action', 'action': 'knife_dig'}, {'tag': 'forge:tools/knives'}],
                'result': [{'item': 'farmersdelight:tomato_seeds'}, {'item': 'farmersdelight:tomato', 'chance': .2}, {'item': 'test:dyestuff', 'count': 2, 'chance': .1}]}}

    def test_all_ordered_outputs_and_forge_tool_preserved(self):
        from recipe_migration import native_cutting_definition
        route = self.route(); recipe = native_cutting_definition('test:original', route)
        self.assertEqual(recipe['id'], 'test:original')
        self.assertEqual(recipe['json']['type'], 'better_cooking:cutting')
        self.assertEqual(recipe['json']['results'], route['original_resource']['result'])
        self.assertEqual(recipe['json']['tool'], route['original_resource']['tool'])
        self.assertEqual(recipe['json']['ingredient'], route['original_resource']['ingredients'][0])
        self.assertEqual(recipe['json']['cut_count'], 1)
        self.assertNotIn('stages', recipe['json']); self.assertNotIn('model_id', recipe['json'])

    def test_new_result_metadata_not_silently_dropped(self):
        from recipe_migration import native_cutting_definition
        route = self.route(); route['original_resource']['result'][0]['fortune_bonus'] = 2
        with self.assertRaisesRegex(ValueError, 'unreviewed_result_metadata'): native_cutting_definition('test:original', route)

    def test_conditions_not_stripped_or_assumed_processed_by_kubejs(self):
        from recipe_migration import native_cutting_definition
        route = self.route(); route['original_resource']['conditions'] = [{'type': 'forge:mod_loaded', 'modid': 'optional_mod'}]
        with self.assertRaisesRegex(ValueError, 'conditions_require_proven_emission_path'): native_cutting_definition('test:original', route)

    def test_no_implicit_contract_approval(self):
        from recipe_migration import native_cutting_definition
        route = self.route(); route['authored_review']['serializer_contract_status'] = 'pending'
        with self.assertRaisesRegex(ValueError, 'contract_pending'): native_cutting_definition('test:original', route)

    def test_multi_ingredient_cold_assembly_cannot_be_a_free_cut(self):
        from recipe_migration import native_cutting_definition
        route = self.route(); route['original_resource']['ingredients'].append({'item': 'test:second'})
        with self.assertRaisesRegex(ValueError, 'one_native_input_unit'): native_cutting_definition('test:original', route)

    def test_nbt_and_native_default_one_preserved(self):
        from recipe_migration import native_cutting_definition
        route = self.route(); route['original_resource']['result'][1]['nbt'] = '{CustomModelData:3}'
        result = native_cutting_definition('test:original', route)['json']['results']
        self.assertNotIn('count', result[0]); self.assertNotIn('chance', result[0])
        self.assertEqual(result[1]['nbt'], '{CustomModelData:3}')

    def test_native_cut_no_minimum_meal_work_or_removal(self):
        f, d = reviewed_fixture()
        raw = {'type': 'farmersdelight:cutting', 'ingredients': [{'item': 'minecraft:apple'}],
               'tool': {'tag': 'forge:tools/knives'}, 'result': [{'item': 'example:salad', 'count': 2}]}
        f['recipes']['example:salad'] = resource_recipe('example:salad', raw, 'source')
        d['routes']['example:salad'] = {'source_type': 'farmersdelight:cutting', 'disposition': 'retain_bulk',
            'adapter': 'native_bulk_cut', 'work_class': 'none', 'native_cut_count': 1,
            'serializer_contract_status': 'root_approved_pending_native', 'reason': 'Native ingredient cut, not meal work.', 'evidence': ['review-source#cut']}
        policy = compile_policy(f, authored=d); route = policy['routes']['example:salad']
        self.assertEqual(route['stages'], []); self.assertEqual(route['work_class'], 'none')
        self.assertEqual(policy['migration']['native_cut_rewrite_ids'], ['example:salad'])
        self.assertNotIn('example:salad', policy['migration']['remove_ids'])


class AdapterAndMoistureTests(unittest.TestCase):
    def meal(self, adapter, moisture=None):
        f, d = reviewed_fixture(); decision = d['families'][0]['decision']
        decision.update(role='meal', work_class='substantial', adapter=adapter)
        if moisture:
            decision['moisture'] = moisture; decision['reviewed'].append('moisture')
        return f, d

    def test_pressed_sweet_mat_finishes_without_oven(self):
        from recipe_migration import recipe_pair
        f, d = self.meal('protected_mat_press_pretoasted_component_no_fictitious_rebake')
        p = compile_policy(f, authored=d); pair = recipe_pair(p['routes']['example:salad'])
        self.assertEqual(len(pair), 1)
        self.assertEqual(pair[0]['json']['surface'], 'baking_mat')
        self.assertEqual(pair[0]['json']['finish'], 'board')

    def test_custard_mat_then_actual_steamer(self):
        from recipe_migration import recipe_pair
        f, d = self.meal('protected_mat_mix_then_steamer')
        pair = recipe_pair(compile_policy(f, authored=d)['routes']['example:salad'])
        self.assertEqual(pair[0]['json']['surface'], 'baking_mat')
        self.assertEqual(pair[0]['json']['finish'], 'steamer')
        self.assertEqual(pair[1]['json']['type'], 'kaleidoscope_cookery:steamer')

    def test_explicit_slice_reassembly_does_not_rebake(self):
        from recipe_migration import recipe_pair
        f, d = self.meal('protected_mat_raw_then_bake')
        d['routes']['example:salad'] = {'source_type': 'minecraft:crafting_shapeless', 'surface': 'board',
            'adapter': 'native_served_plate_assembly_inherit', 'reason': 'Exact already baked components.', 'evidence': ['review-source#baked']}
        pair = recipe_pair(compile_policy(f, authored=d)['routes']['example:salad'])
        self.assertEqual(len(pair), 1); self.assertEqual(pair[0]['json']['finish'], 'board')

    def test_unreviewed_candidate_water_is_not_direct_override(self):
        food = compile_policy(fixture())['foods']['example:salad']
        self.assertIsNone(food['composite_moisture']['direct_override'])
        self.assertIsNone(food['composite_moisture']['process_retention'])
        self.assertIsNone(food['composite_moisture']['added_water_mb'])

    def test_explicit_water_override_replaces_not_stacks(self):
        f, d = self.meal('protected_cold_board_assembly', {'hydration': 2, 'quenching': 2.5, 'class': 'authored_salad'})
        food = compile_policy(f, authored=d)['foods']['example:salad']; water = food['composite_moisture']
        self.assertEqual(water['mode'], 'direct_override')
        self.assertEqual(water['direct_override']['hydration'], 2)
        self.assertEqual(water['no_double_credit'], 'direct_override_replaces_ingredient_water_never_adds_to_it')
        self.assertFalse(water['quality_scales_water'])
        self.assertIn('composite_ingredient_moisture_and_nutrients_require_resolved_yield', food['unresolved'])

    def test_multioutput_never_emits_lossy_primary_only_recipe(self):
        f, d = self.meal('protected_cold_board_assembly')
        f['recipes']['example:salad']['outputs'].append({'kind': 'item', 'id': 'minecraft:apple', 'count': 1, 'chance': .5})
        p = compile_policy(f, authored=d)
        self.assertIn({'source_id': 'example:salad', 'error': 'multiple_outputs_need_transactional_adapter'}, p['migration']['errors'])
        self.assertNotIn('example:salad', p['migration']['remove_ids'])
        self.assertFalse(any(r['id'].startswith('better_cooking:migration/example/salad') for r in p['migration']['recipes']))

    def test_single_probabilistic_result_not_upgraded_to_guaranteed(self):
        f, d = self.meal('protected_cold_board_assembly'); f['recipes']['example:salad']['outputs'][0]['chance'] = .25
        p = compile_policy(f, authored=d)
        self.assertIn({'source_id': 'example:salad', 'error': 'probabilistic_output_requires_native_adapter'}, p['migration']['errors'])
        self.assertNotIn('example:salad', p['migration']['remove_ids'])


class AcquisitionResourceTests(unittest.TestCase):
    def test_only_item_entry_names_rewrite(self):
        source = {'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'kaleidoscope_cookery:tomato_seed', 'weight': 10,
            'conditions': [{'condition': 'minecraft:random_chance', 'chance': .125}],
            'functions': [{'function': 'minecraft:set_count', 'count': {'min': 1, 'max': 3}}]}]}],
            'block': 'kaleidoscope_cookery:tomato_crop', 'unrelated': 'kaleidoscope_cookery:tomato_seed'}
        rewritten, changes = rewrite_loot(source)
        expected = copy.deepcopy(source); expected['pools'][0]['entries'][0]['name'] = 'farmersdelight:tomato_seeds'
        self.assertEqual(rewritten, expected); self.assertEqual(len(changes), 1)
        self.assertEqual(source['pools'][0]['entries'][0]['name'], 'kaleidoscope_cookery:tomato_seed')

    def test_transitive_acquisition_identities_are_not_ingredient_equivalences(self):
        from policy_rules import ALIASES as INGREDIENT_ALIASES
        self.assertEqual(len(ALIASES), 7)
        self.assertNotIn('kaleidoscope_cookery:wild_rice', INGREDIENT_ALIASES)
        self.assertNotIn('kaleidoscope_cookery:rice_panicle', INGREDIENT_ALIASES)

    def test_structure_only_exact_tomato_palette_changes(self):
        target = Tag(10, [(b'Name', Tag(8, b'kaleidoscope_cookery:tomato_crop')), (b'Properties', Tag(10, [(b'age', Tag(8, b'7'))]))])
        distinct = Tag(10, [(b'Name', Tag(8, b'kaleidoscope_cookery:chili_crop')), (b'Properties', Tag(10, [(b'age', Tag(8, b'7'))]))])
        root = Tag(10, [(b'palette', Tag(9, (10, [target, distinct]))), (b'entities', Tag(9, (10, []))),
                        (b'DataVersion', Tag(3, struct.pack('>i', 3337))), (b'exact_nan_bits', Tag(5, b'\x7f\xc0\x00\x01'))])
        raw = encode(b'', root); converted, changes = canonical_finite_forage(raw)
        _, actual = decode(converted); expected = copy.deepcopy(root)
        first = get(expected, 'palette').value[1][0]; get(first, 'Name').value = b'farmersdelight:wild_tomatoes'
        first.value = [(k, v) for k, v in first.value if k != b'Properties']
        self.assertEqual(actual, expected); self.assertEqual(changes[0]['removed_incompatible_properties'], {'age': '7'})
        self.assertEqual(converted, canonical_finite_forage(raw)[0])

    def test_structure_no_target_is_exact_noop_when_explicit(self):
        root = Tag(10, [(b'palette', Tag(9, (10, [Tag(10, [(b'Name', Tag(8, b'minecraft:stone'))])])) )])
        raw = encode(b'', root)
        self.assertEqual(canonical_finite_forage(raw, allow_no_changes=True), (raw, []))
        with self.assertRaisesRegex(ValueError, 'palette entry missing'): canonical_finite_forage(raw)

    def test_new_unreviewed_state_property_is_error(self):
        root = Tag(10, [(b'palette', Tag(9, (10, [Tag(10, [(b'Name', Tag(8, b'kaleidoscope_cookery:tomato_crop')),
              (b'Properties', Tag(10, [(b'new_state', Tag(8, b'yes'))]))])])) )])
        with self.assertRaisesRegex(ValueError, 'state property'): canonical_finite_forage(encode(b'', root))

    def test_truncated_nbt_is_error_not_silent_empty_palette(self):
        with self.assertRaises(ValueError): decode(b'\x0a\x00\x00\x08\x00')


if __name__ == '__main__': unittest.main()
