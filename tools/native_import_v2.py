"""Strict v2 observation validation and ALL-row quarantine, not backend authority.

validate_snapshot/derive_partition return UNTRUSTED data. Only the Root binding
capability plus pinned SourceBaseline can produce a bound imported catalogue.
"""
from collections.abc import Mapping
import copy
from decimal import Decimal
from datetime import datetime, timedelta
import hashlib
import re
import uuid
from extract_catalogue import encoded
from source_input import require_source_baseline
from native_json import MAX_DEPTH, MAX_NODES

SCHEMA = 'better_cooking:native_catalogue.v2'
PARTITION_SCHEMA = 'better_cooking:native_observation_partition.v2'
NBT_BASIS = 'item_getTag_snbt_only_full_saved_stack_and_ForgeCaps_not_observed'
ROOT_KEYS = set('schema status created_utc qualification discovery_only food_tags_orientation context resource_inputs recipe_identity recipe_stack_nbt_basis importable registered_items block_items item_tags food_tags mods foods recipes gaps native_consumables native_drink_ingredients native_drink_ingredients_context native_consumables_context native_fd_carriers native_fd_feasts'.split())
RECIPE_REQUIRED = {'id', 'recipe_class', 'native_recipe_type', 'native_serializer'}
RECIPE_OPTIONAL = set('ingredients result results resource_json resource_json_context cook_ticks experience container tool cut_count native_ingredient results_semantics stir_fry_count carrier soup_base subtype tea_fluid ingredient_count native_output_count_constant'.split())
ID = re.compile(r'[a-z0-9_.-]+:[a-z0-9_./-]+')
_UUID = re.compile(r'(?:[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})')
_ARRAY = (list, tuple)


def identifier(value, field='ID'):
    if not isinstance(value, str) or not ID.fullmatch(value): raise ValueError('invalid native ' + field)


def _text(value, field):
    if not isinstance(value, str) or not value: raise ValueError('invalid native ' + field)


def _keys(value, required, optional=(), field='object'):
    if not isinstance(value, Mapping) or not set(required) <= set(value) or set(value) - set(required) - set(optional):
        raise ValueError('invalid native ' + field + ' fields')


def _array(value, field):
    if not isinstance(value, _ARRAY): raise ValueError('native ' + field + ' must be array')


def _int(value, field, minimum=None, maximum=None):
    if type(value) is not int or (minimum is not None and value < minimum) or (maximum is not None and value > maximum):
        raise ValueError('invalid native integer ' + field)


def _number(value, field, minimum=None, maximum=None):
    if type(value) not in (int, Decimal) or (isinstance(value, Decimal) and not value.is_finite()):
        raise ValueError('invalid exact native number ' + field)
    if (minimum is not None and value < minimum) or (maximum is not None and value > maximum):
        raise ValueError('invalid native bounds/probability ' + field)


def _bool(value, field):
    if type(value) is not bool: raise ValueError('invalid native boolean ' + field)


def canonical_uuid(value):
    if not isinstance(value, str) or not _UUID.fullmatch(value): raise ValueError('invalid canonical UUID')
    return uuid.UUID(value).hex


def _stack(value, field):
    if value is None: return
    _keys(value, {'id', 'count'}, {'nbt', 'chance'}, field)
    identifier(value['id'], field + ' ID'); _int(value['count'], field + ' count', 0, 2147483647)
    if 'nbt' in value and value['nbt'] is not None:
        if not isinstance(value['nbt'], str): raise ValueError('invalid native tag SNBT')
    if 'chance' in value and value['chance'] is not None: _number(value['chance'], field + ' chance', 0, 1)


def _nutrients(value, field, bounded=False, ordinary=False):
    if value is None: return
    if not isinstance(value, Mapping): raise ValueError('native ' + field + ' must be explicit nutrient map/null')
    for key, number in value.items():
        _text(key, 'nutrient group')
        if ordinary and key not in ('proteins', 'grains', 'fruits', 'fats', 'dairy', 'vegetables'):
            raise ValueError('paid ordinary nutrients duplicate Spice or unknown group')
        _number(number, field + '/' + key, 0 if bounded else None, 10000 if bounded else None)


