#!/usr/bin/env python3
"""Freeze explicit inputs; never discover arbitrary sibling source repositories.

Historical runtime edges are NOT a statement of current effective KubeJS recipes.
Current resources are overlays, not an emulation of Forge conditions or JavaScript.
"""
import argparse
import hashlib
import json
import re
import tomllib
import zipfile
from pathlib import Path
from policy_rules import ALIASES, BULK_ALIASES, CROP_ALIASES

HERE = Path(__file__).resolve().parent


def encoded(value):
    return json.dumps(value, sort_keys=True, indent=2, ensure_ascii=False) + '\n'


def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def merge_tag(target, tag, raw):
    if raw.get('replace') or tag not in target:
        target[tag] = raw
    else:
        target[tag] = dict(raw, values=target[tag].get('values', []) + raw.get('values', []), replace=target[tag].get('replace', False))


def resource_recipe(rid, raw, evidence):
    """Only normalize unambiguous item recipes. Retain raw JSON for everything."""
    groups = []
    issues = []
    def ingredient(value):
        if isinstance(value, list):
            return [edge for alternative in value for edge in ingredient(alternative)]
        if not isinstance(value, dict):
            issues.append('unsupported ingredient ' + repr(value)); return []
        if 'item' in value or 'tag' in value:
            kind = 'item' if 'item' in value else 'tag'
            if set(value) - {kind, 'count'}:
                issues.append('ingredient has additional matching constraints')
            return [{'kind': kind, 'id': value[kind], 'count': value.get('count', 1)}]
        issues.append('custom ingredient requires native adapter'); return []
    if isinstance(raw.get('pattern'), list) and isinstance(raw.get('key'), dict):
        for row in raw['pattern']:
            for key in row:
                if key != ' ':
                    groups.append(ingredient(raw['key'][key]))
    else:
        if 'pattern' in raw: issues.append('custom pattern semantics require native adapter')
        values = raw.get('ingredients', raw.get('ingredient', []))
        if isinstance(values, dict): values = [values]
        for value in values:
            if value == []: continue  # Ingredient.EMPTY in a native shaped slot.
            groups.append(ingredient(value))
    if raw.get('type') == 'kaleidoscope_cookery:teapot':
        count = raw.get('ingredient_count', 12)
        if type(count) is not int or count < 1: issues.append('invalid native teapot ingredient_count')
        else:
            for group in groups:
                for edge in group: edge['count'] *= count
    result = raw.get('result', {})
    if isinstance(result, str): result = {'item': result}
    results = raw.get('results', result if isinstance(result, list) else [result] if result else [])
    outputs = []
    for value in results:
        if isinstance(value, dict) and 'item' in value:
            outputs.append({'kind': 'item', 'id': value['item'], 'count': value.get('count', 1),
                            **({'chance': value['chance']} if 'chance' in value else {})})
        else: issues.append('non-item or custom output')
    if not groups: issues.append('no statically normalized ingredients')
    if raw.get('type', '').split(':')[0] not in {'minecraft', 'farmersdelight', 'ubesdelight', 'kaleidoscope_cookery', 'create'}:
        issues.append('unreviewed serializer')
    if 'conditions' in raw or 'forge:conditions' in raw: issues.append('resource conditions need live resolution')
    return {'id': rid, 'type': raw.get('type'), 'inputs': [x[0] for x in groups if len(x) == 1],
            'input_groups': groups, 'outputs': outputs, 'requirements': {'time': raw.get('time', raw.get('cookingtime', 0))},
            'raw': raw, 'issues': sorted(set(issues)), 'evidence': [evidence], 'layer': 'resource_overlay'}


def historical_recipe(row):
    groups, issues = [], list(row.get('issues', []))
    def ing(value):
        if isinstance(value, list): return [edge for v in value for edge in ing(v)]
        if isinstance(value, dict):
            if 'item' in value: return [{'kind': 'item', 'id': value['item'], 'count': value.get('count', 1)}]
            if 'tag' in value: return [{'kind': 'tag', 'id': value['tag'], 'count': value.get('count', 1)}]
        return []
    for group in row.get('input_groups', []):
        alternatives = ing(group.get('ingredient'))
        if not alternatives: alternatives = group.get('alternatives', [])
        if alternatives: groups.append(alternatives)
    if not groups and row.get('inputs'):
        groups = [[edge] for edge in row['inputs']]
    if row.get('normalization') == 'partial' or not row.get('parsed'): issues.append('partial historical normalization')
    if row.get('fluids_in') or row.get('fluids_out'): issues.append('fluid accounting requires native adapter')
    return {key: row.get(key, []) for key in ('inputs', 'outputs', 'requirements', 'catalysts', 'fluids_in', 'fluids_out')} | {
        'id': row['id'], 'type': row['type'], 'serializer': row.get('serializer'), 'input_groups': groups,
        'issues': issues, 'evidence': ['historical_runtime:' + row['id']], 'layer': 'historical_runtime',
        'serializer_payload_sha256': row.get('serializer_payload', {}).get('sha256')}


