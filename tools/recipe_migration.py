"""Emit exact registered serializer JSON; separate planning from authorization."""
import copy
import math
import re
from policy_rules import BULK_ALIASES


def forge_ingredients(groups):
    result = []
    for group in groups:
        if not group: raise ValueError('empty ingredient group')
        counts = {edge.get('count', 1) for edge in group}
        if len(counts) != 1: raise ValueError('alternative quantities differ')
        count = counts.pop()
        if type(count) is not int or count <= 0: raise ValueError('ingredient count must be positive integer')
        choices = []
        for edge in group:
            if edge.get('kind') not in ('item', 'tag'): raise ValueError('unsupported preparation ingredient')
            choices.append({edge['kind']: edge['id']})
        result.append({'ingredient': choices[0] if len(choices) == 1 else choices, 'count': count})
    return result


def prepaid_steamer_container(route, external, ingredients, finish='steamer'):
    """Move a proven FD per-output carrier cost, never add or waive that cost."""
    if route.get('source_type') != 'farmersdelight:cooking':
        raise ValueError('external_container_source_payment_semantics_require_adapter')
    if not isinstance(external, dict) or set(external) - {'item', 'id', 'count'}:
        raise ValueError('external_container_matching_or_metadata_requires_adapter')
    item = external.get('item', external.get('id'))
    per_output = external.get('count', 1)
    if not isinstance(item, str) or item == 'minecraft:air' or type(per_output) is not int or not 1 <= per_output <= 64:
        raise ValueError('external_container_literal_item_quantity_required')
    if (route.get('native_quality') or {}).get('mode') == 'retained_seeded_flexible_evaluator':
        raise ValueError('external_container_prepaid_requires_fixed_counted_formulation')
    if any(gap in route.get('unresolved', []) for gap in ('native_dynamic_output_requires_adapter', 'native_vessel_output_constant_requires_actual_serving_enumeration')):
        raise ValueError('external_container_actual_output_yield_requires_proof')
    count = route['replacement']['output']['count']
    if type(count) is not int or not 1 <= count <= 64: raise ValueError('external_container_output_count_invalid')
    outputs = route.get('outputs', [])
    if len(outputs) != 1 or outputs[0].get('kind') != 'item' or outputs[0].get('id') != route['replacement']['output']['id'] or outputs[0].get('count') != count:
        raise ValueError('external_container_literal_output_identity_and_quantity_require_source_agreement')
    required = per_output * count
    lowered = copy.deepcopy(ingredients)
    # Identical literal groups may share a counted demand; NEVER subtract an
    # existing bowl ingredient from the independent external-container cost.
    remaining = required
    for group in lowered:
        if group['ingredient'] == {'item': item}:
            moved = min(remaining, 64 - group['count']); group['count'] += moved; remaining -= moved
    while remaining:
        moved = min(remaining, 64); lowered.append({'ingredient': {'item': item}, 'count': moved}); remaining -= moved
    if len(lowered) > 9: raise ValueError('prepaid_container_exceeds_nine_counted_input_groups')
    typed = (route.get('native_semantics') or {}).get('container')
    if isinstance(typed, dict) and (typed.get('id', typed.get('item')) != item or typed.get('count') != per_output):
        raise ValueError('native_external_container_mismatch_requires_adapter')
    return lowered, {'source_recipe_id': route['source_id'], 'source_external_container': copy.deepcopy(external),
        'payment': 'prepaid_counted_preparation_input', 'per_output_carrier_count': per_output,
        'literal_output_items': count, 'total_carrier_count': required,
        'existing_ingredient_costs_not_subtracted': True, 'final_native_steamer_has_no_carrier_debit': finish == 'steamer',
        'finish_surface': finish, 'native_finish_carrier_debit': 'none_after_prepaid_counted_cost',
        'evidence': copy.deepcopy(route.get('evidence', [])), 'status': 'source_count_proof_pending_native_debit_acceptance'}