def _effects(values, chance='chance', food_item=None):
    _array(values, 'effects')
    for effect in values:
        if not isinstance(effect, Mapping): raise ValueError('native effect object required')
        identifier(effect.get('id'), 'effect ID')
        _int(effect.get('duration'), 'effect duration', 0)
        _int(effect.get('amplifier'), 'effect amplifier', 0)
        value = effect.get(chance)
        if food_item is not None:
            # FoodProperties stores the literal nextFloat() comparison threshold, not a
            # percentage. Only this independently source-reviewed Hexerei leaf exceeds 1.
            # Keep 100.0 unchanged; do not infer a paid effect or skip the native RNG draw.
            _number(value, 'food effect comparison threshold', 0)
            if value > 1 and not (food_item == 'hexerei:belladonna_berries' and
                                  effect['id'] == 'minecraft:poison' and
                                  type(value) is Decimal and value == Decimal('100.0') and
                                  effect['duration'] == 100 and effect['amplifier'] == 2):
                raise ValueError('unreviewed food effect probability/comparison threshold')
        else:
            _number(value, 'effect probability', 0, 1)


def _food_properties(value, nutrition='hunger'):
    if not isinstance(value, Mapping): raise ValueError('native food properties object required')
    _int(value.get(nutrition), nutrition)
    _number(value.get('saturation_modifier'), 'saturation_modifier')


def _explained(snapshot, item, field):
    return any(isinstance(g, Mapping) and g.get('scope') in ('food', field) and
               g.get('id') in (item, '*', 'all') and g.get('field', field) == field for g in snapshot['gaps'])


