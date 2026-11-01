"""Synthetic UNTRUSTED structural/partition controls; NEVER mint Root capability."""
import copy
import json
from decimal import Decimal
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from native_import_v2 import validate_snapshot, derive_partition, _effects, NBT_BASIS, canonical_uuid, ROOT_KEYS, RECIPE_REQUIRED, RECIPE_OPTIONAL
from native_import import import_native


def node(ref='t0', item='example:new_apple', state='registered'):
    apis = ('builtin', 'forge') if ref.startswith('t') else ('forge',)
    key = 'example:actual_type' if state == 'registered' else None
    return {'ref': ref, 'class': 'synthetic.SameClass', 'first_recipe_id': item, 'state': state,
            'registered_key': key,
            'lookups': {api: {'outcome': 'key' if key else 'absent', 'key': key, 'error': None} for api in apis},
            'active_binding': {api: 'same_object' if key else 'not_sampled' for api in apis}}


def observation():
    return {'schema': 'better_cooking:native_catalogue.v2', 'status': 'observed_unqualified',
            'created_utc': '2026-10-29T00:00:00Z', 'qualification': False, 'discovery_only': True,
            'food_tags_orientation': 'tag_to_items', 'context': {'player': '01234567-89ab-4cde-8fab-0123456789ab'},
            'resource_inputs': {'identity_binding': 'external_root_receipt_required'}, 'recipe_stack_nbt_basis': NBT_BASIS, 'importable': True,
            'recipe_identity': {'contract': 'better_cooking:recipe_identity_observation.v2', 'scope': 'one_collect_invocation_only',
                                'invocation_nonce': '12345678-1234-4234-8234-123456789abc', 'type_objects': [node()], 'serializer_objects': [node('s0')]},
            'registered_items': ['example:new_apple', 'minecraft:apple'], 'block_items': {},
            'item_tags': {'forge:fruits/apple': ['example:new_apple']}, 'food_tags': {'forge:fruits/apple': ['example:new_apple']},
            'mods': [{'id': 'minecraft', 'version': '1.20.1'}],
            'foods': [{'id': 'example:new_apple', 'hunger': 5, 'saturation_modifier': Decimal('.4'),
                       'meat': False, 'always_eat': False, 'fast': False,
                       'effects': [{'id': 'minecraft:poison', 'duration': 100, 'amplifier': 0, 'chance': Decimal('.2')}],
                       'nutrients': {'fruits': Decimal('.5')}, 'sugar': Decimal('.1'), 'alcohol': 0, 'spice': 0}],
            'recipes': [{'id': 'example:new_apple', 'recipe_class': 'synthetic.Recipe',
                         'native_recipe_type': {'getter': 'returned_object', 'state': 'registered', 'ref': 't0'},
                         'native_serializer': {'getter': 'returned_object', 'state': 'registered', 'ref': 's0'},
                         'ingredients': [{'item': 'minecraft:apple', 'count': 4}],
                         'result': {'id': 'example:new_apple', 'count': 3, 'nbt': '{Known:1b}', 'chance': Decimal('.25')}}],
            'gaps': [], 'native_consumables': [], 'native_drink_ingredients': [],
            'native_consumables_context': {'status': 'discovery_getters_only', 'qualification': False, 'observed_finish': False},
            'native_drink_ingredients_context': {'schema': 'better_cooking:native_drink_ingredients.v2', 'status': 'observed_getters_unbound_runtime', 'qualification': False, 'auto_install': False},
            'native_fd_carriers': {'status': 'unobserved', 'rows': None}, 'native_fd_feasts': {'status': 'unobserved', 'rows': None}}


def provenance():
    # Labelled SYNTHETIC partition inputs, NOT production Root provenance.
    return {'raw_sha256': 'a' * 64, 'raw_bytes_count': 1234, 'binding_sha256': 'b' * 64,
            'contract_sha256': 'c' * 64, 'invocation_nonce': observation()['recipe_identity']['invocation_nonce']}


def baseline():
    return {'recipes': {'example:historical': {'outputs': [{'id': 'minecraft:apple', 'count': 1}]}}}


