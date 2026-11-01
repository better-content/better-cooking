#!/usr/bin/env python3
"""Deterministic, fail-closed culinary policy compiler (standard library only).

Discovery is finite; unknowns are data, not fabricated acceptance. The emitted
migration plan is NEVER authorized when unresolved matching/quantity semantics
remain. Qualification also requires a fresh exact native discovery manifest.
"""
import argparse
import collections
import copy
import csv
import io
import hashlib
import json
import math
import os
import re
import tempfile
import ctypes
from pathlib import Path
from extract_catalogue import encoded
from recipe_migration import build_plan, validate_native_plan
from native_import import load_native, require_bound_catalogue
from source_input import load_source_baseline, require_source_baseline
from policy_rules import ALIASES, BULK_ALIASES, CROP_ALIASES
from acquisition_policy import acquisition_plan
from authored_adapters import lower_adapter
from nonstandard_consumables import source_review as nonstandard_review
from native_drink_ingredients import capture as capture_drink_ingredients
from authored_decisions import expand_decisions, decision_provenance, verify_source_inputs
from signed_native_retention import reviewed_retention, validate_profile as validate_signed_profile

HERE = Path(__file__).resolve().parent
WORK = {'straightforward': (6, .5, 600), 'substantial': (8, .7, 1800), 'elaborate': (10, .9, 4800)}
CARRIERS = {'minecraft:bowl', 'minecraft:glass_bottle', 'minecraft:bucket', 'minecraft:flower_pot', 'minecraft:stick'}
SUPPRESSED = ['kaleidoscope_cookery:' + name for name in ('satiated_shield', 'vigor', 'vitality', 'preservation', 'projectile_dodge', 'hinder', 'instant_smelting', 'warmth')]
DISTINCT = ('ferment', 'brewing', 'keg_pouring', 'kettle_pouring', 'aging', 'preserv', 'drying', 'bamboo_tray', 'air_dry')
SPECIAL_ITEMS = {'minecraft:golden_apple', 'minecraft:enchanted_golden_apple', 'minecraft:golden_carrot',
                 'minecraft:chorus_fruit', 'minecraft:suspicious_stew', 'minecraft:rotten_flesh',
                 'minecraft:spider_eye', 'minecraft:poisonous_potato', 'minecraft:pufferfish'}
BULK_PATHS = {'sugar', 'salt', 'flour', 'wheat_flour', 'raw_dough', 'wheat_dough', 'dough', 'raw_noodles',
              'noodles', 'milk_bucket', 'milk_bottle', 'milk', 'cream', 'butter', 'cheese', 'quark',
              'rice', 'cooked_rice', 'cabbage', 'tomato', 'red_chili', 'green_chili', 'lettuce',
              'rice_panicle', 'wheat', 'bread', 'mantou', 'baked_potato', 'dried_kelp',
              'raw_pasta', 'cooked_pasta', 'fried_egg', 'rice_roll_medley', 'beef_patty', 'minced_beef',
              'bacon', 'cooked_bacon', 'chicken_cuts', 'cooked_chicken_cuts', 'cod_slice', 'cooked_cod_slice',
              'salmon_slice', 'cooked_salmon_slice', 'ham', 'smoked_ham', 'horse_feed'}


def active_ticks(work_class, servings):
    if work_class not in WORK: raise ValueError('unknown work class')
    if type(servings) is not int or servings < 1: raise ValueError('servings must be positive integer')
    return math.ceil(WORK[work_class][2] * servings / 8)


def work_stages(action, duration, tool_tag):
    """Preserve exact prescribed active work within native 1200tick/64repeat bounds."""
    if type(duration) is not int or duration < 1: raise ValueError('invalid active work')
    full, remainder = divmod(duration, 1200)
    stages = []
    while full:
        repetitions = min(full, 64)
        stages.append({'action': action, 'duration_ticks': 1200, 'repetitions': repetitions, 'tool_tag': tool_tag})
        full -= repetitions
    if remainder:
        stages.append({'action': action, 'duration_ticks': remainder, 'repetitions': 1, 'tool_tag': tool_tag})
    return stages


def validate_id(value):
    return isinstance(value, str) and re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', value)


def clean_group(group):
    return [{k: e[k] for k in ('kind', 'id', 'count') if k in e} for e in group]


def equivalent_groups(groups, registered_items):
    result = []
    for group in groups:
        choices = clean_group(group)
        for edge in list(choices):
            canonical = ALIASES.get(edge.get('id')) if edge.get('kind') == 'item' else None
            if canonical is not None and canonical in registered_items:
                alternative = {'kind': 'item', 'id': canonical, 'count': edge.get('count', 1)}
                if alternative not in choices: choices.append(alternative)
        result.append(choices)
    return result


def resolve_tags(historical, resources):
    resolved, failures = {}, []
    def visit(tag, chain):
        if tag in resolved: return resolved[tag]
        if tag in chain:
            raise ValueError('tag cycle: ' + ' -> '.join(chain + [tag]))
        raw = resources.get(tag)
        members = set(historical.get(tag, [])) if not raw or not raw.get('replace') else set()
        if raw:
            for value in raw.get('values', []):
                optional = isinstance(value, dict) and not value.get('required', True)
                value = value.get('id') if isinstance(value, dict) else value
                if value.startswith('#'):
                    target = value[1:]
                    if target not in resources and target not in historical:
                        if not optional: failures.append({'tag': tag, 'missing': target})
                    else: members.update(visit(target, chain + [tag]))
                else: members.add(value)
        resolved[tag] = sorted(members)
        return resolved[tag]
    for tag in sorted(set(historical) | set(resources)): visit(tag, [])
    return resolved, failures


def role_for(item, item_tags, rows, drinks):
    namespace, name = item.split(':', 1)
    if item in drinks or 'drink' in item_tags or item.startswith('better_drinking_water:'): return 'drink'
    if item in SPECIAL_ITEMS or namespace in {'ars_nouveau', 'ars_elemental', 'malum', 'goety', 'bloodmagic', 'occultism'}: return 'special'
    if name in BULK_PATHS: return 'staple'
    if any(tag.startswith(('forge:crops/', 'forge:fruits/', 'forge:raw_', 'forge:seeds/')) for tag in item_tags): return 'ingredient'
    if name.startswith(('raw_', 'cooked_')) and not any(x in name for x in ('meal', 'stew', 'soup', 'sandwich', 'rice_bowl')): return 'staple'
    if any(len([g for g in r['input_groups'] if any(e.get('id') not in CARRIERS for e in g)]) > 1 for r in rows): return 'meal'
    if any(x in name for x in ('soup', 'stew', 'salad', 'sandwich', 'burger', 'pie', 'cake', 'dumpling', 'baozi', 'samsa', 'custard', 'pudding', 'halo_halo', 'ice_cream', 'feast', 'rice_bowl')): return 'meal'
    return 'unresolved'


