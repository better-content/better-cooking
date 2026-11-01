#!/usr/bin/env python3
"""Compare live edible/recipe IDs with a historical culinary policy; never install recipes."""
import argparse
import hashlib
import json
from pathlib import Path


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def audit(edible, recipes, policy, *, edible_sha256, recipes_sha256):
    if edible.get('schema') != 'bc.edible_items.v1' or edible.get('item_count') != len(edible.get('items', {})):
        raise ValueError('invalid live edible inventory')
    if recipes.get('recipe_count') != len(recipes.get('recipes', [])):
        raise ValueError('invalid live recipe inventory')
    if policy.get('schema') != 'better_cooking:culinary_policy.v1':
        raise ValueError('invalid historical policy')
    live_rows = recipes['recipes']
    recipe_ids = [row['id'] for row in live_rows]
    if len(recipe_ids) != len(set(recipe_ids)):
        raise ValueError('duplicate live recipe ID')
    historical_foods = policy['foods']
    catalogue = {}
    for item_id, food in sorted(edible['items'].items()):
        previous = historical_foods.get(item_id)
        role = previous.get('role', 'unreviewed') if previous else 'unreviewed'
        catalogue[item_id] = {'classification': role, 'status': 'review_required' if role in ('unresolved', 'unreviewed') else 'source_candidate_not_recipe_approved',
                              'recipe_change': 'undecided' if role in ('unresolved', 'unreviewed') else 'no_change_unless_route_decision',
                              'food_properties': food}
    routes = policy['routes']
    migration = policy.get('migration', {})
    canonical_ids = set(migration.get('canonical_bulk_rewrite_ids', [])) | set(migration.get('native_cut_rewrite_ids', []))
    loaded = set(recipe_ids)
    queued = []
    for item_id, row in catalogue.items():
        if row['status'] == 'review_required':
            queued.append({'kind': 'food_classification', 'id': item_id, 'reason': row['classification']})
    for row in live_rows:
        source_id = row['id']
        if source_id not in routes:
            continue
        old = routes[source_id]
        disposition = old.get('disposition', 'unresolved')
        # Historical physiology/qualification holds on unchanged native recipes
        # are not migration work. Only routes to be changed need serializer review.
        issues = []
        if disposition == 'unresolved':
            issues.append('unresolved_route_disposition')
        if disposition == 'replace' or source_id in canonical_ids:
            replacement = old.get('replacement') or {}
            if disposition == 'replace' and not replacement.get('id'):
                issues.append('missing_replacement_id')
            issues.append('old_preparation_serializer_not_recipe_only_approved')
            issues.extend(old.get('unresolved', []))
        if issues:
            queued.append({'kind': 'recipe_route', 'id': source_id, 'type': row.get('type'),
                           'serializer': row.get('serializer'), 'historical_disposition': disposition,
                           'reasons': sorted(set(issues))})
    missing = sorted(set(routes) - loaded)
    for source_id in missing:
        queued.append({'kind': 'historical_recipe_not_live', 'id': source_id, 'reason': 'review_before_removal_or_redirect'})
    return {'schema': 'better_cooking:recipe_scope_audit.v1', 'status': 'review_required_not_installable',
            'edible_sha256': edible_sha256, 'recipes_sha256': recipes_sha256,
            'edible_snapshot_id': edible['snapshot_id'], 'recipe_snapshot_id': recipes['snapshot_id'],
            'catalogue_count': len(catalogue), 'live_recipe_count': len(live_rows),
            'historical_route_count': len(routes), 'historical_routes_not_live': len(missing),
            'catalogue': catalogue, 'review_queue': queued}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ('edible', 'recipes', 'policy', 'output'):
        parser.add_argument('--' + option, type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('refusing to overwrite a previous audit')
    result = audit(json.loads(args.edible.read_text()), json.loads(args.recipes.read_text()),
                   json.loads(args.policy.read_text()), edible_sha256=digest(args.edible), recipes_sha256=digest(args.recipes))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print('catalogue:', result['catalogue_count'], 'routes not live:', result['historical_routes_not_live'],
          'review queue:', len(result['review_queue']))


if __name__ == '__main__':
    main()