class NativeV2StructuralTests(unittest.TestCase):
    def setUp(self): self.s = observation()
    def test_schema_outer_contract_and_local_refs_are_closed(self):
        tools = Path(__file__).resolve().parents[1]
        schema = json.loads((tools / 'native_catalogue_v2.schema.json').read_bytes())
        self.assertEqual(set(schema['required']), ROOT_KEYS)
        self.assertEqual(set(schema['properties']), ROOT_KEYS)
        recipe = schema['$defs']['recipe']
        self.assertEqual(set(recipe['required']), RECIPE_REQUIRED)
        self.assertEqual(set(recipe['properties']), RECIPE_REQUIRED | RECIPE_OPTIONAL)
        for document in (schema, json.loads((tools / 'policy.schema.json').read_bytes())):
            def walk(value):
                if isinstance(value, dict):
                    if '$ref' in value:
                        self.assertTrue(value['$ref'].startswith('#/$defs/'))
                        self.assertIn(value['$ref'].split('/')[-1], document['$defs'])
                    for child in value.values(): walk(child)
                elif isinstance(value, list):
                    for child in value: walk(child)
            walk(document)

    def test_structural_validation_is_not_binding(self):
        self.assertIs(validate_snapshot(self.s), self.s)
        with self.assertRaisesRegex(ValueError, 'binding'): import_native({}, self.s)
        with self.assertRaises(ValueError): import_native({}, self.s, binding={'validated': True})

    def test_food_getter_threshold_100_is_observation_only_and_not_drink_probability(self):
        food = self.s['foods'][0]
        food['id'] = 'hexerei:belladonna_berries'
        effect = food['effects'][0]
        effect.update(duration=100, amplifier=2, chance=Decimal('100.0'))
        original = copy.deepcopy(self.s)
        self.assertIs(validate_snapshot(self.s), self.s)
        self.assertEqual(original, self.s)
        self.assertEqual([], derive_partition(self.s, provenance(), baseline())['admitted_recipe_ids'])
        with self.assertRaisesRegex(ValueError, 'probability'):
            _effects([dict(effect, probability=Decimal('100.0'))], 'probability')
        for field, bad in [('id', 'minecraft:speed'), ('duration', 101), ('amplifier', 1),
                           ('chance', Decimal('1.5')), ('chance', 100), ('chance', True),
                           ('chance', Decimal('Infinity')), ('chance', Decimal('1000'))]:
            modified = copy.deepcopy(original)
            modified['foods'][0]['effects'][0][field] = bad
            with self.subTest(field=field, bad=bad), self.assertRaises(ValueError): validate_snapshot(modified)
        modified = copy.deepcopy(original)
        modified['foods'][0]['id'] = 'hexerei:other_berries'
        with self.assertRaisesRegex(ValueError, 'unreviewed'): validate_snapshot(modified)
        for chance in (Decimal('0'), Decimal('.25'), Decimal('1')):
            modified = copy.deepcopy(original)
            modified['foods'][0]['effects'][0]['chance'] = chance
            validate_snapshot(modified)

    def test_all_rows_quarantined_even_registered_bound_no_gaps(self):
        p = derive_partition(self.s, provenance(), baseline())
        self.assertEqual(p['admitted_recipe_ids'], [])
        self.assertEqual(p['quarantined_recipe_ids'], ['example:new_apple'])
        self.assertEqual(p['ledger'][0]['reasons'], ['backend_contract_not_approved'])
        self.assertEqual(p['source_only_unobserved_recipe_ids'], ['example:historical'])
        self.assertFalse(p['qualification'])

    def test_known_absence_not_getter_null(self):
        self.s['recipe_identity']['type_objects'][0] = node(state='actually_unregistered')
        self.s['recipes'][0]['native_recipe_type']['state'] = 'actually_unregistered'
        p = derive_partition(self.s, provenance(), baseline())
        self.assertIn('recipe_type_owner_keys_absent', p['ledger'][0]['reasons'])
        self.assertIn('recipe_type_active_binding_not_sampled', p['ledger'][0]['reasons'])
        self.assertNotIn('recipe_type_missing', p['ledger'][0]['reasons'])
        self.assertIsNone(self.s['recipe_identity']['type_objects'][0]['registered_key'])

    def test_getter_null_no_node_and_missing_only(self):
        self.s['recipe_identity']['type_objects'] = []
        self.s['recipes'][0]['native_recipe_type'] = {'getter': 'returned_null', 'state': 'missing', 'ref': None}
        reasons = derive_partition(self.s, provenance(), baseline())['ledger'][0]['reasons']
        self.assertEqual(reasons, ['backend_contract_not_approved', 'recipe_type_missing'])

    def test_registered_owner_active_mismatch_not_active_proof(self):
        self.s['recipe_identity']['type_objects'][0]['active_binding']['forge'] = 'mismatch'
        reasons = derive_partition(self.s, provenance(), baseline())['ledger'][0]['reasons']
        self.assertIn('recipe_type_active_binding_mismatch', reasons)
        self.assertEqual(self.s['recipe_identity']['type_objects'][0]['state'], 'registered')

    def test_conflicting_lookup_results_never_choose_preferred_key(self):
        for outcome in ('absent', 'key', 'error', 'not_sampled'):
            s = observation(); n = s['recipe_identity']['type_objects'][0]
            n['lookups']['forge'] = {'outcome': outcome, 'key': 'other:actual' if outcome == 'key' else None, 'error': 'actual exception' if outcome == 'error' else None}
            n['active_binding']['forge'] = 'mismatch' if outcome == 'key' else 'not_sampled'
            n.update(state='unknown', registered_key=None); s['recipes'][0]['native_recipe_type']['state'] = 'unknown'
            validate_snapshot(s)
            self.assertIn('recipe_type_unknown', derive_partition(s, provenance(), baseline())['ledger'][0]['reasons'])
            n['registered_key'] = 'example:actual_type'
            with self.assertRaises(ValueError): validate_snapshot(s)

    def test_same_class_distinct_objects_and_real_shared_ref_separate_cases(self):
        other = copy.deepcopy(self.s['recipes'][0]); other['id'] = 'example:z'
        self.s['recipes'].append(other)
        # Synthetic genuine shared reference, not pinned KC fixed/flex equality.
        validate_snapshot(self.s)
        other['native_recipe_type']['ref'] = 't1'
        self.s['recipe_identity']['type_objects'].append(node('t1', 'example:z'))
        validate_snapshot(self.s)
        self.assertEqual([r['type_ref'] for r in derive_partition(self.s, provenance(), baseline())['ledger']], ['t0', 't1'])

    def test_duplicate_holey_unused_dangling_wrong_table_first_refs(self):
        changes = [lambda s:s['recipes'].append(copy.deepcopy(s['recipes'][0])),
                   lambda s:s['recipe_identity']['type_objects'].append(node('t1', 'example:z')),
                   lambda s:s['recipe_identity']['type_objects'][0].update(ref='t1'),
                   lambda s:s['recipe_identity']['type_objects'][0].update(first_recipe_id='example:wrong'),
                   lambda s:s['recipes'][0]['native_recipe_type'].update(ref='s0'),
                   lambda s:s['recipes'][0]['native_recipe_type'].update(ref='t99')]
        for change in changes:
            s = observation(); change(s)
            with self.assertRaises(ValueError): validate_snapshot(s)

    def test_unions_keys_and_false_binding_reject(self):
        changes = [lambda s:s.update(extra=1), lambda s:s['recipes'][0].update(type='invented:type'),
                   lambda s:s['recipes'][0]['native_recipe_type'].update(state='missing'),
                   lambda s:s['recipe_identity']['type_objects'][0]['lookups']['builtin'].update(error='fake'),
                   lambda s:s['recipe_identity']['type_objects'][0]['lookups']['builtin'].update(outcome='absent', key=None),
                   lambda s:s['recipe_identity']['type_objects'][0]['active_binding'].update(forge='not_sampled'),
                   lambda s:s['recipe_identity'].update(scope='portable')]
        for change in changes:
            s = observation(); change(s)
            with self.assertRaises(ValueError): validate_snapshot(s)

    def test_lossless_multioutput_chances_air_and_opaque_resources(self):
        row = self.s['recipes'][0]
        row['results'] = [copy.deepcopy(row['result']), {'id': 'minecraft:air', 'count': 0, 'chance': Decimal('.0000000000000000000001')}]
        row['resource_json'] = {'type': 'unknown:serializer', 'source_cost': -9, 'nbt': {'mod_specific': [0, None]}}
        before = copy.deepcopy(self.s)
        derive_partition(self.s, provenance(), baseline())
        self.assertEqual(self.s, before)
        self.assertEqual(row['result']['nbt'], '{Known:1b}')
        self.assertEqual(row['result']['count'], 3)
        self.assertEqual(row['ingredients'][0]['count'], 4)

    def test_unknown_result_and_cost_not_defaulted(self):
        row = self.s['recipes'][0]; del row['result']
        row.update(cook_ticks=None, container=None, native_ingredient={'custom': {'whatever': True}})
        validate_snapshot(self.s)
        self.assertNotIn('result', row); self.assertIsNone(row['container'])
        self.assertEqual(derive_partition(self.s, provenance(), baseline())['recipe_count'], 1)

    def test_typed_count_numeric_and_nbt_negative_controls(self):
        for field, bad in [('count', True), ('count', 3.0), ('count', Decimal('3e0')), ('count', -1), ('count', 2147483648), ('chance', Decimal('1.01')), ('chance', False), ('nbt', {})]:
            s = observation(); s['recipes'][0]['result'][field] = bad
            with self.subTest(field=field, bad=bad), self.assertRaises(ValueError): validate_snapshot(s)
        for field, bad in [('cook_ticks', True), ('ingredient_count', Decimal('12.0')), ('native_output_count_constant', 0)]:
            s = observation(); s['recipes'][0][field] = bad
            with self.assertRaises(ValueError): validate_snapshot(s)

    def test_zero_negative_clock_experience_observed_not_defaulted(self):
        self.s['recipes'][0].update(cook_ticks=-1, experience=Decimal('-0.125'), ingredient_count=12, native_output_count_constant=3)
        validate_snapshot(self.s)
        self.assertEqual(self.s['recipes'][0]['ingredient_count'], 12)

    def test_no_v1_diagnostic_relabel_escape_or_eligibility(self):
        for schema in ('better_cooking:native_catalogue.v1', 'better_cooking:native_catalogue_recipe_identity_diagnostic.v1', 'other:v2'):
            s = observation(); s['schema'] = schema
            with self.assertRaises(ValueError): validate_snapshot(s)
        for field, bad in [('qualification', 0), ('discovery_only', 1), ('importable', 'true'), ('recipe_stack_nbt_basis', 'full_ForgeCaps')]:
            s = observation(); s[field] = bad
            with self.assertRaises(ValueError): validate_snapshot(s)

    def test_carrier_is_opaque_ingredient_not_a_stack_and_no_full_caps_inferred(self):
        row = self.s['recipes'][0]
        row['carrier'] = {'item': 'minecraft:bowl'}
        row['tool'] = {'tag': 'forge:tools/knives'}
        row['native_ingredient'] = {'custom_predicate': {'count': None}}
        validate_snapshot(self.s)
        self.assertNotIn('count', row['carrier'])
        self.assertEqual(self.s['recipe_stack_nbt_basis'], NBT_BASIS)

    def test_opaque_context_cannot_bypass_json_scalar_or_unicode_guard(self):
        for bad in (Decimal('Infinity'), float('inf'), '\ud800'):
            s = observation(); s['recipes'][0]['resource_json'] = {'opaque': bad}
            with self.assertRaises(ValueError): validate_snapshot(s)

    def test_nonrecipe_field_negatives_contexts_and_known_unknown_retention(self):
        changes = [lambda s:s['foods'][0].update(nutrients=[]),
                   lambda s:s['foods'][0].update(sugar=True),
                   lambda s:s['foods'][0]['effects'][0].update(duration=-1),
                   lambda s:s['foods'][0]['effects'][0].update(amplifier=True),
                   lambda s:s['native_consumables_context'].update(observed_finish=True),
                   lambda s:s['native_drink_ingredients_context'].update(auto_install=True),
                   lambda s:s.update(created_utc='not-a-time')]
        for change in changes:
            s = observation(); change(s)
            with self.assertRaises(ValueError): validate_snapshot(s)
        self.s['foods'][0].update(hunger=-4, nutrients={'signed_source_fact': Decimal('-0.2')})
        validate_snapshot(self.s)
        self.assertEqual(self.s['foods'][0]['nutrients']['signed_source_fact'], Decimal('-0.2'))
        self.assertEqual(derive_partition(self.s, provenance(), baseline())['admitted_recipe_ids'], [])

    def test_nonstandard_getters_source_agreement_effects_and_unknowns(self):
        source = {'nonstandard_consumables': {'consumables': {'example:cup': {
            'item_class': 'synthetic.Cup', 'is_edible': False, 'food_properties': None,
            'use_animation': 'drink', 'use_duration_ticks': 32, 'remainder': 'example:empty_cup',
            'source_selected_effects': [{'id': 'minecraft:speed', 'duration': 100, 'amplifier': 0, 'chance': .5}]}}}}
        row = {'id': 'example:cup', 'item_class': 'synthetic.Cup', 'is_edible': False, 'food_properties': None,
               'use_animation': 'drink', 'use_duration_ticks': 32, 'remainder': 'example:empty_cup',
               'sugar': None, 'nutrients': None, 'alcohol': 0, 'spice': None,
               'source_only': {'observed_finish': False, 'food_data_eat_call': False},
               'native_effect_declarations': [{'id': 'minecraft:speed', 'duration': 100, 'amplifier': 0, 'probability': Decimal('.5')}]}
        self.s['native_consumables'] = [row]; before = copy.deepcopy(self.s)
        validate_snapshot(self.s, source); self.assertEqual(self.s, before)
        self.assertIsNone(row['food_properties']); self.assertIsNone(row['nutrients'])
        changes = [lambda r:r.update(is_edible=True), lambda r:r.update(use_duration_ticks=31),
                   lambda r:r.update(sugar=True), lambda r:r.update(food_properties={'hunger': 1, 'saturation_modifier': 0}),
                   lambda r:r['native_effect_declarations'][0].update(probability=Decimal('1.1')),
                   lambda r:r['native_effect_declarations'][0].update(duration=101),
                   lambda r:r['source_only'].update(observed_finish=True)]
        for change in changes:
            s = copy.deepcopy(before); change(s['native_consumables'][0])
            with self.assertRaises(ValueError): validate_snapshot(s, source)

    def test_full_saved_stack_appenders_do_not_claim_serve_or_guessed_debit(self):
        section = {'schema_version': 1, 'status': 'observed_public_getters_only', 'recipe_count': 1,
                   'qualification': 'unqualified_getter_facts_only', 'rows': [
                       {'recipe_id': 'example:new_apple', 'observed_serve': False,
                        'result': {'empty': False, 'item': 'example:new_apple', 'count': 3, 'stack_nbt_snbt': '{Count:3b,id:"example:new_apple"}'},
                        'carrier_debit_per_output_item': None}]}
        self.s['native_fd_carriers'] = section; validate_snapshot(self.s)
        self.assertEqual(section['rows'][0]['result']['count'], 3)
        for change in (lambda v:v['rows'][0].update(observed_serve=True),
                       lambda v:v['rows'][0]['result'].update(count=True),
                       lambda v:v['rows'][0].update(carrier_debit_per_output_item=0),
                       lambda v:v.update(recipe_count=2)):
            s = observation(); s['native_fd_carriers'] = copy.deepcopy(section); change(s['native_fd_carriers'])
            with self.assertRaises(ValueError): validate_snapshot(s)

    def test_uuid_spellings_exact_no_strip_or_uppercase(self):
        self.assertEqual(canonical_uuid('01234567-89ab-4cde-8fab-0123456789ab'), canonical_uuid('0123456789ab4cde8fab0123456789ab'))
        for bad in ('{01234567-89ab-4cde-8fab-0123456789ab}', '01234567-89AB-4CDE-8FAB-0123456789AB', 'urn:uuid:01234567-89ab-4cde-8fab-0123456789ab'):
            with self.assertRaises(ValueError): canonical_uuid(bad)


if __name__ == '__main__': unittest.main()
