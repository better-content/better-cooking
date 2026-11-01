#!/usr/bin/env python3
"""Bind KubeJS source JSON and live registry facts for a recipe-only migration."""
import argparse
import hashlib
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('source-json', 'live-recipes', 'edible', 'historical-policy', 'build-manifest', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    files = [args.source_json, args.live_recipes, args.edible, args.historical_policy, args.build_manifest]
    sources, live, edible, old, builds = [json.loads(p.read_text()) for p in files]
    if sources['schema'] != 'better_cooking:recipe_source_json.v1' or sources['errors']:
        raise ValueError('invalid source recipe capture')
    live_rows = {row['id']: row for row in live['recipes']}
    if len(live_rows) != live['recipe_count']:
        raise ValueError('duplicate/missing live recipe rows')
    recipes = {key: row for key, row in sources['recipes'].items() if key in live_rows}
    food_ids = set(edible['items'])
    affected = set(old['routes']) & set(recipes)
    for key, row in live_rows.items():
        if key in recipes and any(output.get('id') in food_ids for output in row.get('outputs', [])):
            affected.add(key)
    dependency_ids = {'create:crafting/appliances/slime_ball', 'powergrid:sequenced_assembly/unetched_circuit_board'}
    if not dependency_ids <= set(recipes):
        raise ValueError('canonical dough dependency source missing')
    affected.update(dependency_ids)
    protected = {key for key in affected if recipes[key].get('type') in {'hexerei:drying_rack', 'kaleidoscope_cookery:drying'}
                 or '/drying_rack/' in key or old['routes'].get(key, {}).get('disposition') == 'retain_nonfood'}
    result = {'schema': 'better_cooking:recipe_baseline.v1',
              'source_identity': {'edible_snapshot_id': edible['snapshot_id'], 'recipe_snapshot_id': live['snapshot_id'],
                                  'build_manifest_status': builds['status'], 'active_count': builds['active_count'],
                                  'inputs': [{'path': str(p.resolve()), 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in files]},
              'edible_items': edible['items'], 'recipes': recipes,
              'affected_recipe_ids': sorted(affected), 'protected_recipe_ids': sorted(protected - dependency_ids),
              'canonical_dependency_ids': sorted(dependency_ids),
              'pending_recipe_ids': sorted(set(sources.get('pending_ids', [])) & set(recipes)),
              'source_serializer_ids': {key: live_rows[key]['serializer'] for key in sorted(recipes)},
              'serializers': sorted({r['serializer'] for r in live_rows.values()}),
              'live_without_source_json': sorted(set(live_rows) - set(recipes)),
              'historical_not_live': sorted(set(old['routes']) - set(live_rows)),
              'historical_live_without_source_json': sorted((set(old['routes']) & set(live_rows)) - set(recipes))}
    if args.output.exists():
        raise ValueError('refusing to overwrite baseline')
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
    print('affected', len(affected), 'protected', len(protected), 'live without JSON', len(result['live_without_source_json']))


if __name__ == '__main__':
    main()