def _validate_nonrecipes(s, source=None):
    for key in ('registered_items', 'foods', 'mods', 'gaps', 'native_consumables', 'native_drink_ingredients'):
        _array(s[key], key)
    for key in ('context', 'resource_inputs', 'block_items', 'item_tags', 'food_tags',
                'native_drink_ingredients_context', 'native_consumables_context', 'native_fd_carriers', 'native_fd_feasts'):
        if not isinstance(s[key], Mapping): raise ValueError('native ' + key + ' must be object')
    if s['context'].get('player') is not None: canonical_uuid(s['context']['player'])
    for item in s['registered_items']: identifier(item, 'registered item ID')
    if len(set(s['registered_items'])) != len(s['registered_items']): raise ValueError('duplicate registered item')
    if s['food_tags_orientation'] != 'tag_to_items': raise ValueError('invalid food tag orientation')
    for name in ('item_tags', 'food_tags'):
        for tag, members in s[name].items():
            identifier(tag, 'tag ID'); _array(members, 'tag members')
            for item in members: identifier(item, 'tag member')
            if len(set(members)) != len(members): raise ValueError('duplicate native tag member')
    for row in s['mods']:
        _keys(row, {'id', 'version'}, field='mod'); _text(row['id'], 'mod id'); _text(row['version'], 'mod version')
    seen = set()
    for row in s['foods']:
        if not isinstance(row, Mapping): raise ValueError('native food object required')
        item = row.get('id'); identifier(item, 'food ID')
        if item in seen: raise ValueError('duplicate native food ID')
        seen.add(item); _food_properties(row)
        for field in ('meat', 'always_eat', 'fast'):
            if field in row: _bool(row[field], field)
        for field in ('effects', 'nutrients'):
            if row.get(field) is None and not _explained(s, item, field):
                raise ValueError('missing native ' + field + ' without matching food gap')
        if row.get('effects') is not None: _effects(row['effects'], food_item=item)
        _nutrients(row.get('nutrients'), 'nutrients')
        for field in ('sugar', 'alcohol', 'spice'):
            if row.get(field) is not None: _number(row[field], field)
    for item, block in s['block_items'].items():
        identifier(item, 'block item ID')
        if not isinstance(block, Mapping): raise ValueError('native block must be object')
        identifier(block.get('block_id'), 'block ID'); _text(block.get('block_class'), 'block class')
        for key in ('native_max_bites', 'servings'):
            if key in block: _int(block[key], key, 1)
        if 'native_bites_values' in block:
            _array(block['native_bites_values'], 'bite domain')
            for value in block['native_bites_values']: _int(value, 'bite value', 0)
        if 'native_per_bite_food' in block: _food_properties(block['native_per_bite_food'])
    selectors = (source or {}).get('nonstandard_consumables', {}).get('ingredient_inputs', {})
    allowed = set(selectors.get('exact_item_ids', []))
    for tag in selectors.get('live_item_tags', []): allowed.update(s['item_tags'].get(tag, ()))
    context = s['native_drink_ingredients_context']
    if (context.get('schema') != 'better_cooking:native_drink_ingredients.v2' or
            context.get('status') != 'observed_getters_unbound_runtime' or
            context.get('qualification') is not False or context.get('auto_install') is not False):
        raise ValueError('native drink input requires unqualified v2 collector context')
    seen = set()
    for row in s['native_drink_ingredients']:
        _keys(row, set('id observation_kind template_basis input_template item_class is_edible food_properties nutrients sugar alcohol spice paid_intake gaps'.split()), field='drink input exact 13')
        item = row['id']; identifier(item, 'drink input ID')
        if item in seen: raise ValueError('duplicate native drink input')
        seen.add(item)
        if source is not None and item not in allowed: raise ValueError('native drink input not explicit/live selected')
        basis = 'provider_purity3_variant' if item == 'minecraft:water_bucket' else 'native_default'
        if row['template_basis'] != basis or row['observation_kind'] != 'observed_template': raise ValueError('invalid template_basis/observation_kind')
        _text(row['input_template'], 'input template SNBT')
        if len(row['input_template']) > 65536: raise ValueError('native template SNBT cap')
        _text(row['item_class'], 'input class'); _bool(row['is_edible'], 'input edible')
        if not isinstance(row['gaps'], Mapping): raise ValueError('input gaps must be object')
        if row['food_properties'] is not None: _food_properties(row['food_properties'])
        _nutrients(row['nutrients'], 'raw input nutrients', bounded=True)
        for field in ('sugar', 'alcohol', 'spice'):
            if row[field] is not None: _number(row[field], 'raw input ' + field, 0, 10000)
        paid = row['paid_intake']
        if not isinstance(paid, Mapping): raise ValueError('paid intake object required')
        _bool(paid.get('complete'), 'paid complete')
        fields = {'complete', 'nutrients', 'sugar', 'alcohol', 'spice'}
        if not paid['complete']: fields.add('reason')
        _keys(paid, fields, field='paid intake')
        if paid['complete']:
            if paid['nutrients'] is None: raise ValueError('complete paid intake requires explicit nutrient map')
            _nutrients(paid['nutrients'], 'paid nutrients', bounded=True, ordinary=True)
            for field in ('sugar', 'alcohol', 'spice'): _number(paid[field], 'complete paid ' + field, 0, 10000)
        else:
            _text(paid['reason'], 'unknown paid reason')
            if any(paid[k] is not None for k in ('nutrients', 'sugar', 'alcohol', 'spice')): raise ValueError('unknown paid fields require literal null')
    consumable_context = s['native_consumables_context']
    if (consumable_context.get('qualification') is not False or consumable_context.get('observed_finish') is not False or
            consumable_context.get('status') != 'discovery_getters_only'):
        raise ValueError('native consumables context cannot assert qualification/Finish')
    seen = set()
    for row in s['native_consumables']:
        if not isinstance(row, Mapping): raise ValueError('native consumable object required')
        item = row.get('id'); identifier(item, 'consumable ID')
        if item in seen: raise ValueError('duplicate native consumable')
        seen.add(item)
        for field in ('item_class', 'use_animation', 'remainder'): _text(row.get(field), field)
        identifier(row['remainder'], 'remainder'); _bool(row.get('is_edible'), 'consumable edible')
        _int(row.get('use_duration_ticks'), 'consumable use duration', 0)
        if 'food_properties' not in row: raise ValueError('missing consumable food_properties')
        if row['food_properties'] is not None: _food_properties(row['food_properties'])
        if 'native_effect_declarations' in row: _effects(row['native_effect_declarations'], 'probability')
        if row.get('source_only') is not None:
            if not isinstance(row['source_only'], Mapping) or row['source_only'].get('observed_finish') is not False:
                raise ValueError('source-only declaration cannot assert observed Finish')
        _nutrients(row.get('nutrients'), 'consumable nutrients')
        for field in ('sugar', 'alcohol', 'spice'):
            if row.get(field) is not None: _number(row[field], 'consumable ' + field, 0, 10000)
        definition = (source or {}).get('nonstandard_consumables', {}).get('consumables', {}).get(item)
        if definition:
            for field in ('item_class', 'is_edible', 'food_properties', 'use_animation', 'use_duration_ticks', 'remainder'):
                if row[field] != definition[field]: raise ValueError('consumable getter/source mismatch: ' + field)
            if 'native_effect_declarations' in row:
                actual = [{**{k: e[k] for k in ('id', 'duration', 'amplifier')}, 'chance': e['probability']} for e in row['native_effect_declarations']]
                def source_numbers(value):
                    if type(value) is float: return Decimal(str(value))
                    if isinstance(value, Mapping): return {k: source_numbers(v) for k, v in value.items()}
                    if isinstance(value, _ARRAY): return [source_numbers(v) for v in value]
                    return value
                if actual != source_numbers(definition['source_selected_effects']): raise ValueError('native declared effects differ from reviewed source')
    # Full-SNBT appender observations stay separate and unqualified; no carrier,
    # feast, provider, paid-dose or servings getter is installed into source facts.
    for section_name in ('native_fd_carriers', 'native_fd_feasts'):
        section = s[section_name]
        if 'schema_version' in section: _int(section['schema_version'], section_name + ' schema', 1, 1)
        for count_key in ('recipe_count', 'block_item_count'):
            if section.get(count_key) is not None:
                _int(section[count_key], section_name + ' count', 0)
                if not isinstance(section.get('rows'), _ARRAY) or section[count_key] != len(section['rows']): raise ValueError('appender count/rows mismatch')
        if section.get('qualification') is not None and section['qualification'] != 'unqualified_getter_facts_only':
            raise ValueError('appender cannot assert qualification')
        if 'rows' not in section: raise ValueError('missing ' + section_name + ' rows')
        if section['rows'] is None:
            if section.get('status') not in ('unobserved', 'error_unresolved'): raise ValueError('unknown appender rows without status')
            continue
        _array(section['rows'], section_name + ' rows')
        for row in section['rows']:
            if not isinstance(row, Mapping): raise ValueError('appender row object required')
            for key in ('observed_serve', 'observed_serving_or_intake'):
                if key in row and row[key] is not False: raise ValueError('getter cannot assert observed serving/intake')
            for key in ('result', 'resolved_container', 'default_state_serving_item'):
                stack = row.get(key)
                if stack is not None:
                    _keys(stack, {'empty', 'item', 'count', 'stack_nbt_snbt'}, field='full saved stack')
                    _bool(stack['empty'], 'stack empty'); identifier(stack['item'], 'saved item'); _int(stack['count'], 'saved count', 0, 2147483647)
                    _text(stack['stack_nbt_snbt'], 'full stack SNBT')
            for key in ('default_remaining', 'max_servings', 'source_carrier_units_per_output_when_native_valid'):
                if row.get(key) is not None: _int(row[key], key, 0)
            if row.get('carrier_debit_per_output_item') is not None: raise ValueError('getter has no observed carrier debit')
            if row.get('servings_property_domain') is not None:
                _array(row['servings_property_domain'], 'servings property domain')
                for value in row['servings_property_domain']: _int(value, 'servings property value', 0)
            if row.get('serving_food_properties') is not None: _food_properties(row['serving_food_properties'], 'nutrition')