def moisture(item, role, tags):
    """Authored category defaults. Composite derivation stays unresolved until yields/moisture freeze."""
    name = item.split(':', 1)[1]
    if name in {'melon_slice', 'orange', 'lemon', 'lime', 'grapefruit', 'pomelo'} or ('soup' in name and 'thick' not in name): return (4., 5., 'juicy_or_liquid_soup')
    if 'salad' in name or 'stew' in name or any(t.startswith('forge:fruits/') for t in tags): return (2., 2.5, 'fruit_salad_thick_stew')
    if any(x in name for x in ('bread', 'pastry', 'cake', 'pie', 'cookie', 'biscuit', 'pandesal', 'ensaymada', 'polvoron')): return (.25, .3125, 'softbread_pastry')
    if any(x in name for x in ('jerky', 'dried_', 'cracker')): return (0., 0., 'explicit_dry')
    if any(x in name for x in ('beef', 'pork', 'chicken', 'mutton', 'steak', 'bacon', 'ham', 'cod', 'salmon', 'meat')): return (.5, .625, 'meat')
    if any(t.startswith('forge:crops/') for t in tags) or any(x in name for x in ('rice', 'carrot', 'potato', 'cabbage', 'tomato')): return (1., 1.25, 'moist_vegetable_cookedgrain')
    return (None, None, 'unresolved')


def surface_for(row, output):
    name = output.split(':', 1)[1]
    kind = str(row.get('type'))
    if kind.startswith('kaleidoscope_cookery:'):
        tail = kind.split(':')[1].removeprefix('flex_')
        if tail == 'chopping_board': return 'board'
        if tail in ('pot', 'stockpot', 'steamer', 'teapot'): return tail
    if any(x in name for x in ('cake', 'pie', 'cookie', 'bread', 'pastry', 'pandesal', 'ensaymada', 'hopia')): return 'baking_mat'
    if any(x in name for x in ('soup', 'stew', 'congee', 'borscht')): return 'stockpot'
    if any(x in name for x in ('salad', 'sandwich', 'burger', 'sushi', 'roll_medley', 'ice_cream', 'halo_halo')): return 'board'
    if any(x in name for x in ('dumpling', 'baozi', 'mantou', 'zongzi', 'leche_flan')): return 'steamer'
    if row.get('serializer') in ('minecraft:smelting', 'minecraft:smoking', 'minecraft:campfire_cooking'): return 'roasting'
    return 'pot'


def _require_input_origin(frozen):
    try:
        return require_source_baseline(frozen)
    except ValueError:
        return require_bound_catalogue(frozen)


def compile_policy(frozen, overrides=None, authored=None):
    """Production entrypoint: positive source/native origin required, never flags."""
    _require_input_origin(frozen)
    result = _compile_policy_model(frozen, overrides, authored)
    _require_input_origin(frozen)
    validate(result, frozen)
    return result


