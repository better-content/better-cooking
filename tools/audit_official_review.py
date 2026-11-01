"""Offline exact official-cache draft integrity, not source approval/native facts."""
import hashlib
import json
from pathlib import Path
import zipfile


def audit(draft_path, expected_sha256, version_path):
    raw = draft_path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected_sha256: raise ValueError('delivered draft changed')
    draft = json.loads(raw); version = json.loads(version_path.read_text())
    if version['id'] != '1.20.1': raise ValueError('wrong exact Minecraft version')
    inputs = draft['source_inputs']
    expected_paths = {str(version_path), str(version_path.parent/'client.jar'), str(version_path.parent/'client_mappings.txt')}
    if set(inputs) != expected_paths: raise ValueError('unexpected primary cache authority')
    for path, digest in inputs.items():
        if hashlib.sha256(Path(path).read_bytes()).hexdigest() != digest: raise ValueError('primary byte mismatch '+path)
    for key, name in [('client','client.jar'),('client_mappings','client_mappings.txt')]:
        data = (version_path.parent/name).read_bytes(); record = version['downloads'][key]
        if len(data) != record['size'] or hashlib.sha1(data).hexdigest() != record['sha1']: raise ValueError('official cached download record mismatch')
    jar_path = version_path.parent/'client.jar'; count = 0
    with zipfile.ZipFile(jar_path) as jar:
        cache = {}
        def walk(value):
            nonlocal count
            if isinstance(value, dict):
                if 'artifact' in value:
                    if value['artifact'] != str(jar_path) or value['artifact_sha256'] != inputs[str(jar_path)]: raise ValueError('unbound artifact citation')
                    entry = value['entry']
                    if entry not in cache: cache[entry] = hashlib.sha256(jar.read(entry)).hexdigest()
                    if cache[entry] != value['entry_sha256']: raise ValueError('member digest mismatch')
                    count += 1
                for child in value.values(): walk(child)
            elif isinstance(value, list):
                for child in value: walk(child)
        walk(draft['foods']); walk(draft['routes'])
        route_ids = []
        for row in draft['routes']:
            rid = row.get('recipe_id',row.get('id',row.get('source_id'))); ns,name=rid.split(':',1)
            if row.get('source_entry',f'data/{ns}/recipes/{name}.json') != f'data/{ns}/recipes/{name}.json': raise ValueError('source recipe entry/identity mismatch')
            original = row.get('original_resource',row.get('original_resource_json'))
            if original != json.loads(jar.read(f'data/{ns}/recipes/{name}.json')): raise ValueError('original resource changed: '+rid)
            route_ids.append(rid)
    return {'status':'INTEGRITY_ONLY_NOT_P4_APPROVAL_OR_NATIVE_ACCEPTANCE', 'draft':str(draft_path), 'draft_sha256':expected_sha256,
        'primary_source_inputs':inputs, 'food_ids':[row['id'] for row in draft['foods']], 'route_ids':route_ids,
        'artifact_entry_citations_verified':count, 'native_acceptance':False,
        'trust_limitation':'Exact cached official manifest/download-record verification only; no fresh launcher-manifest authentication or Forge runtime observation.'}

if __name__ == '__main__':
    import argparse
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--draft',type=Path,required=True);p.add_argument('--expected-sha256',required=True)
    p.add_argument('--version',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    args=p.parse_args(); report=audit(args.draft,args.expected_sha256,args.version)
    args.output.write_text(json.dumps(report,indent=2,sort_keys=True)+'\n')
    print(len(report['food_ids']),'exact foods;',len(report['route_ids']),'whole recipes;',report['artifact_entry_citations_verified'],'citations; NOT approval')