def recipe_pair(route):
    """Return preparation and optional finish recipe, preserving literal yield.

    This is a serializer-level plan, NOT native apparatus acceptance. Quantity,
    carrier, fluid and NBT handling gaps stay on the route and block installation.
    """
    replacement = route['replacement']
    if not replacement: return []
    if 'authored_adapter_requires_explicit_lowering' in route.get('unresolved', []): raise ValueError('authored_adapter_requires_explicit_lowering')
    if len(route.get('outputs', [])) > 1: raise ValueError('multiple_outputs_need_transactional_adapter')
    if any(out.get('chance', 1) != 1 for out in route.get('outputs', [])): raise ValueError('probabilistic_output_requires_native_adapter')
    surface = replacement['surface']
    finish = replacement.get('finish') or ('baking' if surface in ('baking_mat', 'baking') else surface)
    if finish not in ('board', 'pot', 'stockpot', 'steamer', 'baking', 'roasting', 'teapot'):
        raise ValueError('unsupported finishing surface')
    prep_id = replacement['id']
    output = {'item': replacement['output']['id'], 'count': replacement['output']['count']}
    raw = route.get('original_resource') or {}
    source_type = route.get('source_type') or raw.get('type')
    external = raw.get('carrier', raw.get('container'))
    typed_container = (route.get('native_semantics') or {}).get('container')
    if source_type == 'farmersdelight:cooking' and 'container' not in raw and (
            not isinstance(typed_container, dict) or type(typed_container.get('count')) is not int or typed_container['count'] < 0):
        # FD may derive its output carrier from the result item. JSON field
        # absence is NOT proof of no carrier; the public native getter resolves it.
        raise ValueError('fd_implicit_output_container_requires_native_or_bound_item_remainder_source')
    if source_type == 'farmersdelight:cooking' and 'container' in raw and isinstance(typed_container, dict):
        declared = raw['container']
        if not isinstance(declared, dict) or declared.get('item', declared.get('id')) != typed_container.get('id', typed_container.get('item')) or declared.get('count', 1) != typed_container.get('count'):
            raise ValueError('native_external_container_mismatch_requires_adapter')
    if isinstance(typed_container, dict) and typed_container.get('count', 0) > 0:
        external = external or typed_container
    if finish == 'stockpot' and output['count'] > 9: raise ValueError('stockpot_literal_output_exceeds_native_capacity_9')
    own_stockpot = finish == 'stockpot' and source_type not in ('kaleidoscope_cookery:stockpot', 'kaleidoscope_cookery:flex_stockpot')
    if own_stockpot and (route.get('adapter_reviewed') is not True or route.get('adapter_decision') != 'protected_stockpot_heat_finish'):
        raise ValueError('stockpot_no_original_soup_base_requires_registered_no_extra_cost_backend_and_explicit_reviewed_choice')
    if isinstance(external, dict) and external.get('count', 1) != 1 and finish != 'steamer' and not own_stockpot:
        raise ValueError('counted_output_container_requires_adapter')
    if isinstance(external, dict) and 'nbt' in external:
        raise ValueError('output_container_nbt_requires_matching_and_payment_adapter')
    if finish == 'stockpot':
        if own_stockpot and source_type not in ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless', 'farmersdelight:cooking'):
            raise ValueError('stockpot_original_external_cost_semantics_require_adapter')
    if own_stockpot and source_type == 'farmersdelight:cooking' and not external and (
            not isinstance(typed_container, dict) or type(typed_container.get('count')) is not int or typed_container['count'] != 0):
        raise ValueError('fd_implicit_output_container_requires_native_or_bound_item_remainder_source')
    ingredients = forge_ingredients(replacement['input_groups']); prepaid = None
    if external and (finish == 'steamer' or own_stockpot):
        ingredients, prepaid = prepaid_steamer_container(route, external, ingredients, finish)
    elif external and finish not in ('pot', 'stockpot'):
        raise ValueError('external_output_carrier_requires_prepaid_native_finish_adapter')
    preparation = {
        'type': 'better_cooking:preparation',
        'ingredients': ingredients,
        'result': output,
        'surface': replacement.get('preparation_surface') or ('baking_mat' if finish == 'baking' else 'board'),
        'finish': finish,
        'servings': replacement['servings'], 'work_class': replacement['work_class'],
        'stages': copy.deepcopy(replacement['stages']), 'cook_ticks': replacement['cook_ticks'],
        'native_quality': copy.deepcopy(replacement.get('native_quality', {'mode': 'fixed_standard_unless_inherited_preparation'}))}
    if own_stockpot:
        preparation['stockpot_cost_contract'] = {
            'backend': 'better_cooking:prepared_stockpot', 'source_recipe_type': source_type,
            'source_external_cost': 'prepaid_exact_per_output' if prepaid else (
                'native_getter_explicit_empty' if source_type == 'farmersdelight:cooking' else 'crafting_has_no_separate_output_container_debit'),
            'no_added_water_or_base': True, 'native_acceptance': False}
    if prepaid: preparation['source_external_container'] = prepaid  # audit only; counted ingredients actually enforce debit
    recipes = [{'id': prep_id, 'json': preparation}]
    if finish == 'board': return recipes
    batch = {'type': 'better_cooking:prepared_batch', 'route': prep_id}
    ticks = replacement['cook_ticks']
    if finish in ('baking', 'roasting'):
        if finish == 'roasting' and route.get('cook_ticks_basis') != 'source_declared_or_native_getter':
            raise ValueError('roasting_requires_source_or_authored_cook_duration_not_placeholder')
        native = {'type': 'better_cooking:roasting' if finish == 'roasting' else 'better_cooking:baking',
                  'ingredient': batch, 'result': output, 'experience': 0, 'cookingtime': ticks}
    elif finish == 'steamer':
        native = {'type': 'kaleidoscope_cookery:steamer', 'ingredient': batch,
                  'result': output, 'cook_tick': ticks}
    elif finish == 'teapot':
        native = {'type': 'kaleidoscope_cookery:teapot', 'ingredient': batch,
                  'ingredient_count': 1, 'time': ticks, 'result': output}
    elif own_stockpot:
        native = {'type': 'better_cooking:prepared_stockpot', 'route': prep_id,
                  'result': output, 'time': ticks, 'empty_carrier': True}
    else:
        native = {'type': 'kaleidoscope_cookery:' + finish,
                  'ingredients': [batch], 'time': ticks, 'result': output}
        raw = route.get('original_resource') or {}
        if 'carrier' in raw: native['carrier'] = copy.deepcopy(raw['carrier'])
        elif 'container' in raw: native['carrier'] = copy.deepcopy(raw['container'])
        typed = route.get('native_semantics') or {}
        if 'container' in typed:
            container = typed['container']
            if container.get('count', 0) > 0:
                if container['count'] != 1: raise ValueError('counted_output_container_requires_adapter')
                native['carrier'] = {'item': container['id']}
            elif finish == 'stockpot': native['empty_carrier'] = True
        if finish == 'pot': native['stir_fry_count'] = raw.get('stir_fry_count', 3)
        elif finish == 'stockpot':
            if 'soup_base' in raw: native['soup_base'] = raw['soup_base']
            for key in ('cooking_texture', 'finished_texture', 'cooking_bubble_color', 'finished_bubble_color', 'empty_carrier'):
                if key in raw: native[key] = raw[key]
    recipes.append({'id': prep_id + '/finish', 'json': native})
    return recipes