def _compile_policy_model(frozen, overrides=None, authored=None):
    """PRIVATE speculative unit-model calculation, not publication/admission API."""
    if any(k in frozen for k in ('native_observation_partition', 'native_observation_provenance', 'native_source_recipe_baseline', 'native_snapshot')):
        require_bound_catalogue(frozen)
    overrides = overrides or {}
    if authored is not None: verify_source_inputs(authored, frozen)
    food_reviews, route_reviews = expand_decisions(authored) if authored is not None else ({}, {})
    tags, tag_failures = resolve_tags(frozen['item_tags'], frozen.get('resource_tags', {}))
    by_item_tags = collections.defaultdict(list)
    for tag, values in tags.items():
        for item in values: by_item_tags[item].append(tag)
    by_output = collections.defaultdict(list)
    for row in frozen['recipes'].values():
        for out in row['outputs']:
            if out.get('kind') == 'item': by_output[out['id']].append(row)
    drinks = frozen.get('managed_drinks', {})
    declarations = copy.deepcopy(frozen['foods'])
    for item in drinks:
        declarations.setdefault(item, {'food': {'nutrition': 0, 'saturation_modifier': 0}, 'effects': None, 'evidence': ['bundled_drinks_resource']})
    profiles = {}
    for item, source in sorted(declarations.items()):
        itemtags = sorted(by_item_tags[item])
        nonstandard = source.get('nonstandard_consumable')
        review = food_reviews.get(item, nonstandard_review(nonstandard) if nonstandard else {})
        role = review.get('role', role_for(item, itemtags, by_output[item], drinks))
        base = source['food']
        signed_retention = reviewed_retention(item, review, base)
        if any(value is not None and value < 0 for value in (base['nutrition'], base['saturation_modifier'])) and not signed_retention:
            if review.get('role', 'special') != 'special': raise ValueError('authored decision rebalances harmful native food: ' + item)
            role = 'special'  # Other signed candidates retain the existing fail-closed rule.
        rows = by_output[item]
        complexity = max((len(r['input_groups']) for r in rows), default=0)
        workclass = ('elaborate' if complexity >= 7 else 'substantial' if complexity >= 4 else 'straightforward') if role == 'meal' else 'none'
        work_pending = review.get('work_class') == 'unresolved'
        if work_pending and 'work_class' in review.get('reviewed', []):
            raise ValueError('authored review cannot approve unresolved work: ' + item)
        # A held work decision retains an explicitly UNAPPROVED structural
        # placeholder; it is never silently counted as a reviewed exemption.
        if not work_pending: workclass = review.get('work_class', workclass)
        if role == 'meal' and workclass not in WORK: raise ValueError('authored meal needs explicit valid work class: ' + item)
        if role != 'meal' and workclass != 'none': raise ValueError('nonmeal work class must remain none: ' + item)
        hunger, saturation = WORK[workclass][:2] if role == 'meal' else (base['nutrition'], base['saturation_modifier'])
        if nonstandard:
            # This is a normalized NO FoodData operation target, NOT getter zeros.
            target = nonstandard['source_finish_food_mutation']['normalized_target']
            hunger, saturation = target['hunger'], target['saturation_modifier']
            hydration, quenching, moisture_class = None, None, 'actual_native_paid_batch_entitlement_required'
        else: hydration, quenching, moisture_class = moisture(item, role, itemtags)
        if review.get('moisture'):
            value = review['moisture']
            hydration, quenching, moisture_class = value['hydration'], value['quenching'], value['class']
        reviewed_class = {'role', 'work_class'} <= set(review.get('reviewed', []))
        unresolved = [] if reviewed_class else ['candidate_classification_requires_review']
        unresolved.extend(review.get('unresolved', []))
        if work_pending: unresolved.append('authored_work_class_requires_explicit_decision')
        if role == 'unresolved': unresolved.append('food_role_requires_review')
        if hydration is None: unresolved.append('moisture_requires_review')
        elif 'moisture' not in review.get('reviewed', []):
            unresolved.append('candidate_moisture_requires_explicit_review')
        if hunger is None or saturation is None:
            unresolved.append('placed_or_dynamic_food_properties_not_enumerated')
        if source.get('effects') is None: unresolved.append('native_effects_not_enumerated')
        elif source.get('effects') and not source.get('effects_resolved'): unresolved.append('source_effect_expressions_need_native_resolution')
        if source.get('nutrients_resolved') is not True: unresolved.append('native_nutrients_not_enumerated')
        if role == 'meal': unresolved.append('composite_ingredient_moisture_and_nutrients_require_resolved_yield')
        if item in frozen['servings'] and frozen['servings'][item].get('portions') is None:
            unresolved.append('native_serving_count_not_enumerated')
        profile = {'id': item, 'role': role, 'canonical_id': ALIASES.get(item, item), 'work_class': workclass,
                   'servings': frozen['servings'].get(item, {}).get('portions') or 1,
                   'base_hunger': hunger, 'base_saturation': saturation, 'hydration': hydration, 'quenching': quenching,
                   'nutrients': {}, 'effects': [], 'quality': 'fixed_standard' if role != 'meal' else 'carry_preparation',
                   'moisture_class': moisture_class, 'nutrition_basis': 'work_class_standard' if role == 'meal' else 'retained_native',
                   'native_food': base, 'native_effect_expressions': source.get('effects'),
                   'native_consumption': source.get('native_consumption'),
                   'diet_tags_for_audit_only': [t for t in itemtags if t.startswith('diet:')],
                   'evidence': source['evidence'], 'unresolved': unresolved,
                   'classification': 'candidate_rule_requires_native_review',
                   'quantity_basis': 'per_portion' if role == 'meal' else 'native_intake',
                   'serving_metadata': {
                       'servings': 'edible_portions_per_item',
                       'nutrition': 'per_edible_portion' if role == 'meal' else 'retained_native_intake',
                       'moisture': 'per_edible_portion',
                       'nutrients': 'ingredient_provenance_per_edible_portion' if role == 'meal' else 'retained_native_intake',
                       'whole_prepared_item_multiplier': frozen['servings'].get(item, {}).get('portions') or 1,
                       'placed_bite_multiplier': 1,
                       'serving_count_proven': item not in frozen['servings'] or frozen['servings'][item].get('portions') is not None}}
        if source.get('effects_resolved'):
            profile['effects'] = copy.deepcopy(source['effects'])
        if source.get('nutrients_resolved'):
            ordinary = {key: value for key, value in (source.get('nutrients') or {}).items() if key.lower() not in ('spice', 'spices')}
            if role == 'meal':
                if profile['serving_metadata']['serving_count_proven']:
                    profile['nutrients'] = {key: value / profile['servings'] for key, value in ordinary.items()}
                    profile['serving_metadata']['native_nutrient_fallback'] = 'full_item_gains_divided_once_by_proven_portions'
                else: profile['unresolved'].append('native_nutrient_portion_basis_unknown')
            else: profile['nutrients'] = ordinary
        profile['serving_metadata']['native_output_diet_fallback_requires_explicit_route_approval'] = True
        profile['serving_metadata']['actual_preparation_nutrients'] = 'already_per_portion_supersedes_native_fallback_no_extra_tag_credit'
        profile['serving_metadata']['native_sugar_alcohol_spice'] = 'full_item_intake_unscaled_in_native_consumption'
        if source.get('placed_only_candidate'):
            profile['placed_only_candidate'] = True
            profile['unresolved'].append('block_candidate_not_an_edible_item_assertion')
        if item in drinks:
            drink = drinks[item]
            if drink.get('hydration_class') and drink.get('serving_mb'):
                profile['hydration'], profile['quenching'] = drink['hydration'], drink['quenching']
                profile['effects'] = drink.get('effects', [])
                profile['moisture_class'] = drink['hydration_class']
                profile['serving_mb'] = drink['serving_mb']
                profile['intake_owner'] = 'better_drinking_water'
                profile['quantity_basis'] = 'paid_beverage' if role == 'drink' else 'native_intake'
                profile['culinary_food_completion_credit'] = False
                profile['evidence'] = profile['evidence'] + ['authored_managed_drinks_resource']
                profile['unresolved'] = [u for u in unresolved if u not in ('moisture_requires_review', 'candidate_moisture_requires_explicit_review', 'native_effects_not_enumerated')]
            else:
                profile['unresolved'].append('bundled_drink_values_stale_requires_authored_classification')
        if nonstandard:
            profile['native_food_properties'] = None
            profile['nonstandard_consumable'] = copy.deepcopy(nonstandard)
            profile['nutrition_basis'] = 'source_finish_no_FoodData_mutation_not_zero_FoodProperties'
            profile['quantity_basis'] = 'native_intake'
            profile['intake_owner'] = 'better_cooking_native_whole_completion'
            profile['serving_metadata'].update(servings='completed_native_uses_per_item', whole_prepared_item_multiplier=1,
                serving_count_proven=False, native_completion_status='source_only_until_connected_finish_receipt')
            profile['unresolved'].append('native_nonstandard_finish_debit_container_effects_and_paid_provenance_require_connected_acceptance')
            if nonstandard.get('source_production', {}).get('source_safety_requires_native_closure'):
                profile['unresolved'].append('native_paid_source_safety_delivery_or_source_proven_purification_requires_acceptance')
            if not source.get('native_nonstandard_base_getters_observed'): profile['unresolved'].append('native_nonstandard_getters_not_enumerated')
            for dose in ('sugar', 'alcohol', 'spice'):
                if (source.get('native_consumption') or {}).get(dose) is None: profile['unresolved'].append('native_nonstandard_' + dose + '_dose_not_enumerated')
        if review:
            profile['authored_review'] = copy.deepcopy(review)
            profile['evidence'] = profile['evidence'] + review['evidence']
            profile['classification'] = 'authored_source_reviewed_pending_native' if reviewed_class else 'partially_authored_review_pending_native'
        if role == 'meal':
            explicit_water = 'moisture' in review.get('reviewed', []) and review.get('moisture') and all(review['moisture'].get(k) is not None for k in ('hydration', 'quenching'))
            profile['composite_moisture'] = {
                'basis': 'per_edible_portion',
                'mode': 'direct_override' if explicit_water else 'derive_actual_paid_inputs',
                'direct_override': {'hydration': review['moisture']['hydration'], 'quenching': review['moisture']['quenching'],
                                    'reason': review['reason'], 'evidence': review['evidence'],
                                    'decision_basis': 'authored_category_balance_override_not_native_measured_water'} if explicit_water else None,
                'process_retention': None, 'added_water_mb': None,
                'normalization': 'actual_edible_yield_once',
                'no_double_credit': 'direct_override_replaces_ingredient_water_never_adds_to_it',
                'quality_scales_water': False,
                'status': 'authored_override_pending_native_delivery' if explicit_water else 'requires_authored_route_retention_and_added_water'}
        profiles[item] = profile
    unknown_review_foods = sorted(set(food_reviews) - set(profiles))
    routes = []
    for rid, row in sorted(frozen['recipes'].items()):
        outputs = row['outputs']
        edible = [o for o in outputs if o.get('id') in profiles]
        output = next((o for o in outputs if o.get('kind') == 'item'), None)
        primary = output.get('id') if output else None
        role = profiles.get(primary, {}).get('role')
        surface = surface_for(row, primary) if primary else None
        issues = list(row.get('issues', []))
        adapter_plan = {}
        if str(row['type']).startswith('better_cooking:'): disposition = 'retain_special'
        elif primary in BULK_ALIASES: disposition = 'retain_bulk'
        elif not edible: disposition = 'retain_nonfood'
        elif any(s in str(row['type']) for s in DISTINCT): disposition = 'retain_distinct_apparatus'
        elif role == 'special': disposition = 'retain_special'
        elif role in ('ingredient', 'staple', 'drink'): disposition = 'retain_bulk'
        elif role == 'meal': disposition = 'replace'
        else: disposition = 'unresolved'
        food_review = food_reviews.get(primary, {})
        if disposition == 'replace' and food_review.get('adapter'):
            adapter_plan = lower_adapter(food_review, row) or {}
            if adapter_plan: surface = adapter_plan['surface']
            else: issues.append('authored_adapter_requires_explicit_lowering')
        route_review = route_reviews.get(rid, {})
        if route_review:
            if route_review.get('source_type', row['type']) != row['type']: raise ValueError('authored route serializer changed: ' + rid)
            disposition = route_review.get('disposition', disposition)
            surface = route_review.get('surface', surface)
            issues.extend(route_review.get('unresolved', []))
            if disposition == 'replace':
                reviewed_adapter = lower_adapter(route_review, row)
                if reviewed_adapter: adapter_plan = reviewed_adapter
        finish = adapter_plan.get('finish')
        preparation_surface = adapter_plan.get('preparation_surface')
        original_groups = [clean_group(g) for g in row['input_groups']]
        groups = equivalent_groups(row['input_groups'], frozen['items']) if disposition == 'replace' else original_groups
        if any(e.get('kind') not in ('item', 'tag') for g in groups for e in g): issues.append('unsupported_input_kind_requires_adapter')
        for group in groups:
            for edge in group:
                if edge.get('kind') == 'item' and edge.get('id') not in frozen['items']: issues.append('unknown_item_reference:' + edge['id'])
                if edge.get('kind') == 'tag' and not tags.get(edge['id']): issues.append('empty_or_unknown_tag_reference:' + edge['id'])
        if disposition == 'replace': issues.append('candidate_route_requires_native_review')
        if disposition == 'replace':
            if len(outputs) != 1: issues.append('multiple_outputs_need_transactional_adapter')
            if any('chance' in o and o['chance'] != 1 for o in outputs): issues.append('probabilistic_output_requires_native_adapter')
            if not groups or any(not g for g in groups): issues.append('incomplete_ingredient_groups')
            if row.get('raw', {}).get('carrier') or row.get('catalysts'): issues.append('carrier_or_catalyst_must_be_accounted_by_native_surface')
            if row.get('raw', {}).get('soup_base'): issues.append('soup_base_volume_requires_native_adapter')
            if row.get('raw', {}).get('type', '').startswith('kaleidoscope_cookery:flex_'): issues.append('flexible_formulation_requires_native_evaluator')
            if surface == 'baking_mat': issues.append('prepared_raw_product_and_furnace_baking_need_registered_stage_item')
            if any(len(g) > 1 for g in groups): issues.append('alternatives_require_live_membership_recheck')
        wc = route_review.get('work_class', profiles.get(primary, {}).get('work_class', 'none'))
        portions = profiles.get(primary, {}).get('servings', 1) * (output.get('count', 1) if output else 1)
        duration = active_ticks(wc, portions) if wc in WORK else 1
        action = {'board': 'assemble', 'pot': 'stir', 'stockpot': 'stir', 'steamer': 'assemble', 'baking_mat': 'knead', 'roasting': 'assemble', 'teapot': 'mix'}.get(surface, 'assemble')
        action = route_review.get('action', adapter_plan.get('action', action))
        work_tool = adapter_plan.get('tool_tag', 'forge:tools/knives' if action == 'cut' else 'better_cooking:preparation_tools')
        # Gate data is provenance, never a synthetic token or a bypass of actual inputs.
        gates = []
        for ordinal, group in enumerate(groups):
            for edge in group:
                values = tags.get(edge['id'], []) if edge.get('kind') == 'tag' else [edge.get('id')]
                canonical = sorted({ALIASES.get(v, v) for v in values})
                crop = [v for v in canonical if any(t.startswith(('forge:crops/', 'forge:grain/')) for t in by_item_tags[v])]
                if crop or edge.get('id', '').startswith(('forge:crops/', 'forge:grain/')):
                    gates.append({'input_group': ordinal, 'requirement': 'actual_ingredient_origin', 'authority': 'farmersdelight_bumblezone', 'canonical_members': crop})
        if disposition == 'replace':
            issues.append('actual_paid_input_nutrition_water_and_carrier_facts_require_complete_resolution')
        known_cook_time = row.get('requirements', {}).get('time')
        if disposition == 'replace' and finish != 'board' and not known_cook_time:
            issues.append('native_finish_duration_requires_public_default_or_authored_decision')
        native_quality = {'mode': 'fixed_standard_unless_inherited_preparation', 'inheritance': 'worst_inherited_no_reroll_or_compound'}
        if row['type'] in ('kaleidoscope_cookery:flex_pot', 'kaleidoscope_cookery:flex_stockpot'):
            native_quality = {'mode': 'retained_seeded_flexible_evaluator', 'source_recipe_id': rid,
                              'source_recipe_type': row['type'], 'seed_source': 'actual_runtime_world_seed',
                              'input_basis': 'actual_paid_counted_inputs',
                              'inheritance': 'worst_inherited_no_reroll_or_compound',
                              'formulation': {'max_native_units': 9, 'debit': 'one_count1_unit_per_occupied_input_slot',
                                              'required_groups': 'each_covered', 'ratios': 'native_user_selectable'},
                              'evidence': ['kc_source:blockentity/kitchen/' + ('PotBlockEntity.java#applyFlexRecipe' if row['type'].endswith(':flex_pot') else 'StockpotBlockEntity.java#applyFlexRecipe'), 'kc_source:item/quality/QualityEvaluator.java']}
        route = {'id': 'better_cooking:migration/' + rid.replace(':', '/', 1), 'source_id': rid,
                 'native_quality': native_quality, 'serving_basis': 'total_edible_portions_in_original_output_batch',
                 'ingredient_intake_contract': {
                     'basis': 'actual_paid_counted_stacks_including_nonedible_ingredients_and_carriers',
                     'edible_food_catalogue_is_not_complete_ingredient_facts': True,
                     'unknown_profile_or_optional': 'unresolved_never_zero_or_empty_map',
                     'null_food_properties': 'not_a_zero_nutrient_or_water_declaration',
                     'native_output_diet_fallback': 'requires_explicit_root_route_approval_no_automatic_unknown_input_fallback',
                     'frozen_drink_defaults_scope': 'only_reviewed_six_bags_live_flowers_and_water_milk_exact_variants_not_flour_wheat_or_arbitrary_carriers'},

                 'type': 'better_cooking:preparation', 'source_type': row['type'],
                 'inputs': [{e['kind']: e['id'], 'count': e.get('count', 1)} for g in groups if len(g) == 1 for e in g if e.get('kind') in ('item', 'tag')],
                 'input_groups': groups, 'original_input_groups': original_groups, 'source_original_input_groups': original_groups,
                 'output': {'item': primary, 'count': output.get('count', 1)} if output else None,
                 'outputs': outputs, 'disposition': disposition, 'surface': surface,
                 'finish': finish, 'preparation_surface': preparation_surface,
                 'adapter_decision': route_review.get('adapter', food_review.get('adapter')),
                 'adapter_reviewed': 'adapter' in (route_review if 'adapter' in route_review else food_review).get('reviewed', []),
                 'replaces': [rid] if disposition == 'replace' else [],
                 'stages': work_stages(action, max(1, duration), work_tool) if disposition == 'replace' else [],
                 'cook_ticks': max(1, known_cook_time or 1),
                 'cook_ticks_basis': 'source_declared_or_native_getter' if known_cook_time else 'positive_placeholder_not_qualified_source_duration',
                 'servings': portions, 'work_class': wc, 'origin_gates': gates,
                 'evidence': row['evidence'], 'unresolved': sorted(set(issues)),
                 'original_resource': row.get('raw'), 'native_semantics': row.get('native_semantics'),
                 'classification': 'candidate_rule_requires_native_review'}
        if row.get('source_native_production'):
            route['source_native_production'] = copy.deepcopy(row['source_native_production'])
            route['native_declared_result_prototype'] = copy.deepcopy(row.get('native_declared_result_prototype'))
            route['serving_basis'] = 'completed_native_drink_items_in_source_batch_not_display_group_size'
        if route_review:
            route['authored_review'] = copy.deepcopy(route_review)
            route['evidence'] = route['evidence'] + route_review['evidence']
            route['classification'] = 'authored_source_reviewed_pending_native'
            if 'candidate_route_requires_native_review' in route['unresolved']:
                route['unresolved'].remove('candidate_route_requires_native_review')
                route['unresolved'].append('authored_route_requires_native_acceptance')
        routes.append(route)
    gaps = [{'code': 'historical_snapshot_not_current', 'detail': x} for x in frozen['limitations']]
    gaps.extend({'code': 'resource_tag_reference', 'detail': failure} for failure in tag_failures)
    gaps.extend({'code': 'native_import', 'detail': gap} for gap in frozen.get('native_import_gaps', []))
    if 'native_observation_partition' in frozen:
        gaps.extend({'code': 'native_authored_route_live_unavailable', 'detail': source} for source in sorted(route_reviews))
    gaps.extend({'code': 'authored_food_not_currently_declared', 'detail': item} for item in unknown_review_foods)
    gaps.extend({'code': 'authored_route_not_currently_effective', 'detail': rid} for rid in sorted(set(route_reviews) - set(frozen['recipes'])))
    gaps.extend([{'code': 'native_surface_coverage', 'detail': detail} for detail in (
        'Exact current registry/effects/recipe conditions and post-KubeJS graph must be freshly frozen.',
        'Placed FD feasts/cake bites, KC portable vessels/teapot/lunchbag nested contents need native intake enumeration.',
        'Ingredient nutrients and composite added-water/retention/yield require per-route provenance; Diet membership is not edible evidence.',
        'Current KubeJS recipes, including food_eras, are pinned but not statically executed; runtime compiler input required.',
        'Loot/trades/world acquisition and renewable meal trade offers are not proven by this recipe-resource graph; finite found stock stays unchanged.',
        'Candidate classifiers require explicit native review before qualification; no name heuristic is an acceptance oracle.')])
    policy = {'schema': 'better_cooking:culinary_policy.v1', 'version': 1, 'qualification': 'unqualified',
              'provenance': {'frozen_inputs_sha256': hashlib.sha256(encoded(frozen).encode()).hexdigest(),
                             'snapshot_id': frozen['snapshot']['snapshot_id'], 'input_count': len(frozen['manifest']),
                             'native_snapshot_sha256': frozen.get('native_snapshot_sha256'),
                             'native_snapshot_raw_sha256': frozen.get('native_snapshot_raw_sha256'),
                             'native_snapshot_sha256_basis': 'canonical_sorted_indented_JSON_not_raw_bytes'},
              'defaults': {'quality_multipliers': {'poor': .75, 'standard': 1., 'excellent': 1.15, 'superb': 1.30},
                           'reference_portions': 8, 'work_classes': {k: {'hunger': v[0], 'saturation_modifier': v[1], 'active_ticks_per_eight_portions': v[2]} for k, v in WORK.items()},
                           'effect_cap': {'amplifier': 0, 'duration_ticks': 1200, 'preserve_preexisting_stronger': True, 'preserve_harmful_probability': True},
                           'suppress_effects': SUPPRESSED, 'ingredient_age': 'oldest_settled_input',
                           'quality_scales': ['hunger', 'total_saturation_once', 'ordinary_nutrients_once'],
                           'quality_never_scales': ['water', 'quenching', 'sugar', 'alcohol', 'spice_dose'],
                           'derived_nutrients': 'divide_actual_ingredient_provenance_by_actual_edible_yield_no_direct_tag_double_credit'},
              'aliases': {a: b for a, b in ALIASES.items() if a in frozen['items'] and b in frozen['items']},
              'foods': {}, 'servings': copy.deepcopy(frozen['servings']), 'routes': {},
              'exceptions': {'registered_legacy_items': 'retain_ids_and_finite_found_stock',
                             'distinct_apparatus': 'retain_existing_brewing_fermentation_aging_preserving',
                             'deferred': ['new_canning', 'new_drying', 'preservation_overhaul']}, 'gaps': gaps}
    if 'native_observation_partition' in frozen:
        policy['native_observation_partition'] = copy.deepcopy(frozen['native_observation_partition'])
        policy['native_observation_provenance'] = copy.deepcopy(frozen['native_observation_provenance'])
        policy['native_unresolved_recipe_ids'] = sorted(row['id'] for row in frozen['native_unresolved_recipes'])
        policy['provenance']['frozen_inputs_sha256_basis'] = 'derived_public_catalogue_not_raw_native_identity'
        policy['provenance']['native_snapshot_raw_sha256'] = frozen['native_observation_partition']['raw_sha256']
        policy['provenance']['native_snapshot_sha256_basis'] = 'not_computed_native_tree_retained_only_in_private_capsule'
        policy['provenance']['native_food_projection'] = 'none_source_food_profile_facts_only'
    if authored is not None: policy['provenance']['authored_decisions'] = decision_provenance(authored)
    # V3 wire contract: keyed food profiles and original source-route IDs.
    for item, profile in profiles.items():
        profile['canonical'] = profile.pop('canonical_id')
        profile['nutrition'] = {'hunger': profile.pop('base_hunger'), 'saturation_modifier': profile.pop('base_saturation'), 'nutrients': profile.pop('nutrients')}
        profile['moisture'] = {'hydration': profile.pop('hydration'), 'quenching': profile.pop('quenching')}
        policy['foods'][item] = profile
    for route in routes:
        replacement = None
        if route['disposition'] == 'replace':
            replacement = {key: route[key] for key in ('id', 'surface', 'finish', 'preparation_surface', 'input_groups', 'servings', 'work_class', 'stages', 'cook_ticks', 'native_quality')}
            replacement['output'] = {'id': route['output']['item'], 'count': route['output']['count']}
        route['replacement'] = replacement
        policy['routes'][route['source_id']] = route
    # Authored reviews use the public V3 shape, not compiler-private fields.
    # Review metadata never supplies native qualification or rewrites ratios.
    for item, review in overrides.get('foods', {}).items():
        if item not in policy['foods']: raise ValueError('review references unknown food: ' + item)
        if 'id' in review and review['id'] != item: raise ValueError('review changes food identity')
        policy['foods'][item].update(copy.deepcopy(review))
    for source, review in overrides.get('routes', {}).items():
        if source not in policy['routes']: raise ValueError('review references unknown route: ' + source)
        route = policy['routes'][source]
        if 'source_id' in review and review['source_id'] != source: raise ValueError('review changes source identity')
        replacement_review = review.get('replacement')
        route.update({k: copy.deepcopy(v) for k, v in review.items() if k != 'replacement'})
        if replacement_review:
            if not route['replacement']: raise ValueError('review cannot invent replacement for retained source')
            route['replacement'].update(copy.deepcopy(replacement_review))
            for key in ('id', 'surface', 'finish', 'preparation_surface', 'input_groups', 'servings', 'work_class', 'stages', 'cook_ticks', 'native_quality'):
                route[key] = route['replacement'][key]
            route['output'] = {'item': route['replacement']['output']['id'], 'count': route['replacement']['output']['count']}
        if route['disposition'] != 'replace':
            route['replaces'], route['replacement'] = [], None
    validate(policy, frozen)
    policy['migration'] = build_plan(policy)
    policy['acquisition'] = acquisition_plan(frozen, authored.get('acquisition') if authored else None)
    policy['nonstandard_consumables'] = copy.deepcopy(frozen.get('nonstandard_consumables', {}).get('consumables', {}))
    policy['native_drink_ingredients'] = copy.deepcopy(frozen.get('native_drink_ingredients') or capture_drink_ingredients(frozen, {}))
    policy['native_unresolved_recipe_ids'] = sorted(row['id'] for row in frozen.get('native_unresolved_recipes', []))
    return policy


