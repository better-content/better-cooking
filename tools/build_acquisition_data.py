#!/usr/bin/env python3
"""Stage exact new-output crop compatibility data, never a native closure receipt.

Only five named KC loot tables and the exact tomato seed recipe are writable.
Leaf item IDs change, quantities/chances/conditions/weights remain byte-semantic.
Canonical outputs pass through the existing provider-owned seed/origin gate.
No existing inventories, world blocks, structures, merchant offers or registries move.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import zipfile
from extract_catalogue import encoded
from structure_nbt import canonical_finite_forage

HERE = Path(__file__).resolve().parent
PREFIX = 'kaleidoscope_cookery:'
TARGETS = {'tomato': 'farmersdelight:tomato', 'tomato_seed': 'farmersdelight:tomato_seeds',
           'lettuce': 'farmersdelight:cabbage', 'lettuce_seed': 'farmersdelight:cabbage_seeds',
           'rice': 'farmersdelight:rice', 'wild_rice': 'better_bumblezone_crops:farmersdelight_rice_seeds',
           'rice_panicle': 'farmersdelight:rice_panicle'}
ALIASES = {PREFIX + name: target for name, target in TARGETS.items()}
LOOT = ['blocks/tomato_crop', 'blocks/lettuce_crop', 'blocks/rice_crop', 'straw_hat_seed_drop', 'chest/village_chest']


def rewrite_loot(raw):
    result = copy.deepcopy(raw)
    changes = []
    def visit(value):
        if isinstance(value, list):
            for entry in value: visit(entry)
        elif isinstance(value, dict):
            if value.get('type') == 'minecraft:item' and value.get('name') in ALIASES:
                old = value['name']; value['name'] = ALIASES[old]
                changes.append({'from': old, 'to': value['name']})
            for child in value.values(): visit(child)
    visit(result)
    return result, changes


def build(jar, pack):
    outputs, resources = {}, []
    with zipfile.ZipFile(jar) as archive:
        for loot in LOOT:
            resource = 'data/kaleidoscope_cookery/loot_tables/' + loot + '.json'
            original = archive.read(resource)
            converted, changes = rewrite_loot(json.loads(original))
            if not changes: raise ValueError('expected pinned crop loot reference missing: ' + resource)
            output = pack / 'kubejs' / resource
            outputs[output] = encoded(converted)
            resources.append({'resource': resource, 'source_sha256': hashlib.sha256(original).hexdigest(),
                              'output': str(output), 'changes': changes, 'rule': 'only_item_name_leaves_changed'})
        resource = 'data/kaleidoscope_cookery/recipes/tomato_seed.json'
        original = archive.read(resource); raw = json.loads(original)
        if raw.get('result') != {'item': PREFIX + 'tomato_seed'}: raise ValueError('pinned tomato seed recipe changed')
        raw['result']['item'] = TARGETS['tomato_seed']
        output = pack / 'kubejs' / resource; outputs[output] = encoded(raw)
        resources.append({'resource': resource, 'source_sha256': hashlib.sha256(original).hexdigest(), 'output': str(output),
                          'changes': [{'from': PREFIX + 'tomato_seed', 'to': TARGETS['tomato_seed']}],
                          'rule': 'same_count_canonical_output_then_existing_21_seed_recipe_gate'})
    java = '/home/dev/.tmp/kaleidoscope-review/sources/com/github/ysbbbbbb/kaleidoscopecookery/'
    document = {'schema': 'better_cooking:authored_acquisition.v1', 'status': 'authored_pending_native',
                'source_jar': str(jar), 'source_jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                'canonical_authority_source': '/home/dev/mod_source/better-bumblezone-crops/src/main/resources/defaults/cultivars.json',
                'harvest_redirects': {}, 'data_overrides': resources,
                'trade_input_compatibility': [],
                'worldgen': {'status': 'requires_native_origin_and_growth_guard',
                            'exact_templates': ['kaleidoscope_cookery:village/houses/' + biome + '_kitchen' for biome in ['desert', 'plains', 'savanna', 'snowy', 'taiga']],
                            'processor': 'kaleidoscope_cookery:crop_replace',
                            'existing_world_blocks': 'retain', 'finite_existing_stock': 'retain',
                            'rule': 'No heuristic structure removal; native BCC origin/growth/propagation authority must prevent unsourced renewable cultivars.'}}
    for old, target in ALIASES.items():
        document['harvest_redirects'][old] = {'target': target, 'source_count_multiplier': 1,
             'scope': 'new_native_harvest_output_only', 'status': 'authored_pending_native',
             'evidence': [java + 'init/ModItems.java', java + 'block/crop/BaseCropBlock.java', java + 'block/misc/StrawBlocks.java'],
             'reason': 'Canonical NEW crop acquisition output only; retain physical output count and leave all old inventory stacks registered and unchanged. Panicle/wild-rice are acquisition identities, NOT 1:1 ingredient equivalences.'}
    for old, count in [('tomato', 12), ('lettuce', 16), ('rice', 20)]:
        document['trade_input_compatibility'].append({'profession': 'kaleidoscope_cookery:chef', 'level': 1,
            'legacy_input': PREFIX + old, 'canonical_input': TARGETS[old], 'input_count': count,
            'output': {'id': 'minecraft:emerald', 'count': 1}, 'max_uses': 16, 'villager_xp': 2, 'price_multiplier': .05,
            'legacy_offer': 'retain_for_finite_stock', 'canonical_offer': 'requires_native_exact_compatibility_factory',
            'evidence': [java + 'event/ModTradesEvent.java#addNoviceTrades'],
            'reason': 'Source-proven CHEF BUY offer, not a seed/meal sale. Keep old finite-stock purchase and add same-price canonical-input compatibility without deleting the profession.'})
    with zipfile.ZipFile(jar) as archive:
        for biome in ['desert', 'plains', 'savanna', 'snowy', 'taiga']:
            resource = 'data/kaleidoscope_cookery/structures/village/houses/' + biome + '_kitchen.nbt'
            original = archive.read(resource)
            converted, changes = canonical_finite_forage(original, allow_no_changes=True)
            if bool(changes) != (biome in ('plains', 'savanna', 'taiga')): raise ValueError('pinned template crop scope changed: ' + biome)
            output = pack / 'kubejs' / resource
            if changes: outputs[output] = converted
            resources.append({'resource': resource, 'source_sha256': hashlib.sha256(original).hexdigest(),
                              'output': str(output) if changes else None, 'changes': changes,
                              'rule': 'only_new_template_tomato_palette_to_canonical_finite_wild_forage' if changes else 'inspected_no_duplicate_cultivar_palette_no_override'})
        resource = 'data/kaleidoscope_cookery/worldgen/processor_list/crop_replace.json'
        original = archive.read(resource); raw = json.loads(original)
        if len(raw['processors']) != 1 or len(raw['processors'][0]['rules']) != 2: raise ValueError('new crop processor rules need explicit review')
        changes = []
        for rule in raw['processors'][0]['rules']:
            if rule['input_predicate']['block'] != PREFIX + 'tomato_crop': raise ValueError('crop processor input changed')
            rule['input_predicate']['block'] = 'farmersdelight:wild_tomatoes'
            state = rule['output_state']
            if state['Name'] == PREFIX + 'lettuce_crop':
                if set(state.get('Properties', {})) != {'age'}: raise ValueError('new lettuce state properties need review')
                del state['Properties']; state['Name'] = 'farmersdelight:wild_cabbages'
            elif state['Name'] != PREFIX + 'chili_crop': raise ValueError('new crop processor output needs review')
            changes.append({'input': rule['input_predicate']['block'], 'output': state['Name'],
                            'probability': rule['input_predicate']['probability']})
        output = pack / 'kubejs' / resource; outputs[output] = encoded(raw)
        resources.append({'resource': resource, 'source_sha256': hashlib.sha256(original).hexdigest(), 'output': str(output),
                          'changes': changes, 'rule': 'canonical_finite_wild_forage_preserve_distinct_chili_and_exact_probabilities'})
    document['worldgen']['status'] = 'authored_canonical_finite_forage_pending_native'
    document['worldgen']['rule'] = 'Only new exact kitchen tomato palettes become FD wild tomatoes; lettuce processor becomes FD wild cabbage; preserve distinct chili and both native probabilities. BCC native wild-forage authority must prove nonplantable finite food-only drops.'
    return outputs, document


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--kc-jar', type=Path, required=True)
    parser.add_argument('--pack', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=HERE / 'authored/crop_acquisition.json')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    outputs, document = build(args.kc_jar.resolve(), args.pack.resolve())
    outputs[args.output] = encoded(document)
    for path, text in outputs.items():
        data = text.encode() if isinstance(text, str) else text
        if args.check:
            if not path.exists() or path.read_bytes() != data: raise SystemExit('not reproducible: ' + str(path))
        else:
            if path.exists() and path not in (args.output,) and path.read_bytes() != data:
                raise SystemExit('refusing to overwrite existing different data: ' + str(path))
            path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
    print(f'{len(outputs)-1} exact data overrides; seven acquisition-only aliases; source authored, native closure unresolved')

if __name__ == '__main__': main()
