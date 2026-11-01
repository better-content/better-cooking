#!/usr/bin/env python3
"""Author recipe-only route decisions with exact native retention and canonical bulk redirects.

Abandoned custom preparation stages are not recreated using invented ingredients,
changed yields or lossy serializers. Native processes remain explicit exceptions.
"""
import argparse
import copy
import json
from pathlib import Path
from recipe_policy import fingerprint, compile_policy

ALIASES = {'create:wheat_flour': 'kaleidoscope_cookery:flour',
           'create:dough': 'kaleidoscope_cookery:raw_dough',
           'farmersdelight:wheat_dough': 'kaleidoscope_cookery:raw_dough'}


def redirect(raw, outputs=True):
    value = copy.deepcopy(raw)
    changes = []
    def output(row):
        if isinstance(row, list):
            return [output(x) for x in row]
        if isinstance(row, str) and row in ALIASES:
            changes.append({'kind': 'output_alias', 'from': row, 'to': ALIASES[row]})
            return ALIASES[row]
        if isinstance(row, dict) and row.get('item') in ALIASES:
            before = row['item']; row['item'] = ALIASES[before]
            changes.append({'kind': 'output_alias', 'from': before, 'to': row['item']})
        return row
    def ingredient(row):
        if isinstance(row, list):
            result = []
            for choice in row:
                transformed = ingredient(choice)
                result.extend(transformed if isinstance(transformed, list) else [transformed])
            return result
        if isinstance(row, dict) and row.get('item') in ALIASES:
            # Never erase NBT/custom matcher/count constraints.
            if set(row) != {'item'}:
                raise ValueError('non-simple alias ingredient requires explicit decision')
            changes.append({'kind': 'accept_canonical_and_old_stock', 'from': row['item'], 'to': ALIASES[row['item']]})
            return [row, {'item': ALIASES[row['item']]}]
        return row
    if outputs:
        for key in ('result', 'results'):
            if key in value: value[key] = output(value[key])
    if 'ingredient' in value: value['ingredient'] = ingredient(value['ingredient'])
    if 'ingredients' in value: value['ingredients'] = [ingredient(x) for x in value['ingredients']]
    if 'key' in value: value['key'] = {k: ingredient(v) for k, v in value['key'].items()}
    if 'sequence' in value:
        steps = []
        for step in value['sequence']:
            updated, nested = redirect(step, outputs=False)
            steps.append(updated); changes.extend(nested)
        value['sequence'] = steps
    return value, changes


def retention(raw, old):
    kind = raw['type']
    if ':' not in kind:
        kind = 'minecraft:' + kind
    if kind.startswith('kaleidoscope_cookery:'):
        return 'canonical_native_kaleidoscope', 'Retain the existing Kaleidoscope recipe and its native formulation, apparatus and output; no artificial Better Cooking preparation wrapper.'
    if kind == 'farmersdelight:cutting':
        return 'complementary_native_cutting', 'Retain native cutting tool predicates, ordered outputs and any chance/by-product semantics; Kaleidoscope native cutting remains canonical for its own ingredient formats. No new custom cutting serializer.'
    if kind == 'farmersdelight:cooking':
        return 'complementary_native_batch_meal', 'Retain this exact native batch meal and output-container handling rather than add a mandatory water/oil base or invent a custom finish. No player-work or quality gate is introduced.'
    if kind == 'ubesdelight:baking_mat':
        return 'complementary_native_baking_mat', 'Retain native mat formulation, tool and result-array semantics, including bonus counts; no custom prepared-batch item or synthetic work stages.'
    if 'drying' in kind:
        return 'protected_existing_drying', 'Existing drying recipe, output properties and aging are explicitly unchanged.'
    if old.get('disposition') == 'retain_nonfood':
        return 'protected_nonfood', 'Retain nonfood recipe and dependency infrastructure unchanged.'
    if kind.startswith(('create:', 'createdieselgenerators:', 'pneumaticcraft:', 'tconstruct:', 'complicated_bees:')):
        return 'complementary_native_processing', 'Retain explicit industrial/fluid process, quantities, catalysts and carrier/by-product rules. No blanket automation ban or new custom kitchen process.'
    if kind.startswith(('brewinandchewin:', 'farmersrespite:', 'fruitsdelight:', 'hexerei:')):
        return 'complementary_native_specialist', 'Retain distinct native brewing, fermentation, fruit processing or specialist apparatus with unchanged ingredients and containers.'
    if kind.startswith(('aether:', 'cataclysm:', 'bloodmagic:', 'occultism:', 'malum:', 'rats:')):
        return 'explicit_native_specialist_exception', 'Retain this identified native specialist or delegated recipe with its existing costs and effects; no artificial premium-meal classification or new gameplay gate.'
    if kind in {'minecraft:smelting', 'minecraft:smoking', 'minecraft:campfire_cooking', 'minecraft:blasting'}:
        return 'native_heat_finish', 'Retain the actual native heat finish, input/output ratio and duration; preserve accessible staples and real baking/roasting rather than a token pot step.'
    if kind in {'minecraft:crafting_shaped', 'minecraft:crafting_shapeless'}:
        return 'explicit_native_assembly', 'Retain exact native assembly/portioning and its shaped or shapeless ingredients. Multi-ingredient cold assembly and pastry preparation are not replaced by unsupported custom work mechanics.'
    if kind == 'createsifter:sifting':
        return 'native_material_processing', 'Preserve mesh/water/material processing, durations and probabilistic by-products. An edible experience-nugget by-product does not make mineral separation a premium-meal recipe.'
    if kind == 'the_bumblezone:container_shapeless_recipe_bz':
        return 'bumblezone_container_acquisition', 'Preserve the native honey-container/bee-bread formulation and its exact returned containers; retain Bumblezone acquisition authority rather than treating bee food as a premium kitchen meal.'
    if kind == 'minecraft:smithing_transform':
        return 'explicit_native_specialist_exception', 'Preserve the identified smithing enhancement, template/base/addition predicates and native NBT-copy behavior; this is a deliberate magical basic-food exception, not culinary assembly.'
    if kind in {'the_deep_void:glutton_block_jei_type', 'the_deep_void:treasure_bag_jei_type'}:
        return 'native_exchange_or_loot_display', 'Preserve the native Glutton exchange or treasure-bag acquisition display and its bound item IDs; do not reinterpret its JEI serializer as a kitchen crafting process or remove loot acquisition.'
    raise ValueError('unreviewed recipe serializer: ' + kind)