def validate(policy, frozen=None):
    if policy.get('qualification') == 'qualified':
        raise ValueError('qualified policy refused: no NativeAcceptance authority in initial v2 phase (unresolved acceptance gate)')
    if policy.get('qualification') != 'unqualified': raise ValueError('invalid policy qualification status')
    if frozen is not None and any(k in frozen for k in ('native_observation_partition', 'native_observation_provenance', 'native_source_recipe_baseline', 'native_snapshot')):
        require_bound_catalogue(frozen)
        if policy.get('native_observation_partition') != frozen['native_observation_partition']:
            raise ValueError('native partition missing/changed in policy')
        if policy.get('native_observation_provenance') != frozen['native_observation_provenance']:
            raise ValueError('native provenance missing/changed in policy')
        provenance = policy.get('provenance') or {}
        if (provenance.get('frozen_inputs_sha256') != hashlib.sha256(encoded(frozen).encode()).hexdigest() or
                provenance.get('native_snapshot_raw_sha256') != frozen['native_observation_partition']['raw_sha256'] or
                provenance.get('frozen_inputs_sha256_basis') != 'derived_public_catalogue_not_raw_native_identity'):
            raise ValueError('native derived/raw hash provenance changed')
    validate_native_plan(policy)
    if policy.get('schema') != 'better_cooking:culinary_policy.v1': raise ValueError('wrong schema')
    if not isinstance(policy.get('foods'), dict) or not isinstance(policy.get('routes'), dict): raise ValueError('foods and routes must be maps')
    ids, foodids, removed = set(), set(), set()
    for food_key, food in policy['foods'].items():
        if food_key != food['id']: raise ValueError('food map key differs from identity')
        if food['role'] not in ('ingredient', 'staple', 'meal', 'drink', 'special', 'unresolved'): raise ValueError('invalid food role')
        if food['work_class'] not in ('none', 'unresolved', *WORK): raise ValueError('invalid food work class')
        if food['quality'] not in ('fixed_standard', 'carry_preparation'): raise ValueError('invalid food quality')
        if food['quantity_basis'] not in ('per_portion', 'native_intake', 'paid_beverage'): raise ValueError('invalid quantity basis')
        if food['role'] == 'meal' and food['quantity_basis'] != 'per_portion': raise ValueError('protected meal quantity basis must be per_portion')
        if not validate_id(food['id']) or food['id'] in foodids: raise ValueError('invalid/duplicate food ID')
        foodids.add(food['id'])
        if not validate_id(food['canonical']): raise ValueError('invalid canonical ID')
        signed_retention = validate_signed_profile(food)
        numeric = dict(food['nutrition'], **food['moisture'])
        for key in ('hunger', 'saturation_modifier', 'hydration', 'quenching'):
            value = numeric[key]
            if value is not None and (type(value) not in (float, int) or not math.isfinite(value)): raise ValueError('invalid food value: ' + key)
            if value is not None and value < 0 and not (key in ('hunger', 'saturation_modifier') and (food['role'] == 'special' or signed_retention)):
                raise ValueError('signed food values require special native or finite unresolved source retention')
            if value is None and not food['unresolved']: raise ValueError('unknown food value without unresolved gap')
        for nutrient, value in food['nutrition']['nutrients'].items():
            if nutrient.lower() in ('spice', 'spices'): raise ValueError('spice cannot be ordinary nutrient credit')
            if type(value) not in (int, float) or not math.isfinite(value) or value < 0: raise ValueError('invalid nutrient value')
        if type(food['servings']) is not int or food['servings'] < 1: raise ValueError('invalid servings')
    for source_key, route in policy['routes'].items():
        if source_key != route['source_id']: raise ValueError('source route map key differs from identity')
        if route['disposition'] not in ('replace', 'retain_bulk', 'retain_special', 'retain_nonfood', 'retain_distinct_apparatus', 'deferred', 'unresolved'): raise ValueError('invalid route disposition')
        if not validate_id(route['id']) or route['id'] in ids: raise ValueError('invalid/duplicate route ID')
        ids.add(route['id'])
        if route['disposition'] == 'replace':
            if route['replaces'] != [route['source_id']]: raise ValueError('replacement must target exact source ID')
            if removed.intersection(route['replaces']): raise ValueError('duplicate recipe removal')
            removed.update(route['replaces'])
            if not route['output'] or type(route['output']['count']) is not int or route['output']['count'] <= 0: raise ValueError('invalid replacement output')
            if route['surface'] not in ('board', 'pot', 'stockpot', 'steamer', 'baking_mat', 'baking', 'roasting', 'teapot'): raise ValueError('invalid replacement surface')
            if type(route['servings']) is not int or route['servings'] < 1: raise ValueError('invalid replacement servings')
            output_profile = policy['foods'].get(route['output']['item'])
            if output_profile and route['servings'] != route['output']['count'] * output_profile['servings']:
                raise ValueError('batch portion ratio differs from per-item profile')
            if type(route['cook_ticks']) is not int or route['cook_ticks'] < 1: raise ValueError('invalid cook clock')
            if not route['stages']: raise ValueError('missing work stage')
            replacement = route.get('replacement')
            if not replacement: raise ValueError('missing nested replacement')
            if replacement.get('finish') is not None and replacement['finish'] not in ('board', 'pot', 'stockpot', 'steamer', 'baking', 'roasting', 'teapot'): raise ValueError('invalid explicit finishing adapter')
            if replacement.get('preparation_surface') not in (None, 'board', 'baking_mat'): raise ValueError('invalid explicit preparation station')
            for key in ('id', 'surface', 'finish', 'preparation_surface', 'input_groups', 'servings', 'work_class', 'stages', 'cook_ticks', 'native_quality'):
                if replacement[key] != route[key]: raise ValueError('nested replacement differs: ' + key)
            if replacement['output'] != {'id': route['output']['item'], 'count': route['output']['count']}:
                raise ValueError('nested output ratio changed')
            for stage in route['stages']:
                if stage['action'] not in ('cut', 'mix', 'knead', 'roll', 'assemble', 'stir') or not validate_id(stage['tool_tag']): raise ValueError('invalid stage action/tool')
                if type(stage['duration_ticks']) is not int or type(stage['repetitions']) is not int or not 1 <= stage['duration_ticks'] <= 1200 or not 1 <= stage['repetitions'] <= 64: raise ValueError('invalid work stage')
        elif route['replaces'] or route.get('replacement'): raise ValueError('retained route removes recipe')
        for group in route['input_groups']:
            for edge in group:
                if not validate_id(edge.get('id')) or edge.get('count', 1) <= 0: raise ValueError('invalid ingredient')
                if edge.get('kind') not in ('item', 'tag'):
                    if not route['unresolved']: raise ValueError('unsupported edge without gap')
        quality = route.get('native_quality', {})
        if quality.get('mode') == 'retained_seeded_flexible_evaluator':
            if quality.get('source_recipe_id') != route['source_id'] or quality.get('source_recipe_type') not in ('kaleidoscope_cookery:flex_pot', 'kaleidoscope_cookery:flex_stockpot'):
                raise ValueError('seeded quality lacks original flexible source identity')
            if quality.get('seed_source') != 'actual_runtime_world_seed' or quality.get('input_basis') != 'actual_paid_counted_inputs':
                raise ValueError('seeded quality uses substitute seed or unpaid inputs')
            if quality.get('formulation') != {'max_native_units': 9, 'debit': 'one_count1_unit_per_occupied_input_slot', 'required_groups': 'each_covered', 'ratios': 'native_user_selectable'}:
                raise ValueError('native flexible physical-unit formulation changed')
        elif quality.get('mode') != 'fixed_standard_unless_inherited_preparation': raise ValueError('unknown quality evaluation mode')
        if frozen and frozen['recipes'][route['source_id']]['type'] in ('kaleidoscope_cookery:flex_pot', 'kaleidoscope_cookery:flex_stockpot') and quality.get('mode') != 'retained_seeded_flexible_evaluator':
            raise ValueError('native flexible seeded evaluator lost')
        if frozen and route['disposition'] == 'replace':
            original = frozen['recipes'][route['source_id']]
            if route['input_groups'] != equivalent_groups(original['input_groups'], frozen['items']): raise ValueError('ingredient ratios or canonical equivalences changed')
            expected = next((o for o in original['outputs'] if o.get('kind') == 'item'), None)
            if expected and route['output'] != {'item': expected['id'], 'count': expected.get('count', 1)}: raise ValueError('output ratio changed')
    for alias, canonical in policy['aliases'].items():
        if not validate_id(alias) or not validate_id(canonical): raise ValueError('invalid alias ID')
        if frozen and (alias not in frozen['items'] or canonical not in frozen['items']): raise ValueError('unknown alias reference')
        if alias == canonical: raise ValueError('self alias')
        seen = {alias}
        while canonical in policy['aliases']:
            if canonical in seen: raise ValueError('alias cycle')
            seen.add(canonical); canonical = policy['aliases'][canonical]