def _nodes(nodes, prefix):
    _array(nodes, 'identity nodes'); result = {}
    apis = {'builtin', 'forge'} if prefix == 't' else {'forge'}
    for ordinal, node in enumerate(nodes):
        _keys(node, {'ref', 'class', 'first_recipe_id', 'state', 'registered_key', 'lookups', 'active_binding'}, field='identity node')
        if node['ref'] != prefix + str(ordinal): raise ValueError('duplicate/holey/wrong-table node ref')
        _text(node['class'], 'identity class'); identifier(node['first_recipe_id'], 'first recipe ID')
        _keys(node['lookups'], apis, field='identity lookups'); _keys(node['active_binding'], apis, field='active binding')
        keys, absent = [], 0
        for api in sorted(apis):
            lookup = node['lookups'][api]; _keys(lookup, {'outcome', 'key', 'error'}, field='lookup')
            outcome, key, error = lookup['outcome'], lookup['key'], lookup['error']
            if outcome not in ('key', 'absent', 'not_sampled', 'error'): raise ValueError('invalid lookup outcome')
            if outcome == 'key': identifier(key, 'registry owner key'); keys.append(key)
            elif key is not None: raise ValueError('nonkey lookup must retain null key')
            if outcome == 'absent': absent += 1
            if outcome == 'error': _text(error, 'lookup exception')
            elif error is not None: raise ValueError('nonerror lookup must retain null error')
            active = node['active_binding'][api]
            if outcome == 'key':
                if active not in ('same_object', 'mismatch'): raise ValueError('nonnull owner requires actual active roundtrip')
            elif active != 'not_sampled': raise ValueError('null owner cannot prove active same object')
        state = ('registered' if len(keys) == len(apis) and len(set(keys)) == 1 else
                 'actually_unregistered' if absent == len(apis) else 'unknown')
        expected_key = keys[0] if state == 'registered' else None
        if node['state'] != state or node['registered_key'] != expected_key: raise ValueError('contradictory derived identity state/key')
        result[node['ref']] = node
    return result


