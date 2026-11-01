"""Replay explicit P4 per-ID decisions; never discover/approve a namespace."""
import copy
import hashlib
import json
from pathlib import Path


def merge_family_reviews(document, frozen, path, lock):
    raw = Path(path).read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if lock.get(str(Path(path))) != digest:
        raise ValueError('approved source-family document changed; explicit re-review required')
    family = json.loads(raw)
    if family.get('schema') != 'better_cooking:approved_source_family_reviews.v1':
        raise ValueError('invalid source-family review document')
    prior_foods = {item for group in document['families'] for item in group['items']} | set(document['foods'])
    if prior_foods & set(family['foods']): raise ValueError('source-family food overlaps prior decisions')
    if set(document['routes']) & set(family['routes']): raise ValueError('source-family route overlaps prior decisions')
    if set(family['foods']) - set(frozen['foods']): raise ValueError('source-family food not declared')
    if set(family['routes']) - set(frozen['recipes']): raise ValueError('source-only candidate cannot be promoted to effective route')
    bindings = {**family['source_inputs'], str(Path(path)): digest}
    for name, expected in bindings.items():
        if lock.get(name) != expected or frozen['manifest'].get(name) != expected:
            raise ValueError('source-family input not locked and frozen: '+name)
        if hashlib.sha256(Path(name).read_bytes()).hexdigest() != expected:
            raise ValueError('source-family actual source bytes changed: '+name)
    output = copy.deepcopy(document)
    output['foods'].update(copy.deepcopy(family['foods']))
    output['routes'].update(copy.deepcopy(family['routes']))
    output['source_inputs'].update(bindings)
    output['source_family_reviews'] = {
        'status': family['status'], 'review_origins': family['review_origins'],
        'source_route_candidates': family['source_route_candidates'],
        'native_acceptance': False}
    return output