def report(policy):
    counts = collections.Counter(r['disposition'] for r in policy['routes'].values())
    missing = collections.Counter(g for r in policy['routes'].values() for g in r['unresolved'])
    return {'schema': 'better_cooking:catalogue_report.v1', 'qualification': policy['qualification'],
            'foods': len(policy['foods']), 'routes': len(policy['routes']), 'dispositions': dict(sorted(counts.items())),
            'food_roles': dict(sorted(collections.Counter(f['role'] for f in policy['foods'].values()).items())),
            'nonstandard_source_reviewed_count': len(policy.get('nonstandard_consumables', {})),
            'native_cut_rewrite_ids': policy['migration'].get('native_cut_rewrite_ids', []),
            'authored_reviewed_food_count': sum(f['classification'] == 'authored_source_reviewed_pending_native' for f in policy['foods'].values()),
            'authored_reviewed_route_count': sum('authored_review' in r for r in policy['routes'].values()),
            'authored_decisions': policy['provenance'].get('authored_decisions'),
            'unresolved_food_count': sum(bool(f['unresolved']) for f in policy['foods'].values()),
            'unresolved_route_count': sum(bool(r['unresolved']) or r['disposition'] == 'unresolved' for r in policy['routes'].values()),
            'unresolved_food_ids': sorted(item for item, food in policy['foods'].items() if food['unresolved']),
            'unresolved_route_ids': sorted(source for source, route in policy['routes'].items() if route['unresolved'] or route['disposition'] == 'unresolved'),
            'migration_errors': policy['migration']['errors'],
            'canonical_recipe_rewrite_ids': policy['migration']['canonical_bulk_rewrite_ids'],
            'native_unresolved_recipe_ids': policy.get('native_unresolved_recipe_ids', []),
            'acquisition_unresolved_ids': [row['item'] for row in policy['acquisition']['rows'] if row['unresolved']],
            'serving_records': len(policy['servings']),
            'route_gap_counts': dict(sorted(missing.items())), 'gaps': policy['gaps']}