def _check_json_tree(root):
    nodes = 0
    def visit(value, depth):
        nonlocal nodes
        nodes += 1
        if depth > MAX_DEPTH or nodes > MAX_NODES: raise ValueError('native structural depth/node cap')
        if isinstance(value, Mapping):
            for key, child in value.items():
                if not isinstance(key, str) or re.search('[\ud800-\udfff]', key): raise ValueError('invalid native Unicode object key')
                visit(child, depth + 1)
        elif isinstance(value, _ARRAY):
            for child in value: visit(child, depth + 1)
        elif isinstance(value, str):
            if re.search('[\ud800-\udfff]', value): raise ValueError('invalid native Unicode string')
        elif value is None or type(value) in (bool, int): pass
        elif type(value) is Decimal and value.is_finite(): pass
        else: raise ValueError('unsupported/nonfinite native JSON scalar')
    visit(root, 0)


def validate_snapshot(snapshot, source=None):
    """UNTRUSTED structural check. Does not bind, publish, normalize or qualify."""
    _check_json_tree(snapshot)
    if not isinstance(snapshot, Mapping) or snapshot.get('schema') != SCHEMA: raise ValueError('wrong native snapshot schema; v2 only')
    _keys(snapshot, ROOT_KEYS, field='root')
    if (snapshot['status'] != 'observed_unqualified' or snapshot['qualification'] is not False or
            snapshot['importable'] is not True or snapshot['discovery_only'] is not True or snapshot['recipe_stack_nbt_basis'] != NBT_BASIS):
        raise ValueError('invalid unqualified v2 envelope')
    _text(snapshot['created_utc'], 'created UTC')
    if not re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]+)?(?:Z|\+00:00)', snapshot['created_utc']):
        raise ValueError('invalid native created UTC')
    try:
        if datetime.fromisoformat(snapshot['created_utc'].replace('Z', '+00:00')).utcoffset() != timedelta(0):
            raise ValueError('native created UTC must be aware UTC')
    except (ValueError, OverflowError) as exc:
        raise ValueError('invalid native created UTC') from exc
    _validate_nonrecipes(snapshot, source)
    identity = snapshot['recipe_identity']
    _keys(identity, {'contract', 'scope', 'invocation_nonce', 'type_objects', 'serializer_objects'}, field='recipe identity')
    if identity['contract'] != 'better_cooking:recipe_identity_observation.v2' or identity['scope'] != 'one_collect_invocation_only':
        raise ValueError('invalid identity contract/scope')
    canonical_uuid(identity['invocation_nonce'])
    tables = {'native_recipe_type': _nodes(identity['type_objects'], 't'), 'native_serializer': _nodes(identity['serializer_objects'], 's')}
    seen, used = set(), {key: {} for key in tables}
    _array(snapshot['recipes'], 'recipes')
    previous = None
    for row in snapshot['recipes']:
        _keys(row, RECIPE_REQUIRED, RECIPE_OPTIONAL, 'recipe')
        item = row['id']; identifier(item, 'recipe ID'); _text(row['recipe_class'], 'recipe class')
        if item in seen: raise ValueError('duplicate native recipe ID')
        if previous is not None and item <= previous: raise ValueError('recipes must be sorted by unique recipe ID')
        seen.add(item); previous = item
        for field, table in tables.items():
            obs = row[field]; _keys(obs, {'getter', 'state', 'ref'}, field=field)
            if obs['getter'] == 'returned_null':
                if obs['state'] != 'missing' or obs['ref'] is not None: raise ValueError('contradictory missing getter')
            elif obs['getter'] == 'returned_object':
                ref = obs['ref']
                if not isinstance(ref, str) or ref not in table or obs['state'] != table[ref]['state']: raise ValueError('dangling/cross-table/contradictory observation ref')
                if ref not in used[field]:
                    if table[ref]['first_recipe_id'] != item or ref != ('t' if field == 'native_recipe_type' else 's') + str(len(used[field])):
                        raise ValueError('identity first-reference order mismatch')
                    used[field][ref] = item
            else: raise ValueError('invalid identity getter')
        for field in ('result', 'container'):
            if field in row: _stack(row[field], field)
        # KC carrier().toJson(), like tool/native_ingredient, is Ingredient
        # context, NOT an ItemStack observation. Never invent id/count for it.
        if row.get('results') is not None:
            _array(row['results'], 'results')
            for output in row['results']: _stack(output, 'secondary result')
        if row.get('ingredients') is not None: _array(row['ingredients'], 'ingredients')
        for field in ('cut_count', 'stir_fry_count', 'ingredient_count', 'native_output_count_constant'):
            if row.get(field) is not None: _int(row[field], field, 1 if field == 'native_output_count_constant' else 0)
        if row.get('cook_ticks') is not None: _int(row['cook_ticks'], 'cook_ticks')
        if row.get('experience') is not None: _number(row['experience'], 'experience')
        for field in ('resource_json_context', 'results_semantics', 'subtype', 'soup_base', 'tea_fluid'):
            if row.get(field) is not None: _text(row[field], field)
    for field, table in tables.items():
        if set(used[field]) != set(table): raise ValueError('unreferenced identity node')
    return snapshot


