"""Explicit, source-bound lowering onto installed native Kaleidoscope serializers.

No custom preparation recipes or gameplay qualification are introduced. Candidate
station plans are compiled separately before updating the pack installation.
"""
import copy
from recipe_policy import fingerprint


def members(ingredient, tags):
    if isinstance(ingredient, list):
        return set().union(*(members(x, tags) for x in ingredient))
    if set(ingredient) == {'item'} and isinstance(ingredient['item'], str):
        return {ingredient['item']}
    if set(ingredient) == {'tag'}:
        values = tags.get(ingredient['tag'])
        if not values:
            raise ValueError('empty/missing ingredient tag: ' + ingredient['tag'])
        return set(values)
    raise ValueError('unsupported ingredient predicate: ' + repr(ingredient))


def knife_tools_only(tool):
    alternatives = tool if isinstance(tool, list) else [tool]
    return bool(alternatives) and all(
        (set(x) == {'tag'} and x['tag'] in {'forge:tools/knives', 'farmersdelight:tools/knives'})
        or x == {'type': 'farmersdelight:tool_action', 'action': 'knife_dig'}
        for x in alternatives)


def lower_cutting(source_id, raw, choice, baseline, tags):
    if raw['type'] != 'farmersdelight:cutting' or len(raw['ingredients']) != 1:
        raise ValueError('not a one-input FD cutting recipe: ' + source_id)
    if source_id in baseline['protected_recipe_ids']:
        raise ValueError('protected source: ' + source_id)
    if any(x.get('chance', 1) != 1 for x in raw['result']):
        raise ValueError('Kaleidoscope chopping cannot preserve probabilistic outputs')
    if not knife_tools_only(raw['tool']):
        raise ValueError('not a knife-tag cutting recipe')
    accepted = members(raw['ingredients'][0], tags)
    if any('wild_' in x for x in accepted):
        raise ValueError('keep wild-crop acquisition native')
    for recipe_id, native in baseline['recipes'].items():
        if native['type'] == 'kaleidoscope_cookery:chopping_board' and members(native['ingredient'], tags) & accepted:
            raise ValueError('ambiguous canonical cutting input: ' + source_id + ' / ' + recipe_id)
    model_item = choice['model_item']
    if model_item not in accepted:
        raise ValueError('display model does not represent a valid source ingredient')
    result = {'type': 'kaleidoscope_cookery:chopping_board',
              'ingredient': copy.deepcopy(raw['ingredients'][0]),
              'result': copy.deepcopy(raw['result']), 'cut_count': 3,
              'model_id': 'better_cooking:' + source_id.replace(':', '/')}
    if 'conditions' in raw: result['conditions'] = copy.deepcopy(raw['conditions'])
    return result


def overlapping_formulations(left, right, tags):
    if len(left) != len(right):
        return False
    a, b = [members(x, tags) for x in left], [members(x, tags) for x in right]
    def match(index, used):
        if index == len(a): return True
        return any(j not in used and a[index] & group and match(index + 1, used | {j}) for j, group in enumerate(b))
    return bool(match(0, set()))


