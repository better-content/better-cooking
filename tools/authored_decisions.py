"""Explicit evidence-backed authored reviews, separate from discovery classifiers."""
import copy
import hashlib
from extract_catalogue import encoded

SCHEMA = 'better_cooking:authored_decisions.v1'


def expand_decisions(document):
    if document.get('schema') != SCHEMA: raise ValueError('wrong authored decisions schema')
    foods, routes = {}, copy.deepcopy(document.get('routes', {}))
    for family in document.get('families', []):
        if not family.get('reason') or not family.get('evidence'): raise ValueError('authored family requires evidence and reason')
        if not isinstance(family.get('items'), list) or not family['items']: raise ValueError('authored family requires enumerated IDs')
        for item in family['items']:
            if item in foods: raise ValueError('duplicate authored food decision: ' + item)
            decision = copy.deepcopy(family['decision'])
            decision.update({'review_id': family['id'], 'reason': family['reason'],
                             'evidence': list(family['evidence']) + list(family.get('item_evidence', {}).get(item, []))})
            foods[item] = decision
    for item, decision in document.get('foods', {}).items():
        if not decision.get('reason') or not decision.get('evidence'): raise ValueError('authored food requires evidence and reason: ' + item)
        previous = foods.get(item, {})
        previous.update(copy.deepcopy(decision)); foods[item] = previous
    for item, decision in foods.items():
        for field in decision.get('reviewed', []):
            if field in ('role', 'work_class', 'adapter', 'moisture') and field not in decision:
                raise ValueError('authored review approves a missing field: ' + item + '/' + field)
        if 'moisture' in decision and not {'hydration', 'quenching', 'class'} <= set(decision['moisture']):
            raise ValueError('authored moisture needs explicit values/class: ' + item)
    for source, decision in routes.items():
        if not decision.get('reason') or not decision.get('evidence'): raise ValueError('authored route requires evidence and reason: ' + source)
    return foods, routes


def verify_source_inputs(document, frozen):
    bindings = document.get('source_inputs')
    if not isinstance(bindings, dict) or not bindings: raise ValueError('authored review requires explicit source hash bindings')
    for path, digest in bindings.items():
        if frozen.get('manifest', {}).get(path) != digest:
            raise ValueError('authored review source is not bound to this frozen input: ' + path)


def decision_provenance(document):
    foods, routes = expand_decisions(document)
    return {'schema': SCHEMA, 'sha256': hashlib.sha256(encoded(document).encode()).hexdigest(),
            'food_decisions': len(foods), 'route_decisions': len(routes), 'source_inputs': document.get('source_inputs', {}),
            'source_family_reviews': copy.deepcopy(document.get('source_family_reviews'))}