def build(baseline, catalogue, old):
    if set(catalogue['items']) != set(baseline['edible_items']):
        raise ValueError('catalogue does not match live baseline')
    routes = {}
    dependencies = set(baseline.get('canonical_dependency_ids', []))
    canonical = set(old['migration']['canonical_bulk_rewrite_ids']) | dependencies
    for source in baseline['affected_recipe_ids']:
        raw = baseline['recipes'][source]; previous = old['routes'].get(source, {})
        reviewed = raw
        if raw['type'] == 'unknown':
            if source not in baseline.get('pending_recipe_ids', []) or source not in baseline.get('source_serializer_ids', {}):
                raise ValueError('unbound queued recipe serializer: ' + source)
            reviewed = dict(raw, type=baseline['source_serializer_ids'][source])
        family, reason = retention(reviewed, previous)
        row = {'action': 'retain', 'family': family, 'reason': reason,
               'source_sha256': fingerprint(raw), 'serializer': raw['type']}
        if source in canonical and source not in baseline.get('protected_recipe_ids', []):
            changed, changes = redirect(raw, outputs=source not in dependencies)
            if changes:
                row.update(action='redirect', family='canonical_bulk_ingredient',
                           reason='Use canonical Kaleidoscope flour/raw dough while accepting old stock; keep the original serializer, recipe ID, all quantities, chances, NBT, fluids, time and tool fields.',
                           changes=changes,
                           definitions=[{'id': source, 'json': changed, 'preservation_review': 'Only literal alias outputs and simple ingredient alternatives changed; every other JSON field copied intact.'}])
        routes[source] = row
    return {'schema': 'better_cooking:recipe_decisions.v1', 'baseline_sha256': fingerprint(baseline),
            'foods': catalogue['items'], 'routes': routes,
            'scope_note': 'Recipe-only migration: existing native routes are explicit retention decisions, not proof of the abandoned hands-on culinary system.',
            'historical_not_live': baseline['historical_not_live'],
            'live_without_source_json': baseline['live_without_source_json']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('baseline', 'catalogue', 'historical-policy', 'decisions', 'policy'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    baseline, catalogue, old = [json.loads(p.read_text()) for p in (args.baseline, args.catalogue, args.historical_policy)]
    decisions = build(baseline, catalogue, old)
    policy = compile_policy(baseline, decisions)
    for path, document in ((args.decisions, decisions), (args.policy, policy)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(document, indent=2, sort_keys=True) + '\n')
    print('routes', len(decisions['routes']), 'redirects', len(policy['definitions']), 'removals', len(policy['remove_ids']))


if __name__ == '__main__':
    main()
