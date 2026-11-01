"""Retain actual input getter candidates; never authorize unbound runtime defaults."""
import copy
import math

SCHEMA = 'better_cooking:native_drink_ingredients.v2'
ROW_FIELDS = {'id', 'observation_kind', 'template_basis', 'input_template', 'item_class',
              'is_edible', 'food_properties', 'nutrients', 'sugar', 'alcohol', 'spice', 'paid_intake', 'gaps'}


def capture(frozen, snapshot):
    selectors = frozen.get('nonstandard_consumables', {}).get('ingredient_inputs', {})
    rows = snapshot.get('native_drink_ingredients', [])
    if not isinstance(rows, list): raise ValueError('native_drink_ingredients must be array')
    context = snapshot.get('native_drink_ingredients_context')
    if rows or context is not None:
        if (not isinstance(context, dict) or context.get('schema') != SCHEMA
                or context.get('status') != 'observed_getters_unbound_runtime'
                or context.get('qualification') is not False or context.get('auto_install') is not False):
            raise ValueError('native drink input requires explicit unqualified v2 collector context')
    allowed = set(selectors.get('exact_item_ids', []))
    for tag in selectors.get('live_item_tags', []): allowed.update(snapshot.get('item_tags', {}).get(tag, []))
    actual, seen = [], set()
    for row in rows:
        if not isinstance(row, dict): raise ValueError('native drink input row must be object')
        if set(row) != ROW_FIELDS: raise ValueError('native v2 input requires exact 13 fields; default_stack/v1 rejected')
        if row['observation_kind'] != 'observed_template': raise ValueError('native input observation_kind must be observed_template')
        item = row.get('id')
        basis = 'provider_purity3_variant' if item == 'minecraft:water_bucket' else 'native_default'
        if row['template_basis'] != basis: raise ValueError('native input template_basis does not match item')
        if not isinstance(row['item_class'], str) or not row['item_class'] or type(row['is_edible']) is not bool or not isinstance(row['gaps'], dict):
            raise ValueError('native input class/edibility/gaps types invalid')
        food = row['food_properties']
        if food is not None:
            if not isinstance(food, dict) or set(food) != {'hunger', 'saturation_modifier'}: raise ValueError('raw food properties shape invalid')
            for field, value in food.items(): check(value, 'raw food ' + field)
            if int(food['hunger']) != food['hunger']: raise ValueError('raw hunger must be integral')
        if item not in allowed: raise ValueError('native drink input is not in explicit/live ingredient selectors: ' + str(item))
        if item in seen: raise ValueError('duplicate native drink default input ID')
        seen.add(item)
        # Native SNBT is preserved, not regex-parsed into fabricated tag absence/count facts.
        # Root's typed runtime parser must check Count1/id/exact metadata before any lookup.
        template = row.get('input_template')
        if not isinstance(template, str) or not template or len(template) > 65536: raise ValueError('native input input_template must be bounded actual full SNBT')
        raw_nutrients = row.get('nutrients')
        if raw_nutrients is not None and not isinstance(raw_nutrients, dict): raise ValueError('raw nutrients must be object/null')
        for nutrient, value in (raw_nutrients or {}).items(): check(value, 'raw nutrient ' + nutrient)
        for dose in ('sugar', 'alcohol', 'spice'):
            if row.get(dose) is not None: check(row[dose], 'raw ' + dose)
        paid = row.get('paid_intake')
        if not isinstance(paid, dict): raise ValueError('paid_intake must be explicit complete or incomplete object')
        if type(paid.get('complete')) is not bool: raise ValueError('paid_intake complete must be boolean')
        keys = {'complete', 'nutrients', 'sugar', 'alcohol', 'spice'}
        if paid['complete'] is False: keys.add('reason')
        if set(paid) != keys: raise ValueError('paid_intake requires exact complete/unknown fields')
        if paid['complete'] is False:
            if not isinstance(paid['reason'], str) or not paid['reason']: raise ValueError('unknown paid intake requires reason')
            if any(paid[field] is not None for field in ('nutrients', 'sugar', 'alcohol', 'spice')):
                raise ValueError('unknown paid intake requires four explicit null fields, not zero')
        else:
            if not isinstance(paid.get('nutrients'), dict): raise ValueError('complete paid intake requires explicit nutrient map')
            for nutrient, value in paid['nutrients'].items():
                if nutrient.lower() in ('spice', 'spices'): raise ValueError('paid ordinary nutrition duplicates Spice')
                if nutrient not in ('proteins', 'grains', 'fruits', 'fats', 'dairy', 'vegetables'):
                    raise ValueError('paid nutrient group is not ordinary: ' + nutrient)
                check(value, 'paid nutrient ' + nutrient)
            for dose in ('sugar', 'alcohol', 'spice'): check(paid.get(dose), 'complete paid ' + dose)
        actual.append(copy.deepcopy(row))
    return {'schema': SCHEMA, 'qualification': False, 'auto_install': False, 'status': 'observed_getters_unbound_runtime' if rows else 'not_enumerated',
            'binding': None, 'native_snapshot_canonical_sha256': frozen.get('native_snapshot_sha256'),
            'native_snapshot_raw_sha256': None, 'native_drink_ingredients': actual,
            'unresolved': ['actual_provider_and_resource_byte_identity_not_runtime_verified',
                           'typed_exact_observed_input_template_match_required',
                           'paid_intake_complete_required_raw_Diet_not_a_milk_profile_substitute'],
            'notice': 'Not installable defaults. Pack IDs or their hash do not prove current resource/provider byte identity.'}


def check(value, field):
    if type(value) not in (int, float) or not math.isfinite(value) or not 0 <= value <= 10000:
        raise ValueError('invalid native input ' + field)
