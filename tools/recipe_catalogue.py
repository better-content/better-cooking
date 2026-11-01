#!/usr/bin/env python3
"""Build recipe-role catalogue from live default-stack foods and explicit role decisions."""
import argparse
import hashlib
import json
from pathlib import Path


def build(edible, registered, old, overrides):
    choices = {}
    for group in overrides['groups']:
        for item in group['items']:
            if item in choices:
                raise ValueError('duplicate role decision: ' + item)
            choices[item] = group
    if set(choices) - set(edible['items']):
        raise ValueError('role decisions refer to absent default-stack edibles')
    rows = {}
    for item, food in sorted(edible['items'].items()):
        previous = old['foods'].get(item, {})
        role = choices[item]['classification'] if item in choices else previous.get('role')
        if role not in {'ingredient', 'staple', 'meal', 'drink', 'special'}:
            raise ValueError('missing explicit recipe classification: ' + item)
        rows[item] = {'classification': role, 'no_change': True,
                      'reason': choices[item]['reason'] if item in choices else 'Retain previously authored recipe role; do not change native food properties.',
                      'food_properties': food,
                      'evidence': 'explicit_recipe_role_decision' if item in choices else 'historical_authored_role',
                      'snapshot_id': edible['snapshot_id']}
    other = {}
    for item in sorted(set(old['foods']) - set(rows)):
        other[item] = {'status': 'registered_nondefault_food_context' if item in registered['items'] else 'absent_from_current_registry',
                       'recipe_scope': 'include_only_if_an_affected_recipe_requires_it'}
    return {'schema': 'better_cooking:recipe_catalogue.v1', 'status': 'default_stack_roles_complete',
            'item_count': len(rows), 'items': rows, 'historical_only': other,
            'limitation': 'Default-stack food properties do not cover every contextual food variant or drink. No consumption/physiology qualification is implied.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ('edible', 'registries', 'historical-policy', 'roles', 'output'):
        parser.add_argument('--' + key, type=Path, required=True)
    args = parser.parse_args()
    paths = [args.edible, args.registries, args.historical_policy, args.roles]
    result = build(*(json.loads(p.read_text()) for p in paths))
    result['inputs'] = [{'path': str(p.resolve()), 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in paths]
    if args.output.exists():
        raise SystemExit('refusing to overwrite an earlier catalogue')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
    print('classified', result['item_count'], 'default-stack edibles;', len(result['historical_only']), 'historical-only entries')


if __name__ == '__main__':
    main()