def review_tables(policy):
    foods, routes = io.StringIO(), io.StringIO()
    writer = csv.writer(foods, delimiter='\t', lineterminator='\n')
    writer.writerow(['id', 'role', 'canonical', 'work_class', 'portions_per_item', 'portions_proven', 'hunger_basis', 'hunger', 'saturation_modifier', 'hydration', 'quenching', 'unresolved'])
    for item, food in sorted(policy['foods'].items()):
        writer.writerow([item, food['role'], food['canonical'], food['work_class'], food['servings'], food['serving_metadata']['serving_count_proven'], food['quantity_basis'], food['nutrition']['hunger'], food['nutrition']['saturation_modifier'], food['moisture']['hydration'], food['moisture']['quenching'], ';'.join(food['unresolved'])])
    writer = csv.writer(routes, delimiter='\t', lineterminator='\n')
    writer.writerow(['source_id', 'type', 'disposition', 'surface', 'work_class', 'batch_portions', 'output', 'native_quality_mode', 'native_quality_original_id', 'unresolved'])
    for source, route in sorted(policy['routes'].items()):
        writer.writerow([source, route['source_type'], route['disposition'], route['surface'], route['work_class'], route['servings'], json.dumps(route['output'], sort_keys=True), route['native_quality']['mode'], route['native_quality'].get('source_recipe_id', ''), ';'.join(route['unresolved'])])
    return foods.getvalue(), routes.getvalue()