def canonical_bulk_definition(source, route):
    outputs = route['outputs']
    targets = [o for o in outputs if o.get('kind') == 'item' and o.get('id') in BULK_ALIASES]
    consumers = [e for group in route['input_groups'] for e in group if e.get('kind') == 'item' and e.get('id') in BULK_ALIASES]
    if not targets and (not consumers or route['disposition'] == 'replace'): return None
    raw = copy.deepcopy(route.get('original_resource'))
    if not isinstance(raw, dict): raise ValueError('canonical_bulk_resource_json_missing')
    rewritten = []
    def transform(value):
        if isinstance(value, list): return [transform(member) for member in value]
        if isinstance(value, str):
            if value in BULK_ALIASES:
                rewritten.append({'from': value, 'to': BULK_ALIASES[value], 'count': 1})
                return BULK_ALIASES[value]
        elif isinstance(value, dict) and value.get('item') in BULK_ALIASES:
            old = value['item']; count = value.get('count', 1)
            if type(count) is not int or count < 1: raise ValueError('canonical_bulk_invalid_quantity')
            value['item'] = BULK_ALIASES[old]
            rewritten.append({'from': old, 'to': value['item'], 'count': count})
        return value
    if 'result' in raw: raw['result'] = transform(raw['result'])
    if 'results' in raw: raw['results'] = [transform(value) for value in raw['results']]
    expected = sorted((o['id'], o.get('count', 1)) for o in targets)
    actual = sorted((o['from'], o['count']) for o in rewritten)
    if expected != actual: raise ValueError('canonical_bulk_output_shape_or_quantity_mismatch')
    consumer_rewrites = []
    def ingredient(value):
        if isinstance(value, list):
            converted = []
            for choice in value:
                variant = ingredient(choice)
                converted.extend(variant if isinstance(variant, list) else [variant])
            return converted
        if isinstance(value, dict) and value.get('item') in BULK_ALIASES:
            if set(value) != {'item'}: raise ValueError('canonical_consumer_counted_or_custom_matcher_requires_adapter')
            old = value['item']; canonical = BULK_ALIASES[old]
            consumer_rewrites.append({'from': old, 'accept': canonical, 'count': 1})
            return [value, {'item': canonical}]
        return value
    if consumers and route['disposition'] != 'replace':
        if 'ingredients' in raw:
            if not isinstance(raw['ingredients'], list): raise ValueError('canonical_consumer_ingredients_not_array')
            raw['ingredients'] = [ingredient(value) for value in raw['ingredients']]
        if 'ingredient' in raw: raw['ingredient'] = ingredient(raw['ingredient'])
        if 'key' in raw: raw['key'] = {key: ingredient(value) for key, value in raw['key'].items()}
        if not consumer_rewrites: raise ValueError('canonical_consumer_ingredient_shape_requires_adapter')
    return {'id': source, 'json': raw, 'canonical_rewrites': rewritten, 'canonical_consumer_alternatives': consumer_rewrites}


