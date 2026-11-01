"""Independent draft-integrity/compact audit, NEVER approval or a native receipt."""
import argparse
import hashlib
import json
from pathlib import Path
import tomllib
import zipfile


def audit(draft_path, jar_path, descriptor_path, expected_draft_sha256):
    raw = draft_path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected_draft_sha256:
        raise ValueError('delivered draft bytes changed')
    draft = json.loads(raw)
    descriptor = tomllib.loads(descriptor_path.read_text())
    jar_bytes = jar_path.read_bytes()
    if descriptor['filename'] != jar_path.name:
        raise ValueError('exact descriptor filename mismatch')
    download = descriptor['download']
    if hashlib.new(download['hash-format'], jar_bytes).hexdigest() != download['hash']:
        raise ValueError('current descriptor artifact hash mismatch')
    digest = hashlib.sha256(jar_bytes).hexdigest()
    primary_inputs = {str(jar_path): digest, str(descriptor_path): hashlib.sha256(descriptor_path.read_bytes()).hexdigest()}
    for path, value in primary_inputs.items():
        if draft['source_inputs'].get(path) != value:
            raise ValueError('draft primary source mismatch: '+path)
    citations = 0
    with zipfile.ZipFile(jar_path) as archive:
        cache = {}
        def walk(value):
            nonlocal citations
            if isinstance(value, dict):
                if 'artifact' in value:
                    if value['artifact'] != str(jar_path) or value.get('artifact_sha256') != digest:
                        raise ValueError('unbound artifact citation')
                    entry = value.get('entry', value.get('member'))
                    entry_digest = value.get('entry_sha256', value.get('member_sha256'))
                    if entry not in cache: cache[entry] = hashlib.sha256(archive.read(entry)).hexdigest()
                    if cache[entry] != entry_digest: raise ValueError('entry digest mismatch: '+str(entry))
                    citations += 1
                for child in value.values(): walk(child)
            elif isinstance(value, list):
                for child in value: walk(child)
        walk(draft['foods']); walk(draft['routes'])
        food_route_citations = citations
        walk(draft.get('source_resources', [])); walk(draft.get('shared_source_mechanics', []))
        walk(draft.get('source_brewing_rules', [])); walk(draft.get('brewing_rules', []))
        for resource in draft.get('source_resources', []):
            entry = resource.get('source_entry', resource.get('member'))
            actual = archive.read(entry)
            original = resource.get('original_resource', resource.get('original_resource_json'))
            if json.loads(actual) != original:
                raise ValueError('supporting resource changed: '+entry)
            if 'original_resource_utf8' in resource and actual.decode('utf8') != resource['original_resource_utf8']:
                raise ValueError('supporting resource bytes changed: '+entry)
        route_ids = set()
        for row in draft['routes']:
            rid = row.get('recipe_id', row.get('source_id', row.get('id')))
            if rid in route_ids: raise ValueError('duplicate route '+rid)
            route_ids.add(rid)
            namespace, name = rid.split(':', 1)
            original = row.get('original_resource', row.get('original_resource_json'))
            actual = archive.read(f'data/{namespace}/recipes/{name}.json')
            if json.loads(actual) != original:
                raise ValueError('original recipe changed: '+rid)
            if 'original_resource_utf8' in row and actual.decode('utf8') != row['original_resource_utf8']:
                raise ValueError('original recipe bytes changed: '+rid)
    food_ids = [row['id'] for row in draft['foods']]
    if len(set(food_ids)) != len(food_ids): raise ValueError('duplicate food ID')
    return draft, {'status': 'INTEGRITY_ONLY_NOT_P4_APPROVAL_OR_NATIVE_ACCEPTANCE',
        'draft': str(draft_path), 'draft_sha256': expected_draft_sha256,
        'primary_source_inputs': primary_inputs, 'artifact_entry_citations_verified': citations,
        'food_route_citations_verified': food_route_citations,
        'whole_supporting_resources_verified': len(draft.get('source_resources', [])),
        'brewing_rule_citations_verified': len(draft.get('source_brewing_rules', [])) + len(draft.get('brewing_rules', [])),
        'food_ids': food_ids, 'route_ids': sorted(route_ids), 'native_acceptance': False}


def compact(draft):
    lines = ['# Per-ID proposed source decisions — NOT merged/approved/native facts', '']
    for row in draft['foods']:
        decision = row.get('decision', row.get('proposed_decision'))
        lines += [f"## {row['id']} — {decision}", row['reason'],
                  'Utility: '+json.dumps(row.get('utility_review'), ensure_ascii=False),
                  'Held: '+json.dumps(row.get('unresolved', row.get('unresolved_gates')), ensure_ascii=False), '']
    return '\n'.join(lines)+'\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ('draft', 'jar', 'descriptor', 'output'): parser.add_argument('--'+key, type=Path, required=True)
    parser.add_argument('--expected-draft-sha256', required=True)
    args = parser.parse_args()
    draft, report = audit(args.draft, args.jar, args.descriptor, args.expected_draft_sha256)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True)+'\n')
    args.output.with_suffix('.md').write_text(compact(draft))
    print(len(report['food_ids']), 'food IDs;', len(report['route_ids']), 'literal routes;', report['artifact_entry_citations_verified'], 'verified citations; NOT approval')

if __name__ == '__main__': main()