def _canonical_cli_path(value):
    """Check original spelling BEFORE Path can discard /./ or repeated slashes."""
    try:
        original = os.fspath(value)
        path = Path(original)
        if not isinstance(original, str) or original != str(path) or '..' in path.parts:
            raise ValueError('noncanonical path spelling')
        return path
    except (ValueError, TypeError) as exc:
        raise argparse.ArgumentTypeError('canonical path spelling required') from exc


def _native_output_parent(output, report_path):
    try:
        output, report_path = _canonical_cli_path(output), _canonical_cli_path(report_path)
    except argparse.ArgumentTypeError as exc:
        raise ValueError('native output path spelling must be canonical') from exc
    parent = output.parent
    if not output.is_absolute() or not report_path.is_absolute() or parent != report_path.parent:
        raise ValueError('native outputs require ONE explicit absolute fresh HOME directory')
    try: parent.relative_to('/home/dev')
    except ValueError as exc: raise ValueError('native output outside HOME') from exc
    if '..' in output.parts or '..' in report_path.parts or parent.exists() or parent.is_symlink():
        raise ValueError('native final directory must be fresh and absent')
    for ancestor in (parent.parent, *parent.parent.parents):
        if ancestor.is_symlink(): raise ValueError('native output symlink ancestor')
    if not parent.parent.is_dir() or parent.parent.stat().st_uid != os.getuid():
        raise ValueError('native staging parent must be existing owned directory')
    names = [output.name, report_path.name, report_path.stem + '-foods.tsv', report_path.stem + '-routes.tsv']
    if len(set(names)) != len(names) or 'UNACCEPTED.json' in names:
        raise ValueError('native candidate output names collide')
    return parent