def lower_cooking(source_id, raw, choice, baseline, tags, facts):
    if raw['type'] != 'farmersdelight:cooking' or source_id in baseline['protected_recipe_ids']:
        raise ValueError('not an unprotected FD cooking recipe')
    if choice['source_sha256'] != fingerprint(raw):
        raise ValueError('stale cooking choice')
    native = facts['cooking'][source_id]
    if not 1 <= len(raw['ingredients']) <= 9 or not 1 <= raw['result'].get('count', 1) <= 9:
        raise ValueError('native station capacity/portion limit')
    if native['container_count'] not in (0, 1) or (native['container_count'] and native['container_nbt'] not in (None, '{}', 'null')):
        raise ValueError('nontrivial serving container')
    for group in raw['ingredients']:
        for item in members(group, tags):
            row = facts['items'][item]
            expected = row['crafting_remainder'] or 'minecraft:air'
            if expected != row['kaleidoscope_container'] or row['remainder_count'] not in (0, 1) or (row['remainder_count'] and row['remainder_nbt'] not in (None, '{}', 'null')):
                raise ValueError('ingredient remainder differs at KC station: ' + item)
    target = choice['serializer']
    if target not in {'kaleidoscope_cookery:stockpot', 'kaleidoscope_cookery:pot'}:
        raise ValueError('unauthored cooking surface')
    result = {'type': target, 'ingredients': copy.deepcopy(raw['ingredients']),
              'result': copy.deepcopy(raw['result']), 'time': native['cookingtime']}
    if target == 'kaleidoscope_cookery:stockpot':
        if choice.get('soup_base') != 'minecraft:water':
            raise ValueError('water base must be explicitly authored for this dish')
        result['soup_base'] = choice['soup_base']
        if native['container']:
            result['carrier'] = {'item': native['container']}
        else:
            result['empty_carrier'] = True
    else:
        output_container = facts['items'][raw['result']['item']]['kaleidoscope_container']
        if output_container != (native['container'] or 'minecraft:air'):
            raise ValueError('stir-fry implicit serving carrier differs from FD')
        result['stir_fry_count'] = 3
    if 'conditions' in raw: result['conditions'] = copy.deepcopy(raw['conditions'])
    for recipe_id, other in baseline['recipes'].items():
        if other['type'] == target and overlapping_formulations(raw['ingredients'], other['ingredients'], tags):
            if target.endswith(':stockpot') and other.get('soup_base', 'minecraft:water') != result['soup_base']:
                continue
            raise ValueError('ambiguous native formulation: ' + recipe_id)
    return result


def build_cooking_decisions(baseline, tags, choices, facts):
    if facts.get('schema') != 'better_cooking:native_recipe_facts.v1' or facts.get('errors'):
        raise ValueError('incomplete native container facts')
    rows = {}
    for source_id, choice in choices.items():
        raw = baseline['recipes'][source_id]
        if choice['source_sha256'] != fingerprint(raw):
            raise ValueError('stale authored cooking choice')
        row = {'action': choice.get('action', 'redirect'), 'source_sha256': fingerprint(raw),
               'family': 'native_station_reviewed_exception' if choice.get('action') == 'retain' else 'canonical_native_cooking',
               'reason': choice['reason'], 'native_source_facts': facts['cooking'][source_id],
               'native_facts_sha256': fingerprint(facts)}
        if row['action'] != 'retain':
            definition = lower_cooking(source_id, raw, choice, baseline, tags, facts)
            row['reason'] += ' ' + choice['native_process_tradeoff']
            row['preserved'] = {'ingredients': raw['ingredients'], 'result': raw['result'],
                                'conditions': raw.get('conditions', []), 'cookingtime': facts['cooking'][source_id]['cookingtime']}
            row['definitions'] = [{'id': source_id, 'json': definition,
                                   'preservation_review': 'Exact ingredient groups, result count/NBT, native serving carrier and ingredient remainders verified against live facts. Native station water/oil/handling and XP behavior are explicit process changes, not claims of identical apparatus.'}]
        rows[source_id] = row
    return rows


def build_cutting_decisions(baseline, tags, choices):
    result = {}
    for source_id, choice in choices.items():
        raw = baseline['recipes'][source_id]
        if choice.get('source_sha256') != fingerprint(raw):
            raise ValueError('stale authored station choice: ' + source_id)
        definition = lower_cutting(source_id, raw, choice, baseline, tags)
        result[source_id] = {
            'action': 'redirect', 'family': 'canonical_native_cutting',
            'source_sha256': fingerprint(raw),
            'reason': choice['reason'],
            'station_change': {'from': raw['type'], 'to': definition['type'],
                               'knife_compatibility': 'Native KC kitchen_knife tag includes FD knife tags; preserve required FD/addon infrastructure.'},
            'preserved': {'ingredient': raw['ingredients'][0], 'outputs': raw['result'],
                          'conditions': raw.get('conditions', []), 'registered_items': 'unchanged'},
            'definitions': [{'id': source_id, 'json': definition,
                             'preservation_review': 'One source ingredient, every deterministic output count/NBT/by-product and conditions retained exactly; native KC three-cut workflow replaces the FD board. No overlapping native KC input recipe.'}]
        }
    return result