def native_cutting_definition(source, route):
    """Root-approved native KC-type cut, not a protected-meal work token."""
    review = route.get('authored_review') or {}
    if review.get('serializer_contract_status') != 'root_approved_pending_native':
        raise ValueError('native_cutting_serializer_contract_pending')
    raw = route.get('original_resource')
    if not isinstance(raw, dict): raise ValueError('native_cutting_source_resource_missing')
    if raw.get('conditions') or raw.get('forge:conditions'):
        raise ValueError('native_cutting_resource_conditions_require_proven_emission_path')
    ingredients = raw.get('ingredients')
    if not isinstance(ingredients, list) or len(ingredients) != 1:
        raise ValueError('native_cutting_requires_one_native_input_unit')
    if not isinstance(raw.get('tool'), (dict, list)): raise ValueError('native_cutting_actual_tool_missing')
    results = raw.get('result')
    if not isinstance(results, list) or not 1 <= len(results) <= 64: raise ValueError('native_cutting_results_shape')
    for result in results:
        if not isinstance(result, dict) or set(result) - {'item', 'count', 'chance', 'nbt'} or not isinstance(result.get('item'), str):
            raise ValueError('native_cutting_unreviewed_result_metadata')
        if 'nbt' in result and not isinstance(result['nbt'], (dict, str)): raise ValueError('native_cutting_result_nbt')
        count, chance = result.get('count', 1), result.get('chance', 1)
        if type(count) is not int or not 1 <= count <= 64: raise ValueError('native_cutting_result_count')
        if type(chance) not in (int, float) or not math.isfinite(chance) or not 0 <= chance <= 1: raise ValueError('native_cutting_result_chance')
    if review.get('native_cut_count') != 1: raise ValueError('native_cutting_unreviewed_tool_action_count')
    return {'id': source, 'json': {'type': 'better_cooking:cutting', 'ingredient': copy.deepcopy(ingredients[0]),
             'tool': copy.deepcopy(raw['tool']), 'results': copy.deepcopy(results), 'cut_count': 1, 'source_recipe_id': source},
            'native_bulk_cut': True, 'source_function': review.get('cut_function')}


