"""Thirteen explicit source-bound signed intake holds, NOT a role/namespace rule.

Root approved these exact rows after P4's artifact audit. This cannot authorize
meal normalization, doses/nutrients, native getters, acceptance or resolved use.
"""
import json

# id -> source nutrition, saturation modifier, alwaysEat, reviewed source role
SIGNED_ROWS = {
    'fallout_wastelands_:absinthe': (-6, 6.0, True, 'drink'),
    'fallout_wastelands_:beer': (-2, 1.5, True, 'drink'),
    'fallout_wastelands_:coffee': (0, -.4, False, 'drink'),
    'fallout_wastelands_:gamma_gulp_beer': (-2, 2.5, True, 'drink'),
    'fallout_wastelands_:rad_island_cider': (-4, 1.5, True, 'drink'),
    'fallout_wastelands_:roentgen_rum': (-4, 1.5, True, 'drink'),
    'fallout_wastelands_:scotch': (-4, 1.5, True, 'drink'),
    'fallout_wastelands_:sierra_madre_martini': (-4, 1.5, True, 'drink'),
    'fallout_wastelands_:vodka': (-4, 1.5, True, 'drink'),
    'fallout_wastelands_:whiskey': (-4, 1.5, True, 'drink'),
    'fallout_wastelands_:wine': (-4, 1.5, True, 'drink'),
    'the_deep_void:knightfish': (-99, -99.0, True, 'unresolved'),
    'the_deep_void:voodoo_doll': (-4, -.4, True, 'unresolved'),
}
ARTIFACTS = {
    'drink': 'ced3c8a2fb902f60a98c3c9914e5f9d4ad070499644ef90a39f25420d594ace5',
    'unresolved': '018c80220b13abba85ddeba05a7d4850e2b989d9a14d6f008d65e2e94000f375',
}


def reviewed_retention(item, review, native_food):
    marker = review.get('signed_native_retention')
    if marker is None: return False
    if item not in SIGNED_ROWS: raise ValueError('signed native retention requires finite reviewed identity')
    hunger, saturation, always, role = SIGNED_ROWS[item]
    expected = {'nutrition': hunger, 'saturation_modifier': saturation, 'always_edible': always}
    if (not isinstance(native_food, dict) or native_food != expected
            or type(native_food.get('nutrition')) is not int
            or type(native_food.get('saturation_modifier')) is not float
            or native_food.get('always_edible') is not always
            or not isinstance(marker, dict)
            or marker.get('role_work_normalization_authorized') is not False
            or marker.get('native_acceptance') is not False or marker != {
            'basis': 'literal_source_values_not_observed_getters', 'native_food': expected,
            'role_work_normalization_authorized': False, 'native_acceptance': False}):
        raise ValueError('signed native retention source literals/claim changed: '+item)
    reviewed = set(review.get('reviewed', []))
    if review.get('role') != role or review.get('work_class') != 'unresolved' or reviewed != ({'role'} if role == 'drink' else set()):
        raise ValueError('signed native retention role/work holds changed: '+item)
    if not review.get('unresolved') or not review.get('reason'):
        raise ValueError('signed native retention requires explicit unresolved source evidence: '+item)
    citations = []
    for value in review.get('evidence', []):
        try: citations.append(json.loads(value))
        except (TypeError, ValueError): continue
    if not any(isinstance(c, dict) and c.get('artifact_sha256') == ARTIFACTS[role]
               and str(c.get('entry', c.get('member', ''))).endswith('.class')
               and len(c.get('entry_sha256', c.get('member_sha256', ''))) == 64 for c in citations):
        raise ValueError('signed native retention lacks exact primary class citation: '+item)
    return True


def validate_profile(food):
    review = food.get('authored_review', {})
    if not reviewed_retention(food['id'], review, food.get('native_food')): return False
    hunger, saturation, _, role = SIGNED_ROWS[food['id']]
    if (food['role'] != role or food['nutrition']['hunger'] != hunger
            or food['nutrition']['saturation_modifier'] != saturation
            or food['quantity_basis'] != 'native_intake' or food['nutrition_basis'] != 'retained_native'
            or not food['unresolved'] or 'authored_work_class_requires_explicit_decision' not in food['unresolved']):
        raise ValueError('signed native retention forbids normalized/clamped/resolved intake: '+food['id'])
    return True
