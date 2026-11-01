"""Explicit non-FoodProperties consumables; source declarations are not live getters."""
import copy
import hashlib
import json
import math
import re
from pathlib import Path

SCHEMA = 'better_cooking:nonstandard_consumables.v1'


def source_declaration(row):
    return {'food': {'nutrition': None, 'saturation_modifier': None},
            'effects': copy.deepcopy(row['source_selected_effects']), 'effects_resolved': False,
            'nutrients_resolved': False, 'nonstandard_consumable': copy.deepcopy(row), 'evidence': list(row['evidence'])}


def attach_source(frozen, document, path=None):
    if document.get('schema') != SCHEMA or not isinstance(document.get('consumables'), dict):
        raise ValueError('invalid nonstandard consumable source schema')
    result = copy.deepcopy(frozen)
    if not isinstance(document.get('source_inputs'), dict) or not document['source_inputs']: raise ValueError('nonstandard source inputs required')
    for source, digest in document['source_inputs'].items():
        if result['manifest'].get(source) != digest:
            raise ValueError('nonstandard source is not bound to frozen input: ' + source)
    selectors = document.get('ingredient_inputs', {})
    for kind in ('exact_item_ids', 'live_item_tags'):
        values = selectors.get(kind, [])
        if not isinstance(values, list) or len(values) != len(set(values)) or any(not isinstance(v, str) or not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', v) for v in values):
            raise ValueError('invalid explicit native ingredient selectors')
    for item, row in document['consumables'].items():
        if not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', item): raise ValueError('invalid nonstandard source ID')
        if row.get('id') != item or row.get('kind') != 'native_whole_drink' or row.get('is_edible') is not False or row.get('food_properties', 'missing') is not None:
            raise ValueError('nonstandard source must explicitly distinguish nonedible/null food properties')
        if not row.get('evidence') or row.get('use_animation') != 'drink' or row.get('use_duration_ticks') != 32:
            raise ValueError('nonstandard native use source missing')
        if row.get('source_finish_food_mutation', {}).get('kind') != 'none':
            raise ValueError('nonstandard no-food-mutation source proof missing')
        if row['source_finish_food_mutation'].get('normalized_target') != {'hunger': 0, 'saturation_modifier': 0}:
            raise ValueError('nonstandard normalized no-operation target must not invent food properties')
        # Nulls are the actual *kind* of getter, not zero-valued FoodProperties.
        result['foods'][item] = source_declaration(row)
        result['servings'][item] = {'kind': 'source_native_whole_use', 'portions': 1,
            'evidence': list(row['evidence']), 'status': 'source_only_pending_connected_completion'}
    result['items'] = sorted(set(result['items']) | set(document['consumables']))
    result['nonstandard_consumables'] = copy.deepcopy(document)
    if path is not None:
        result['manifest'][str(Path(path).resolve())] = hashlib.sha256(Path(path).read_bytes()).hexdigest()
    apply_production_source(result)
    return result


def apply_production_source(frozen):
    for item, definition in frozen.get('nonstandard_consumables', {}).get('consumables', {}).items():
        production = definition.get('source_production', {})
        for rid in production.get('recipe_ids', []):
            recipe = frozen['recipes'].get(rid)
            if recipe is None: continue
            recipe['source_native_production'] = copy.deepcopy(production)
            if production.get('kind') == 'native_teapot':
                if len(recipe['outputs']) != 1 or recipe['outputs'][0]['id'] != item:
                    raise ValueError('reviewed native teapot output/source mismatch')
                recipe['native_declared_result_prototype'] = copy.deepcopy(recipe['outputs'])
                recipe['outputs'][0]['count'] = production['output_count']
                recipe['issues'] = sorted(set(recipe['issues'] + ['source_native_assemble_yield_requires_connected_transaction_receipt']))


def load_source(frozen, path):
    return attach_source(frozen, json.loads(path.read_text()), path)


def source_review(row):
    review = {'role': 'drink', 'work_class': 'none', 'reviewed': ['role', 'work_class'],
              'reason': 'Explicit nonedible native whole-cup completion, not a FoodProperties item or placed bite.',
              'evidence': list(row['evidence']), 'source_only': True}
    legacy = row.get('legacy_balance_fallback')
    if legacy:
        for key in ('hydration', 'quenching'):
            value = legacy.get(key)
            if type(value) not in (int, float) or not math.isfinite(value) or value < 0: raise ValueError('invalid explicit legacy drink balance')
        if not legacy.get('invalid_or_unpaid_new_receipt_must_not_fallback') or not legacy.get('unknown_nutrition_not_zero'):
            raise ValueError('legacy balance must not conceal unpaid/unknown intake')
        review['reviewed'].append('moisture')
        review['moisture'] = {key: legacy[key] for key in ('hydration', 'quenching', 'class')}
        review['reason'] += ' Root explicit conservative oldstock balance is not a paid-fluid/getter/zero-nutrition assertion; tagged new receipts supersede it and invalid/unpaid receipts cannot use it.'
    return review


def import_rows(frozen, snapshot, foods, snapshot_hash):
    definitions = frozen.get('nonstandard_consumables', {}).get('consumables', {})
    rows = snapshot.get('native_consumables', [])
    if not isinstance(rows, list): raise ValueError('native_consumables must be array')
    if set(definitions) & set(foods): raise ValueError('nonstandard source contradicts native FoodProperties declaration')
    seen = set()
    for row in rows:
        item = row.get('id')
        if not isinstance(item, str) or not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', item): raise ValueError('invalid native consumable ID')
        if item in seen: raise ValueError('duplicate native consumable ID')
        seen.add(item)
        if item not in definitions:
            frozen['native_import_gaps'].append({'code': 'native_consumable_requires_explicit_source_review', 'id': item})
            continue
        source = definitions[item]
        for key in ('item_class', 'is_edible', 'food_properties', 'use_animation', 'use_duration_ticks', 'remainder'):
            if key not in row or row[key] != source[key]:
                raise ValueError('nonstandard native getter/source mismatch: ' + item + '/' + key)
        declaration = source_declaration(source)
        declaration['native_consumption'] = copy.deepcopy(row)
        declaration['native_nonstandard_base_getters_observed'] = True
        declarations = row.get('native_effect_declarations')
        if isinstance(declarations, list):
            effects = []
            for effect in declarations:
                value = {key: effect.get(key) for key in ('id', 'duration', 'amplifier')}
                value['chance'] = effect.get('probability')
                if not isinstance(value['id'], str) or any(type(value[k]) not in (int, float) or not math.isfinite(value[k]) for k in ('duration', 'amplifier', 'chance')):
                    raise ValueError('invalid native declared effect')
                if value['duration'] < 0 or value['amplifier'] < 0 or not 0 <= value['chance'] <= 1: raise ValueError('invalid native declared effect bounds')
                effects.append(value)
            if effects != source['source_selected_effects']: raise ValueError('native declared effects differ from explicitly reviewed source')
            declaration['effects'] = effects; declaration['effects_resolved'] = True
            declaration['effects_basis'] = 'actual_native_registry_declarations_not_observed_awards'
        declaration['evidence'].append('native_snapshot:' + snapshot_hash + '#native_consumables/' + item)
        nutrients = row.get('nutrients')
        if nutrients is not None and not isinstance(nutrients, dict): raise ValueError('native consumable nutrients must be object/null/absent')
        for value in (nutrients or {}).values():
            if type(value) not in (int, float) or not math.isfinite(value) or value < 0: raise ValueError('invalid native consumable nutrient')
        for dose in ('sugar', 'alcohol', 'spice'):
            value = row.get(dose)
            if value is not None and (type(value) not in (int, float) or not math.isfinite(value) or not 0 <= value <= 10000): raise ValueError('invalid native consumable dose')
        declaration['nutrients'] = copy.deepcopy(nutrients)
        declaration['nutrients_resolved'] = nutrients is not None
        foods[item] = declaration
    for item in definitions:
        if item not in seen:
            foods[item] = source_declaration(definitions[item])
            frozen['native_import_gaps'].append({'code': 'native_nonstandard_consumable_not_enumerated', 'id': item})