def _reasons(snapshot, row):
    result = {'backend_contract_not_approved'}
    for field, prefix, tablekey, absent in (
            ('native_recipe_type', 'recipe_type', 'type_objects', 'owner_keys_absent'),
            ('native_serializer', 'serializer', 'serializer_objects', 'owner_key_absent')):
        obs = row[field]; state = obs['state']
        if state == 'missing': result.add(prefix + '_missing'); continue
        node = snapshot['recipe_identity'][tablekey][int(obs['ref'][1:])]
        if state == 'unknown': result.add(prefix + '_unknown')
        if state == 'actually_unregistered': result.add(prefix + '_' + absent)
        for status in set(node['active_binding'].values()):
            if status in ('mismatch', 'not_sampled'): result.add(prefix + '_active_binding_' + status)
    return sorted(result)


def derive_partition(snapshot, provenance, baseline):
    """UNTRUSTED deterministic projection; a matching dict is NOT a capability."""
    validate_snapshot(snapshot, baseline)
    source_ids = sorted(baseline['recipes']); live = [r['id'] for r in snapshot['recipes']]
    return {'schema': PARTITION_SCHEMA, 'raw_sha256': provenance['raw_sha256'], 'raw_bytes': provenance['raw_bytes_count'],
            'binding_sha256': provenance['binding_sha256'], 'contract_sha256': provenance['contract_sha256'],
            'invocation_nonce': provenance['invocation_nonce'], 'source_baseline_sha256': hashlib.sha256(encoded(baseline).encode()).hexdigest(),
            'recipe_count': len(live), 'ledger': [dict(ordinal=i, id=r['id'], type_ref=r['native_recipe_type']['ref'], serializer_ref=r['native_serializer']['ref'], classification='quarantined', reasons=_reasons(snapshot, r)) for i, r in enumerate(snapshot['recipes'])],
            'admitted_recipe_ids': [], 'quarantined_recipe_ids': live, 'source_recipe_ids': source_ids,
            'source_only_unobserved_recipe_ids': sorted(set(source_ids) - set(live)),
            'backend_authority': 'empty_pending_exact_contracts', 'qualification': False}