def extract(pack, snapshot, kc_jar, kc_sources, drinks_source=None, pinned_jars_dir=None, pinned_mods=(), additional_inputs=()):
    manifest = {}
    def pin(path):
        manifest[str(path)] = sha(path)
    for name in ('snapshot.json', 'registries.json', 'recipes.json', 'tags.json', 'mods.json'):
        pin(snapshot / name)
    pin(kc_jar)
    for path in additional_inputs: pin(path)
    registries = json.loads((snapshot / 'registries.json').read_text())
    items = registries['items']
    foods = {key: {'food': value['food'], 'java_class': value.get('java_class'),
                   'evidence': ['historical_registry:' + key], 'effects': None}
             for key, value in items.items() if 'food' in value}
    source_recipes = json.loads((snapshot / 'recipes.json').read_text())
    recipes = {row['id']: historical_recipe(row) for row in source_recipes['recipes']}
    tags = json.loads((snapshot / 'tags.json').read_text())['item_tags']
    source_tags = {}
    managed_drinks = {}
    acquisition_resources = {}
    # Explicit current pack binaries only; historical installed external mods stay historical.
    jars = sorted((pack / 'mods').glob('*.jar'))
    pinned_external_mods = {}
    for name in pinned_mods:
        if pinned_jars_dir is None: raise ValueError('--pinned-mod requires explicit --pinned-jars-dir')
        descriptor = pack / 'mods' / (name + '.pw.toml')
        data = tomllib.loads(descriptor.read_text()); jar = pinned_jars_dir / data['filename']
        algorithm = data['download']['hash-format']
        if algorithm not in ('sha1', 'sha256', 'sha512', 'md5'): raise ValueError('unsupported pin hash: ' + algorithm)
        digest = hashlib.new(algorithm)
        with jar.open('rb') as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b''): digest.update(block)
        if digest.hexdigest() != data['download']['hash']: raise ValueError('pinned JAR hash mismatch: ' + name)
        pin(descriptor)
        pinned_external_mods[name] = {'jar': str(jar), 'descriptor': str(descriptor), 'hash_format': algorithm,
                                      'hash': data['download']['hash'], 'verified_current_pin': True}
        jars.append(jar)
    jars.append(kc_jar)
    for jar in jars:
        pin(jar)
        with zipfile.ZipFile(jar) as archive:
            for name in sorted(archive.namelist()):
                if name == 'drinks.json' and jar.name.startswith('better-drinking-water-'):
                    managed_drinks = {d['id']: d for d in json.loads(archive.read(name)).get('drinks', [])}
                match = re.fullmatch(r'data/([^/]+)/recipes/(.+)\.json', name)
                tag = re.fullmatch(r'data/([^/]+)/tags/items/(.+)\.json', name)
                if match:
                    try: raw = json.loads(archive.read(name))
                    except (ValueError, UnicodeDecodeError): continue
                    rid = match[1] + ':' + match[2]
                    recipes[rid] = resource_recipe(rid, raw, str(jar) + '#' + name)
                elif jar == kc_jar and name.startswith('data/') and name.endswith('.json') and any(x in name for x in ('/loot_tables/', '/worldgen/', '/biome_modifier/')):
                    raw = json.loads(archive.read(name))
                    acquisition_resources[str(jar) + '#' + name] = raw
                elif tag:
                    merge_tag(source_tags, tag[1] + ':' + tag[2], json.loads(archive.read(name)))
    for path in sorted((pack / 'kubejs/data').rglob('*.json')):
        pin(path)
        rel = path.relative_to(pack / 'kubejs/data').as_posix()
        match = re.fullmatch(r'([^/]+)/recipes/(.+)\.json', rel)
        tag = re.fullmatch(r'([^/]+)/tags/items/(.+)\.json', rel)
        if match:
            rid = match[1] + ':' + match[2]
            recipes[rid] = resource_recipe(rid, json.loads(path.read_text()), str(path))
        elif tag: merge_tag(source_tags, tag[1] + ':' + tag[2], json.loads(path.read_text()))
        elif any(value in '/' + rel for value in ('/loot_tables/', '/worldgen/', '/biome_modifier/')):
            acquisition_resources[str(path)] = json.loads(path.read_text())
    for path in sorted((pack / 'kubejs/data').rglob('*.nbt')): pin(path)
    # JS is pinned, NOT evaluated; every current script remains a requalification input.
    for root in ('kubejs/server_scripts', 'kubejs/startup_scripts'):
        for path in sorted((pack / root).rglob('*.js')):
            if path.name != '83_culinary_policy.js': pin(path)
    for path in sorted((pack / 'mods').glob('*.pw.toml')): pin(path)
    for name in ('pack.toml', 'index.toml', 'gradle/active-custom-mods.json'): pin(pack / name)
    java_root = kc_sources / 'com/github/ysbbbbbb/kaleidoscopecookery'
    java = {}
    for path in sorted(java_root.rglob('*.java')):
        pin(path); java[path.relative_to(java_root).as_posix()] = path.read_text()
    constants = {}
    for match in re.finditer(r'FoodProperties\s+(\w+)\s*=\s*(.*?);', java['init/ModFoods.java'], re.S):
        name, body = match.groups()
        nutrition = re.search(r'\.nutrition\((\d+)\)', body)
        saturation = re.search(r'\.saturationMod\(([\d.]+)[Ff]?\)', body)
        if nutrition and saturation:
            constants[name] = {'nutrition': int(nutrition[1]), 'saturation_modifier': float(saturation[1]),
                               'always_edible': '.alwaysEat()' in body, 'source_expression': body,
                               'effect_expressions': re.findall(r'\.effect\((.*?)\)\s*\n', body, re.S)}
    kc_items = {}
    for line in java['init/ModItems.java'].splitlines():
        match = re.search(r'ITEMS\.register\("([^"]+)"', line)
        if not match: continue
        item_id = 'kaleidoscope_cookery:' + match[1]
        kc_items[item_id] = {'source': line.strip()}
        ref = re.search(r'ModFoods\.(\w+)', line)
        if not ref:
            constructor = re.search(r'new (\w+)\(', line)
            if constructor:
                body = java.get('item/' + constructor[1] + '.java', '')
                ref = re.search(r'\.food\(ModFoods\.(\w+)\)', body)
        if ref and ref[1] in constants:
            const = constants[ref[1]]
            foods[item_id] = {'food': {k: const[k] for k in ('nutrition', 'saturation_modifier', 'always_edible')},
                              'effects': const['effect_expressions'], 'source_expression': const['source_expression'],
                              'evidence': ['kc_source:init/ModItems.java', 'kc_source:init/ModFoods.java#' + ref[1]]}
    servings = {}
    # A block-class candidate is NOT an edible-item assertion. Keep placed-only
    # surfaces separately until native bite/portion/provider discovery qualifies it.
    for block_id, block_data in sorted(registries['blocks'].items()):
        classname = block_data.get('java_class', '').rsplit('.', 1)[-1]
        if not re.search(r'(?:Cake|Feast|Pie|Pancake|IceCream|RiceRollMedley|CheeseWheel|Cheese)Block$', classname): continue
        item_id = block_data.get('item_id')
        servings[block_id] = {'kind': 'placed_candidate', 'portions': None, 'item_id': item_id,
                              'java_class': block_data['java_class'],
                              'unresolved': ['native_placed_intake_and_portions_not_enumerated'],
                              'evidence': ['historical_block_registry:' + block_id]}
        if item_id and item_id != 'minecraft:air' and item_id in items:
            foods.setdefault(item_id, {'food': {'nutrition': None, 'saturation_modifier': None},
                                      'effects': None, 'placed_only_candidate': True,
                                      'evidence': ['historical_block_registry:' + block_id]})
    for vessel in ('teapot', 'transmutation_lunch_bag', 'clay_pot_milk_tea', 'cold_cut_ham_slices'):
        servings['kaleidoscope_cookery:' + vessel] = {
            'kind': 'dynamic_vessel_or_placed_candidate', 'portions': None,
            'unresolved': ['native_contents_and_completion_surface_not_enumerated'],
            'evidence': ['kc_source:init/ModItems.java#' + vessel]}
    for name, count, block, item in re.findall(r'registerFoodData\("([^"]+)",\s*FoodData\s*\.create(?:OneByTwo)?\((\d+),\s*(\w+),\s*(\w+)\)', java['init/registry/FoodBiteRegistry.java']):
        rid = 'kaleidoscope_cookery:' + name
        servings[rid] = {'kind': 'placed_bites', 'portions': int(count), 'item_consumptions': 1,
                         'block_food_constant': block, 'item_food_constant': item,
                         'evidence': ['kc_source:init/registry/FoodBiteRegistry.java#' + name]}
        const = constants[item]
        foods[rid] = {'food': {k: const[k] for k in ('nutrition', 'saturation_modifier', 'always_edible')},
                      'effects': const['effect_expressions'], 'source_expression': const['source_expression'],
                      'evidence': servings[rid]['evidence']}
    if drinks_source is not None:
        pin(drinks_source)
        managed_drinks = {d['id']: d for d in json.loads(drinks_source.read_text()).get('drinks', [])}
    relevant = {rid: row for rid, row in recipes.items() if row['type'] in ('farmersdelight:cutting', 'farmersdelight:cooking')
                or str(row['type']).startswith('kaleidoscope_cookery:')
                or any(edge.get('id') in foods or edge.get('id') in managed_drinks or edge.get('id') in ALIASES for edge in row['outputs'])
                or any(edge.get('kind') == 'item' and edge.get('id') in BULK_ALIASES for group in row['input_groups'] for edge in group)}
    # Keep ALL item IDs/tag membership to validate references without Diet-as-edibility.
    return {'schema': 'better_cooking:frozen_inputs.v1', 'snapshot': json.loads((snapshot / 'snapshot.json').read_text()),
            'source_recipe_status': {k: source_recipes[k] for k in ('recipe_count', 'partial_count', 'error_count', 'complete')},
            'manifest': manifest, 'items': sorted(set(items) | set(kc_items)), 'foods': foods,
            'servings': servings, 'recipes': relevant, 'item_tags': tags, 'resource_tags': source_tags,
            'managed_drinks': managed_drinks, 'acquisition_resources': acquisition_resources,
            'pinned_external_mods': pinned_external_mods,
            'kc_food_constants': constants, 'kc_items': kc_items,
            'limitations': ['Historical snapshot is not current runtime acceptance.',
                            'Resource overlays do not evaluate Forge conditions or current KubeJS JavaScript.',
                            'Historical food registry does not enumerate effects or dynamic food properties.',
                            'KC constructor-driven and dynamic vessel/portion intake require native enumeration.',
                            'Current external Packwiz pins may differ from historical installed JARs.',
                            'Current custom source changes are not deployed; only explicit current JAR resources are overlaid.']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack', type=Path, required=True)
    parser.add_argument('--snapshot', type=Path, required=True)
    parser.add_argument('--kc-jar', type=Path, required=True)
    parser.add_argument('--kc-sources', type=Path, required=True)
    parser.add_argument('--drinks-source', type=Path, help='Explicit coordinated authored source drinks.json; no sibling discovery')
    parser.add_argument('--additional-input', type=Path, action='append', default=[], help='Explicit authored evidence file to hash-bind; no sibling discovery')
    parser.add_argument('--pinned-jars-dir', type=Path, help='Explicit retained JAR directory; each selected mod must match its CURRENT descriptor hash')
    parser.add_argument('--pinned-mod', action='append', default=[], help='Exact pack descriptor basename to inspect; repeat for bounded source review')
    parser.add_argument('--output', type=Path, default=HERE / 'frozen/catalogue_inputs.json')
    args = parser.parse_args()
    result = extract(args.pack.resolve(), args.snapshot.resolve(), args.kc_jar.resolve(), args.kc_sources.resolve(), args.drinks_source.resolve() if args.drinks_source else None, args.pinned_jars_dir.resolve() if args.pinned_jars_dir else None, args.pinned_mod, [p.resolve() for p in args.additional_input])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(encoded(result))
    print(f'frozen {len(result["foods"])} declared food/serving candidates; {len(result["recipes"])} routes; {len(result["manifest"])} pinned inputs; UNQUALIFIED')


if __name__ == '__main__': main()
