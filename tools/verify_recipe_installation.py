#!/usr/bin/env python3
"""Check actual post-install KubeJS sources, live outputs, food properties and tags."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
from recipe_policy import fingerprint


def verify(baseline, policy, captured, live, edible, tags, native):
    if captured.get('errors'):
        raise ValueError('source capture incomplete')
    actual = captured['recipes']
    expected = dict(baseline['recipes'])
    # The KubeJS iterator exposes originals until the event commits. Native
    # serializer-byte verification below checks the actual committed changes.
    changed_ids = {r['id'] for r in policy['definitions']}
    if native.get('schema') != 'bc.recipe_policy_verification.v1' or native.get('status') != 'passed':
        raise ValueError('native committed recipe verification failed')
    if {r['id'] for r in native['definitions']} != changed_ids or native['definition_count'] != len(changed_ids):
        raise ValueError('native definition coverage differs')
    for row in native['definitions']:
        if row['status'] != 'passed' or row['expected_network_sha256'] != row['actual_network_sha256']:
            raise ValueError('native serialized recipe differs: ' + row['id'])
    missing = set(expected) - set(actual)
    changed = [key for key in sorted(set(expected) & set(actual)) if expected[key] != actual[key]]
    if missing or changed:
        raise ValueError('installed recipes differ: missing=' + repr(sorted(missing)) + ' changed=' + repr(changed))
    if baseline['edible_items'] != edible['items']:
        raise ValueError('default-stack food properties changed')
    for tag, item in [('forge:flour/wheat', 'kaleidoscope_cookery:flour'), ('forge:dough/wheat', 'kaleidoscope_cookery:raw_dough')]:
        if item not in tags['item_tags'][tag]: raise ValueError('canonical ingredient absent from ' + tag)
    runtime = {r['id']: r for r in live['recipes']}
    for row in policy['definitions']:
        key = row['id']
        if key not in runtime or runtime[key]['serializer'] != row['json']['type']:
            raise ValueError('missing/wrong loaded serializer: ' + key)
        # Runtime extractor has its own independently observed output registry IDs.
        wanted = row['json'].get('results', row['json'].get('result', []))
        if not isinstance(wanted, list): wanted = [wanted]
        wanted_ids = {x if isinstance(x, str) else x.get('item') for x in wanted}
        observed = {x.get('id') for x in runtime[key].get('outputs', [])}
        if wanted_ids - observed:
            raise ValueError('loaded outputs differ: ' + key + ' ' + repr(wanted_ids - observed))
        wanted_counts, observed_counts = Counter(), Counter()
        for stack in wanted:
            wanted_counts[stack if isinstance(stack, str) else stack.get('item')] += 1 if isinstance(stack, str) else stack.get('count', 1)
        for stack in runtime[key].get('outputs', []):
            observed_counts[stack.get('id')] += stack.get('count', 1)
        if wanted_counts != observed_counts:
            raise ValueError('loaded output quantities differ: ' + key)
    return {'schema': 'better_cooking:recipe_installation.v1', 'status': 'passed',
            'baseline_sha256': fingerprint(baseline), 'policy_sha256': fingerprint(policy),
            'unchanged_recipe_count': len(baseline['recipes']) - len(policy['definitions']) - len(policy['remove_ids']),
            'definition_count': len(policy['definitions']), 'edible_count': len(edible['items']),
            'live_recipe_snapshot': live['snapshot_id'],
            'limitation': 'Server recipe installation check, not Smoke, Tele Soak, visual or culinary gameplay acceptance.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('baseline', 'policy', 'capture', 'live', 'edible', 'tags', 'native', 'installer', 'build-manifest', 'output'):
        parser.add_argument('--'+name, type=Path, required=True)
    args = parser.parse_args()
    paths = [args.baseline, args.policy, args.capture, args.live, args.edible, args.tags, args.native]
    native = json.loads(args.native.read_text())
    if native['policy_file_sha256'] != hashlib.sha256(args.policy.read_bytes()).hexdigest():
        raise ValueError('native check bound to a different policy file')
    result = verify(*(json.loads(p.read_text()) for p in paths))
    server_root = args.installer.resolve().parents[3]
    manifest = json.loads(args.build_manifest.read_text())
    runtime_inputs = {}
    for row in manifest['rows']:
        jar = server_root / 'mods' / Path(row['deployed_path']).name
        digest = hashlib.sha256(jar.read_bytes()).hexdigest()
        if digest != row['deployed_sha256']:
            raise ValueError('fixture active JAR differs from deployment: ' + str(jar))
        runtime_inputs[jar.name] = digest
    result['active_runtime_jars'] = runtime_inputs
    result['build_manifest_sha256'] = hashlib.sha256(args.build_manifest.read_bytes()).hexdigest()
    result['installer_sha256'] = hashlib.sha256(args.installer.read_bytes()).hexdigest()
    result['files'] = [{'path': str(p.resolve()), 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in paths + [args.installer, args.build_manifest]]
    if args.output.exists(): raise ValueError('refusing to overwrite evidence')
    args.output.write_text(json.dumps(result, indent=2)+'\n')
    print(result['status'], result['definition_count'], 'definitions;', result['unchanged_recipe_count'], 'unchanged sources')


if __name__ == '__main__':
    main()