def validate_native_plan(policy):
    """Independent fail-closed barrier for every planner branch and prebuilt plan."""
    partition = policy.get('native_observation_partition')
    provenance = policy.get('provenance') or {}
    marked = (partition is not None or 'native_observation_partition' in policy or
              'native_observation_provenance' in policy or bool(policy.get('native_unresolved_recipe_ids')) or
              any(provenance.get(k) is not None for k in ('native_snapshot_sha256', 'native_snapshot_raw_sha256')))
    if not marked: return  # Pure historical/source planner models are not qualification validation.
    if policy.get('qualification') == 'qualified': raise ValueError('no NativeAcceptance authority for qualified native plan')
    if not isinstance(partition, dict): raise ValueError('missing native v2 partition')
    required = set('schema raw_sha256 raw_bytes binding_sha256 contract_sha256 invocation_nonce source_baseline_sha256 recipe_count ledger admitted_recipe_ids quarantined_recipe_ids source_recipe_ids source_only_unobserved_recipe_ids backend_authority qualification'.split())
    if set(partition) != required or partition['schema'] != 'better_cooking:native_observation_partition.v2':
        raise ValueError('invalid native v2 partition shape')
    for key in ('raw_sha256', 'binding_sha256', 'contract_sha256', 'source_baseline_sha256'):
        if not isinstance(partition[key], str) or not re.fullmatch('[0-9a-f]{64}', partition[key]): raise ValueError('invalid native partition SHA')
    if type(partition['raw_bytes']) is not int or not 0 <= partition['raw_bytes'] <= 67108864: raise ValueError('invalid native partition raw size')
    if not isinstance(partition['invocation_nonce'], str) or not re.fullmatch(r'(?:[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})', partition['invocation_nonce']): raise ValueError('invalid native partition nonce')
    if partition['qualification'] is not False or partition['backend_authority'] != 'empty_pending_exact_contracts' or partition['admitted_recipe_ids'] != []:
        raise ValueError('native backend authority is empty')
    ledger, ids = partition['ledger'], partition['quarantined_recipe_ids']
    if not isinstance(ledger, list) or not isinstance(ids, list) or type(partition['recipe_count']) is not int or partition['recipe_count'] != len(ledger):
        raise ValueError('invalid quarantine counts')
    for key in ('quarantined_recipe_ids', 'source_recipe_ids', 'source_only_unobserved_recipe_ids'):
        values = partition[key]
        if not isinstance(values, list) or any(not isinstance(v, str) or not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', v) for v in values) or values != sorted(set(values)):
            raise ValueError('invalid sorted unique native/source IDs')
    if len(ids) != len(ledger): raise ValueError('lost/duplicate quarantine rows')
    if partition['source_only_unobserved_recipe_ids'] != sorted(set(partition['source_recipe_ids']) - set(ids)):
        raise ValueError('invalid archival source-minus-live partition')
    for ordinal, row in enumerate(ledger):
        if (not isinstance(row, dict) or set(row) != {'ordinal', 'id', 'type_ref', 'serializer_ref', 'classification', 'reasons'} or
                type(row['ordinal']) is not int or row['ordinal'] != ordinal or row['id'] != ids[ordinal] or
                row['classification'] != 'quarantined' or not isinstance(row['reasons'], list) or
                any(not isinstance(reason, str) for reason in row['reasons']) or
                row['reasons'] != sorted(set(row['reasons'])) or 'backend_contract_not_approved' not in row['reasons']):
            raise ValueError('invalid quarantine ledger')
        for key, prefix in (('type_ref', 't'), ('serializer_ref', 's')):
            if row[key] is not None and (not isinstance(row[key], str) or not re.fullmatch(prefix + r'(?:0|[1-9][0-9]*)', row[key])): raise ValueError('invalid quarantine identity reference')
        reasons = {'backend_contract_not_approved', 'recipe_type_missing', 'recipe_type_unknown', 'recipe_type_owner_keys_absent', 'recipe_type_active_binding_mismatch', 'recipe_type_active_binding_not_sampled', 'serializer_missing', 'serializer_unknown', 'serializer_owner_key_absent', 'serializer_active_binding_mismatch', 'serializer_active_binding_not_sampled'}
        if any(not isinstance(reason, str) or reason not in reasons for reason in row['reasons']): raise ValueError('unknown quarantine reason')
    if policy.get('native_unresolved_recipe_ids') != ids: raise ValueError('native unresolved IDs differ from partition')
    if policy.get('routes') != {}: raise ValueError('native empty authority forbids ALL effective routes')
    migration = policy.get('migration')
    if migration is not None:
        for key in ('recipes', 'remove_ids', 'canonical_bulk_rewrite_ids', 'native_cut_rewrite_ids', 'unresolved_source_ids'):
            if migration.get(key) != []: raise ValueError('native quarantine collides with precomputed plan/removals')


def build_plan(policy):
    validate_native_plan(policy)
    recipes, removals, ids, errors = [], [], set(), []
    bulk_rewrites, native_cuts = [], []
    for source, route in sorted(policy['routes'].items()):
        if route.get('adapter_decision') == 'native_bulk_cut':
            try:
                definition = native_cutting_definition(source, route)
                if source in ids: raise ValueError('duplicate native cutting recipe ID')
                ids.add(source); recipes.append(definition); native_cuts.append(source)
            except ValueError as exc: errors.append({'source_id': source, 'error': str(exc)})
            continue
        try:
            bulk = canonical_bulk_definition(source, route)
        except ValueError as exc:
            errors.append({'source_id': source, 'error': str(exc)})
            bulk = None
        if bulk:
            if bulk['id'] in ids: raise ValueError('duplicate canonical rewrite ID')
            ids.add(bulk['id']); recipes.append(bulk); bulk_rewrites.append(source)
        if route['disposition'] != 'replace': continue
        try:
            definitions = recipe_pair(route)
        except ValueError as exc:
            errors.append({'source_id': source, 'error': str(exc)})
            continue
        for definition in definitions:
            if definition['id'] in ids: raise ValueError('duplicate generated recipe ID')
            ids.add(definition['id']); recipes.append(definition)
        if route['replaces'] != [source]: raise ValueError('migration is not exact-ID replacement')
        removals.append(source)
    result = {'schema': 'better_cooking:recipe_migration.v1',
              'qualification': policy['qualification'], 'recipes': recipes, 'remove_ids': removals, 'errors': errors,
              'canonical_bulk_rewrite_ids': bulk_rewrites,
              'native_cut_rewrite_ids': native_cuts,
              'unresolved_source_ids': [source for source, route in sorted(policy['routes'].items()) if route['disposition'] == 'replace' and route['unresolved']]}
    validate_native_plan(dict(policy, migration=result))
    return result