def _publish_native(outputs, output, report_path, *, frozen):
    """Private production commit; origin checked again, Linux NOREPLACE or refuse."""
    _require_input_origin(frozen); require_bound_catalogue(frozen)
    final = _native_output_parent(output, report_path)
    if any(path.parent != final for path, _ in outputs): raise ValueError('multiple native destinations')
    stage = Path(tempfile.mkdtemp(prefix='.' + final.name + '-unaccepted-', dir=final.parent))
    try:
        for path, text in outputs:
            with (stage / path.name).open('x', encoding='utf-8', newline='\n') as stream:
                stream.write(text); stream.flush(); os.fsync(stream.fileno())
        _require_input_origin(frozen)
        # os.rename can replace an existing empty directory. Require Linux's
        # explicit no-replace operation, not a racy exists-check + overwrite.
        libc = ctypes.CDLL(None, use_errno=True)
        rename = getattr(libc, 'renameat2', None)
        if rename is None: raise ValueError('atomic no-replace directory commit unsupported')
        rename.argtypes = [ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint]
        rename.restype = ctypes.c_int
        if rename(-100, os.fsencode(stage), -100, os.fsencode(final), 1) != 0:
            raise OSError(ctypes.get_errno(), 'native no-replace directory commit refused')
    except BaseException:
        if stage.exists():
            try:
                with (stage / 'UNACCEPTED.json').open('x', encoding='utf-8') as stream:
                    stream.write('{"status":"unaccepted","qualification":false}\n')
            except OSError: pass  # Original failure and partial files survive.
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=_canonical_cli_path, default=HERE / 'frozen/catalogue_inputs.json')
    parser.add_argument('--output', type=_canonical_cli_path, required=True)
    parser.add_argument('--overrides', type=Path)
    parser.add_argument('--decisions', type=Path, default=HERE / 'authored/decisions.json', help='Explicit enumerated source reviews; never native qualification')
    parser.add_argument('--nonstandard-consumables', type=_canonical_cli_path, help='Explicit hash-bound non-FoodProperties native-use declarations')
    parser.add_argument('--native-snapshot', type=_canonical_cli_path, help='Fresh v2 observation only; requires independent binding/SHA')
    parser.add_argument('--native-binding', type=_canonical_cli_path)
    parser.add_argument('--native-binding-sha256')
    parser.add_argument('--report', type=_canonical_cli_path, default=HERE / 'reports/catalogue-report.json')
    parser.add_argument('--check', action='store_true', help='Compare deterministic outputs without modifying files')
    args = parser.parse_args()
    native_args = (args.native_snapshot, args.native_binding, args.native_binding_sha256)
    if any(v is not None for v in native_args) and not all(v is not None for v in native_args):
        parser.error('native snapshot, binding and SHA are ALL-or-NONE')
    if args.native_snapshot:
        if args.check: parser.error('native candidate mode forbids --check; fresh unqualified outputs only')
        if not any(v == '--report' or v.startswith('--report=') for v in __import__('sys').argv):
            parser.error('native candidate requires explicit --report')
        _native_output_parent(args.output, args.report)
    # Historical SOURCE CLI accepts canonical relative spellings. Prefix cwd
    # without resolve(): shared reader must still see/reject symlink components.
    source_path = args.input if args.input.is_absolute() else Path.cwd() / args.input
    declaration_path = args.nonstandard_consumables
    if declaration_path is not None and not declaration_path.is_absolute(): declaration_path = Path.cwd() / declaration_path
    frozen = load_source_baseline(source_path, nonstandard_path=declaration_path)
    if args.native_snapshot:
        frozen = load_native(frozen, args.native_snapshot, binding_path=args.native_binding, binding_sha256=args.native_binding_sha256)
    policy = compile_policy(frozen, json.loads(args.overrides.read_text()) if args.overrides else None,
                            json.loads(args.decisions.read_text()) if args.decisions.exists() else None)
    foods_tsv, routes_tsv = review_tables(policy)
    outputs = [(args.output, encoded(policy)), (args.report, encoded(report(policy))),
               (args.report.with_name(args.report.stem + '-foods.tsv'), foods_tsv),
               (args.report.with_name(args.report.stem + '-routes.tsv'), routes_tsv)]
    _require_input_origin(frozen)
    if args.native_snapshot:
        _publish_native(outputs, args.output, args.report, frozen=frozen)
        print(encoded(report(policy)))
        return
    for path, expected in outputs:
        if args.check:
            if not path.exists() or path.read_text() != expected: raise SystemExit('stale generated output: ' + str(path))
        else:
            path.parent.mkdir(parents=True, exist_ok=True); path.write_text(expected)
    print(encoded(report(policy)))


if __name__ == '__main__': main()
