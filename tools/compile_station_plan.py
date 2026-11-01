#!/usr/bin/env python3
"""Validate explicit station choices and stage a complete recipe-only installation."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
from recipe_policy import compile_policy, fingerprint
from recipe_station_migration import build_cutting_decisions, build_cooking_decisions, members, knife_tools_only


def review_retained_cut(source, raw, baseline, tags):
    if source in baseline['protected_recipe_ids']:
        return {'disposition': 'protected_nonfood', 'reason': 'Nonfood/dependency source stays on its installed surface.'}
    if any(x.get('chance', 1) != 1 for x in raw['result']):
        return {'disposition': 'probability_exception', 'reason': 'Native KC chopping has no probability field; retain the exact stochastic output vector.', 'outputs': raw['result']}
    if not knife_tools_only(raw['tool']):
        return {'disposition': 'distinct_tool', 'reason': 'Preserve this non-knife tool requirement instead of reinterpreting it as a kitchen knife.', 'tool': raw['tool']}
    accepted = members(raw['ingredients'][0], tags)
    if any('wild_' in x for x in accepted):
        return {'disposition': 'cultivated_acquisition_authority', 'reason': 'Keep wild-crop acquisition and its by-products under the existing FD/addon route.', 'inputs': sorted(accepted)}
    overlaps = {k: r['result'] for k, r in baseline['recipes'].items() if r['type'] == 'kaleidoscope_cookery:chopping_board' and members(r['ingredient'], tags) & accepted}
    if overlaps:
        return {'disposition': 'distinct_cut_format', 'reason': 'The same raw input already has a KC cut with a different item/yield vector. Preserve this FD format and its consumers; installing another KC match would make output selection ambiguous.', 'native_kc_outputs': overlaps, 'retained_outputs': raw['result']}
    raise ValueError('unreviewed retained food cut: ' + source)


def annotate(decisions, baseline, live, tags):
    graph = {row['id']: row for row in live['recipes']}
    by_output, vectors = {}, {}
    for key, row in graph.items():
        raw = baseline['recipes'].get(key, {})
        # The original graph exporter exposed only the primary cut output.
        # Use the captured full vector for comparisons, including every by-product.
        stacks = raw.get('results', raw.get('result', [])) if raw.get('type') in {'farmersdelight:cutting', 'kaleidoscope_cookery:chopping_board', 'ubesdelight:baking_mat'} else row.get('outputs', [])
        vectors[key] = [{'id': s.get('item', s.get('id')), 'count': s.get('count', 1), 'chance': s.get('chance', 1), 'nbt': s.get('nbt')} for s in stacks]
        by_output.setdefault(fingerprint(vectors[key]), []).append(key)
    for source, decision in decisions['routes'].items():
        native = graph[source]
        alternatives = sorted(set(by_output[fingerprint(vectors[source])]) - {source})
        decision['source_output_vector'] = vectors[source]
        decision['source_output_evidence'] = 'captured_full_native_output_vector' if baseline['recipes'][source]['type'] in {'farmersdelight:cutting', 'kaleidoscope_cookery:chopping_board', 'ubesdelight:baking_mat'} else 'native_recipe_graph'
        decision['source_recipe_facts'] = {key: copy.deepcopy(native.get(key)) for key in ('inputs', 'input_groups', 'outputs', 'catalysts', 'fluids_in', 'fluids_out', 'requirements', 'normalization')}
        decision['same_output_vector_routes'] = alternatives
        decision['alternative_comparisons'] = []
        for alternate in alternatives:
            other = graph[alternate]
            decision['alternative_comparisons'].append({
                'id': alternate, 'serializer': other['serializer'],
                'same_inputs': native.get('input_groups') == other.get('input_groups'),
                'same_fluids': native.get('fluids_in') == other.get('fluids_in'),
                'same_requirements': native.get('requirements') == other.get('requirements'),
                'selected_action': decisions['routes'].get(alternate, {}).get('action', 'outside_changed_source_scope_retained')})
        raw = baseline['recipes'][source]
        if decision['action'] == 'retain' and raw['type'] == 'farmersdelight:cutting':
            review = review_retained_cut(source, raw, baseline, tags)
            decision['retention_review'] = review
            decision['reason'] = review['reason']
        if decision['action'] == 'retain' and raw['type'] == 'farmersdelight:cooking' and decision.get('family') != 'native_station_reviewed_exception':
            result = raw['result']['item']
            if any(x in result for x in ('jelly', 'jam', 'marmalade')):
                reason = 'Existing fruit/sugar reduction and bottling, not a new soup or a new preservation process; preserve its formulation and existing container cost.'
            elif any(x in result for x in ('milk', 'tea', 'cider', 'cocoa', 'sauce', 'sugar', 'polvorone')):
                reason = 'Retain this ingredient/concentrate or beverage process and its exact native portion/container handling, rather than substitute stockpot water or stir-fry oil.'
            elif any(x in result for x in ('cake', 'pie', 'tart', 'muffin', 'custard', 'flan', 'halo_halo')):
                reason = 'Retain this native dessert preparation/ moist-heat route; it is not an equivalent stir-fry or soup. No artificial pot finishing step or unregistered raw pastry is introduced.'
            elif result in {'farmersdelight:stuffed_pumpkin_block', 'brewinandchewin:fiery_fondue_pot'}:
                reason = 'Preserve the assembled feast and its nonstandard external serving vessel and placed-portion behavior.'
            elif result in {'farmersdelight:cooked_rice', 'farmersdelight:dog_food', 'farmersdelight:cabbage_rolls', 'farmersdelight:dumplings', 'brewinandchewin:scarlet_pierogi'}:
                reason = 'Retain the distinct staple, pet-food, stuffed-wrap or dumpling preparation/portion format; these are not the selected soup or stir-fry formulations.'
            else:
                # No serializer-wide escape hatch: additional meals require an authored decision.
                raise ValueError('meal route still needs explicit station review: ' + source)
            decision['retention_review'] = {'disposition': 'distinct_native_process', 'result': raw['result'], 'reason': reason}
            decision['reason'] = reason
    return decisions


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('baseline', 'base-decisions', 'live', 'tags', 'native-facts', 'cutting-choices', 'cooking-choices', 'additional-cooking-choices', 'queued-cooking-choices', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    load = lambda path: json.loads(path.read_text())
    baseline, decisions, tags, facts = load(args.baseline), load(args.base_decisions), load(args.tags)['item_tags'], load(args.native_facts)
    cutting, cooking = load(args.cutting_choices)['choices'], load(args.cooking_choices)['choices']
    for path in (args.additional_cooking_choices, args.queued_cooking_choices):
        additional = load(path)['choices']
        if set(cooking) & set(additional): raise ValueError('duplicate authored cooking decision')
        cooking.update(additional)
    kitchen_knives = set(tags.get('kaleidoscope_cookery:kitchen_knife', []))
    forge_knives = set(tags.get('forge:tools/knives', []))
    if not forge_knives or not forge_knives <= kitchen_knives:
        raise ValueError('FD-family knife compatibility is not preserved by the native kitchen tag')
    live = load(args.live)
    graph = {row['id']: row for row in live['recipes']}
    source_knives = {}
    for source in cutting:
        accepted = set()
        for row in graph[source].get('catalysts', []):
            if row.get('semantic_path') != 'getTool()': continue
            if row.get('kind') == 'item': accepted.add(row['id'])
            elif row.get('kind') == 'tag': accepted.update(tags.get(row['id'], []))
        source_knives[source] = sorted(accepted)
        if not source_knives[source] or not set(source_knives[source]) <= kitchen_knives:
            raise ValueError('observed source knife alternatives lost: ' + source)
    decisions['routes'].update(build_cutting_decisions(baseline, tags, cutting))
    for source, knives in source_knives.items():
        decisions['routes'][source]['observed_source_knife_ids'] = knives
    decisions['routes'].update(build_cooking_decisions(baseline, tags, cooking, facts))
    annotate(decisions, baseline, live, tags)
    # Ensure newly authored fixed-formulation recipes do not conflict with one another.
    from recipe_station_migration import overlapping_formulations
    changed = [(k, r['definitions'][0]['json']) for k, r in decisions['routes'].items() if r.get('family') == 'canonical_native_cooking']
    for index, (key, raw) in enumerate(changed):
        for other_key, other in changed[index + 1:]:
            if raw['type'] == other['type'] and raw.get('soup_base') == other.get('soup_base') and overlapping_formulations(raw['ingredients'], other['ingredients'], tags):
                if {k:v for k,v in raw.items() if k != 'ingredients'} != {k:v for k,v in other.items() if k != 'ingredients'}:
                    raise ValueError('ambiguous migrated formulations: ' + key + ' / ' + other_key)
    policy = compile_policy(baseline, decisions)
    if args.output.exists(): raise ValueError('refusing to overwrite a prior staged candidate')
    args.output.mkdir(parents=True)
    (args.output/'decisions.json').write_text(json.dumps(decisions,indent=2,sort_keys=True)+'\n')
    config=args.output/'kubejs/config/cooking_recipes.json';config.parent.mkdir(parents=True);config.write_text(json.dumps(policy,indent=2,sort_keys=True)+'\n')
    for source, choice in cutting.items():
        item_namespace, item_path = choice['model_item'].split(':',1)
        for stage in range(4):
            model=args.output/('kubejs/assets/better_cooking/models/chopping_board/'+source.replace(':','/')+'/'+str(stage)+'.json')
            model.parent.mkdir(parents=True,exist_ok=True)
            model.write_text(json.dumps({'parent':item_namespace+':item/'+item_path},indent=2)+'\n')
    print(len(policy['definitions']), 'native/ingredient redirects;', len(policy['remove_ids']), 'exact removals')


if __name__ == '__main__': main()
