"""Enumerate exact known duplicate-crop acquisition references; never heuristic-delete."""
from policy_rules import CROP_ALIASES


def strings(value):
    if isinstance(value, str): yield value
    elif isinstance(value, dict):
        for item in value.values(): yield from strings(item)
    elif isinstance(value, list):
        for item in value: yield from strings(item)


def acquisition_plan(frozen, authored=None):
    rows = []
    resources = frozen.get('acquisition_resources', {})
    for item, canonical in sorted(CROP_ALIASES.items()):
        if item not in frozen['items']: continue
        recipe_ids = sorted(rid for rid, row in frozen['recipes'].items() if any(o.get('id') == item for o in row['outputs']))
        resource_refs = sorted(name for name, raw in resources.items() if item in set(strings(raw)))
        rows.append({
            'item': item, 'canonical': canonical, 'authority': 'farmersdelight_bumblezone',
            'status': 'unresolved', 'registered_item': 'retain', 'finite_found_stock': 'retain',
            'known_recipe_output_ids': recipe_ids, 'known_resource_references': resource_refs,
            'recipe_disposition': 'require_exact_acquisition_gate_review_before_activation',
            'loot_disposition': 'retain_pending_exact_finite_vs_renewable_and_origin_review',
            'trade_disposition': 'not_enumerated_no_trade_removal_authorized',
            'worldgen_disposition': 'require_explicit_dimension_seed_and_wild_crop_origin_closure',
            'unresolved': [
                'current_effective_seed_and_crop_acquisition_not_closed',
                'renewable_vs_finite_loot_sources_need_live_origin_audit',
                'villager_wandering_trader_and_other_trade_sources_not_closed',
                'native_growth_harvest_and_Overworld_wild_crop_bypass_not_closed',
            ]})
    harvest_redirects = {}
    if authored is not None:
        if authored.get('schema') != 'better_cooking:authored_acquisition.v1': raise ValueError('wrong authored acquisition schema')
        for source, decision in authored.get('harvest_redirects', {}).items():
            if decision.get('scope') != 'new_native_harvest_output_only' or type(decision.get('source_count_multiplier')) is not int or decision['source_count_multiplier'] != 1:
                raise ValueError('acquisition redirect must preserve exact native count and exclude old stacks')
            if not decision.get('reason') or not decision.get('evidence'): raise ValueError('acquisition redirect needs source evidence')
            harvest_redirects[source] = dict(decision)
        for row in rows:
            if row['item'] in harvest_redirects:
                row['status'] = 'authored_pending_native'
                row['source_review'] = harvest_redirects[row['item']]
                row['loot_disposition'] = 'exact_new_output_canonicalization_then_existing_provider_authority'
                row['recipe_disposition'] = 'canonical_seed_output_then_existing_21_seed_recipe_origin_gate'
                row['trade_disposition'] = 'source_proven_chef_buys_not_seed_sales_native_compatibility_pending'
                row['worldgen_disposition'] = 'retain_finite_structural_forage_native_provider_origin_growth_guard_required'
    return {'schema': 'better_cooking:acquisition_plan.v1', 'status': 'unresolved' if rows else 'closed',
            'harvest_redirects': harvest_redirects,
            'authored_actions': authored.get('data_overrides', []) if authored else [],
            'trade_input_compatibility': authored.get('trade_input_compatibility', []) if authored else [],
            'worldgen': authored.get('worldgen') if authored else None,
            'rows': rows, 'known_resource_count': len(resources),
            'actions_authorized': [],
            'rule': 'No heuristic loot/trade/worldgen removal; unknown closure blocks qualification.'}
