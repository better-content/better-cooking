#!/usr/bin/env python3
"""Report source candidate collisions; NEVER remove or prioritize distinct recipes."""
import argparse
import hashlib
import json
from pathlib import Path
from compile_policy import resolve_tags
from extract_catalogue import encoded


def overlaps(frozen, policy):
    tags, tag_gaps = resolve_tags(frozen['item_tags'], frozen.get('resource_tags', {}))
    def members(groups):
        if len(groups) != 1: return set()
        return {item for edge in groups[0] for item in
                ([edge['id']] if edge['kind'] == 'item' else tags.get(edge['id'], []) if edge['kind'] == 'tag' else [])}
    legacy = {rid: row for rid, row in frozen['recipes'].items() if row['type'] == 'kaleidoscope_cookery:chopping_board'}
    result = []
    for rid, route in sorted(policy['routes'].items()):
        if (route.get('authored_review') or {}).get('adapter') != 'native_bulk_cut': continue
        source = frozen['recipes'].get(rid)
        if source is None: continue
        for lid, other in sorted(legacy.items()):
            common = sorted(members(source['input_groups']) & members(other['input_groups']))
            if not common: continue
            result.append({'reviewed_source_id': rid, 'legacy_kc_source_id': lid, 'candidate_common_items': common,
                'reviewed_input_groups': source['input_groups'], 'legacy_input_groups': other['input_groups'],
                'reviewed_ordered_outputs': source['outputs'], 'legacy_ordered_outputs': other['outputs'],
                'reviewed_tool': (source.get('raw') or {}).get('tool'),
                'legacy_native_tool_gate': {'tag': 'kaleidoscope_cookery:kitchen_knife', 'basis': 'native board public-source gate; actual shared tool and live selection not proven'},
                'reviewed_source_evidence': source['evidence'], 'legacy_source_evidence': other['evidence'],
                'disposition': 'selection_or_coexistence_requires_explicit_root_decision',
                'warning': 'Same ingredient is NOT output/anatomy/count equivalence. No deletion, alias, shadow priority or chosen recipe is authorized.'})
    return {'schema': 'better_cooking:cutting_source_overlap_report.v1', 'qualification': 'unqualified',
            'basis': 'explicit frozen source candidate graph and resolved source tags, NOT actual current RecipeManager lookup',
            'tag_gaps': tag_gaps, 'candidate_pairs': result,
            'required_native_control': 'Actual full-graph connected/native board lookup with intended paid item and genuine tool; isolated recipe fixtures do not prove selection.'}


def main():
    here = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, default=here / 'frozen/catalogue_inputs.json')
    parser.add_argument('--policy', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=here / 'reports/cutting-source-overlaps.json')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    result = overlaps(json.loads(args.input.read_text()), json.loads(args.policy.read_text()))
    result['inputs_sha256'] = {str(p.resolve()): hashlib.sha256(p.read_bytes()).hexdigest() for p in (args.input, args.policy)}
    text = encoded(result)
    if args.check:
        if not args.output.exists() or args.output.read_text() != text: raise SystemExit('cutting source overlap report differs')
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True); args.output.write_text(text)
    print(len(result['candidate_pairs']), 'source candidate pairs; no canonical deletion or live selection inferred')


if __name__ == '__main__': main()
