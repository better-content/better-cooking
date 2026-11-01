"""Derived review index only: never source authority or approval."""
import argparse
import collections
import hashlib
import json
from pathlib import Path


def review_queue(policy, digest):
    groups = collections.defaultdict(list)
    partial = []
    for item, row in sorted(policy['foods'].items()):
        if row['classification'] == 'authored_source_reviewed_pending_native': continue
        groups[item.split(':', 1)[0]].append(item)
        if row['classification'] == 'partially_authored_review_pending_native': partial.append(item)
    return {'schema': 'better_cooking:source_review_queue.v1',
            'status': 'DERIVED_INDEX_NOT_APPROVAL_OR_PRIMARY_SOURCE_AUTHORITY',
            'policy_sha256': digest, 'native_acceptance': False,
            'unreviewed_food_count': sum(map(len, groups.values())),
            'partially_reviewed_food_ids': partial, 'by_namespace': dict(groups)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--policy', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=Path(__file__).parent/'reports/remaining-review-queue.json')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args(); raw = args.policy.read_bytes()
    result = json.dumps(review_queue(json.loads(raw), hashlib.sha256(raw).hexdigest()), indent=2, sort_keys=True)+'\n'
    if args.check:
        if args.output.read_text() != result: raise SystemExit('review index is stale; NOT approval')
    else: args.output.write_text(result)

if __name__ == '__main__': main()
