#!/usr/bin/env python3
"""Compile explicit recipe-only decisions; unrelated gameplay qualifications are not inputs."""
import argparse
import copy
import hashlib
import json
from pathlib import Path

SCHEMA = 'better_cooking:recipe_policy.v1'
ROLES = {'ingredient', 'staple', 'meal', 'drink', 'special', 'native_unchanged'}


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()).hexdigest()


def compile_policy(baseline, decisions):
    if baseline.get('schema') != 'better_cooking:recipe_baseline.v1':
        raise ValueError('wrong baseline schema')
    if decisions.get('schema') != 'better_cooking:recipe_decisions.v1':
        raise ValueError('wrong decisions schema')
    if decisions.get('baseline_sha256') != fingerprint(baseline):
        raise ValueError('stale baseline')
    foods = decisions.get('foods', {})
    if set(foods) != set(baseline['edible_items']):
        raise ValueError('catalogue must cover exactly every live default-stack edible')
    for item, row in foods.items():
        if row.get('classification') not in ROLES or not row.get('reason'):
            raise ValueError('missing food classification or reason: ' + item)
        if type(row.get('no_change')) is not bool:
            raise ValueError('food requires explicit no_change: ' + item)
    routes = decisions.get('routes', {})
    if set(routes) != set(baseline['affected_recipe_ids']):
        raise ValueError('route decisions must cover exactly the affected live recipes')
    definitions = {}; removals = []; source_hashes = {}
    serializers = set(baseline['serializers'])
    for source, row in sorted(routes.items()):
        raw = baseline['recipes'].get(source)
        if raw is None:
            raise ValueError('source recipe missing: ' + source)
        if row.get('source_sha256') != fingerprint(raw):
            raise ValueError('source recipe changed: ' + source)
        if not row.get('reason'):
            raise ValueError('route needs a reason: ' + source)
        action = row.get('action')
        replacements = row.get('definitions', [])
        if action not in {'retain', 'redirect', 'replace', 'remove'}:
            raise ValueError('unresolved route action: ' + source)
        if action == 'retain' and replacements:
            raise ValueError('retained route cannot emit definitions')
        if action in {'redirect', 'replace'} and not replacements:
            raise ValueError('changed route lacks replacement')
        if action == 'remove':
            target = row.get('canonical_recipe')
            if replacements or target == source or target not in routes or routes[target].get('action') != 'retain':
                raise ValueError('removal requires an identified retained canonical route')
        if action == 'redirect' and (len(replacements) != 1 or replacements[0].get('id') != source):
            raise ValueError('redirect must preserve recipe ID')
        if source in baseline.get('protected_recipe_ids', []) and action != 'retain':
            raise ValueError('protected nonfood/drying recipe cannot change: ' + source)
        source_hashes[source] = fingerprint(raw)
        for definition in replacements:
            recipe_id = definition['id']; value = definition['json']; serializer = value.get('type')
            if serializer not in serializers or serializer.startswith('better_cooking:'):
                raise ValueError('unsupported or custom preparation serializer: ' + str(serializer))
            if recipe_id in definitions or (recipe_id in baseline['recipes'] and not (action == 'redirect' and recipe_id == source)):
                raise ValueError('duplicate/colliding recipe ID: ' + recipe_id)
            if not definition.get('preservation_review'):
                raise ValueError('replacement needs explicit quantity/NBT/container review')
            definitions[recipe_id] = copy.deepcopy(value)
        if action in {'replace', 'remove'}:
            removals.append(source)
    return {'schema': SCHEMA, 'enabled': True, 'baseline_sha256': fingerprint(baseline),
            'source_identity': copy.deepcopy(baseline['source_identity']),
            'catalogue': copy.deepcopy(foods), 'routes': copy.deepcopy(routes),
            'source_recipe_hashes': source_hashes,
            'expected_sources': {k: copy.deepcopy(baseline['recipes'][k]) for k in sorted(routes)},
            'pending_source_ids': sorted(set(baseline.get('pending_recipe_ids', [])) & set(routes)),
            'definitions': [{'id': k, 'json': v} for k, v in sorted(definitions.items())],
            'remove_ids': sorted(removals)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ('baseline', 'decisions', 'output'):
        parser.add_argument('--' + key, type=Path, required=True)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    result = compile_policy(json.loads(args.baseline.read_text()), json.loads(args.decisions.read_text()))
    text = json.dumps(result, indent=2, sort_keys=True, allow_nan=False) + '\n'
    if args.check:
        if not args.output.exists() or args.output.read_text() != text:
            raise SystemExit('stale recipe policy')
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text)


if __name__ == '__main__':
    main()