def _materialize(value):
    if isinstance(value, Mapping): return {k: _materialize(v) for k, v in value.items()}
    if isinstance(value, _ARRAY): return [_materialize(v) for v in value]
    return value


def _bound_api():
    secret = object()

    class BoundCatalogue(dict):
        __slots__ = ('_binding', '_baseline', '_public_sha')
        def __init__(self, model, binding, baseline, token):
            if token is not secret: raise ValueError('private bound catalogue constructor')
            super().__init__(model); self._binding = binding; self._baseline = baseline
            self._public_sha = hashlib.sha256(encoded(model).encode()).hexdigest()
        def __copy__(self): return dict(self)
        def __deepcopy__(self, memo): return copy.deepcopy(dict(self), memo)

    def require_bound_catalogue(value):
        if type(value) is not BoundCatalogue: raise ValueError('genuine bound catalogue required')
        from native_observation_binding import require_binding
        cap = require_binding(value._binding)
        if hashlib.sha256(encoded(value).encode()).hexdigest() != value._public_sha: raise ValueError('native-derived catalogue content changed')
        partition = derive_partition(cap.snapshot, cap.provenance, value._baseline)
        if value.get('native_observation_partition') != partition or value.get('recipes') != {}: raise ValueError('altered/missing partition or effective recipes')
        if value.get('native_source_recipe_baseline') != value._baseline['recipes']: raise ValueError('altered archival source baseline')
        return value

    def admit(frozen, snapshot, binding):
        require_source_baseline(frozen)
        from native_observation_binding import require_binding
        cap = require_binding(binding)
        if snapshot is not cap.snapshot: raise ValueError('native snapshot must be capsule snapshot object')
        if canonical_uuid(snapshot['recipe_identity']['invocation_nonce']) != canonical_uuid(cap.provenance['invocation_nonce']): raise ValueError('nonce/capsule mismatch')
        baseline = copy.deepcopy(dict(frozen))
        partition = derive_partition(snapshot, cap.provenance, baseline)
        result = copy.deepcopy(baseline); result['recipes'] = {}
        result['native_source_recipe_baseline'] = copy.deepcopy(baseline['recipes'])
        result['native_observation_partition'] = partition
        result['native_observation_provenance'] = _materialize(cap.provenance)
        result['native_unresolved_recipes'] = [dict(id=r['id'], raw_sha256=partition['raw_sha256'], ordinal=r['ordinal'], reasons=r['reasons'][:]) for r in partition['ledger']]
        result['native_import_gaps'] = [dict(code='native_recipe_quarantined', **row) for row in result['native_unresolved_recipes']]
        result['native_import_gaps'] += [dict(code='source_not_observed', id=item) for item in partition['source_only_unobserved_recipe_ids']]
        # Native food/drink/tags/servings/provider facts remain ONLY in capsule.
        # Deliberately NO resource_recipe/nonstandard/v1 import mutation helper.
        return require_bound_catalogue(BoundCatalogue(result, cap, baseline, secret))

    return admit, require_bound_catalogue


admit_bound, require_bound_catalogue = _bound_api()
del _bound_api


def get_observed_recipe(catalogue, raw_sha256, ordinal):
    """Read-only lossless retrieval by exact raw identity/ordinal; never admission."""
    require_bound_catalogue(catalogue)
    cap = catalogue._binding
    if raw_sha256 != cap.provenance['raw_sha256']: raise ValueError('observed recipe raw SHA mismatch')
    _int(ordinal, 'recipe ordinal', 0)
    if ordinal >= len(cap.snapshot['recipes']): raise ValueError('observed recipe ordinal out of range')
    return cap.snapshot['recipes'][ordinal]
