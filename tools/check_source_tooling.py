#!/usr/bin/env python3
"""Run bounded pure source checks and refresh non-native receipts. No JVM/acceptance."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent


def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--policy', type=Path, required=True)
    parser.add_argument('--pack', type=Path, required=True)
    parser.add_argument('--kc-jar', type=Path, required=True)
    args = parser.parse_args()
    now = datetime.datetime.now(datetime.timezone.utc)
    run = HERE / 'reports' / ('source-check-' + now.strftime('%Y%m%dT%H%M%S%fZ'))
    run.mkdir(parents=True, exist_ok=False)
    commands = [
        ('pure-tests', [sys.executable, '-m', 'unittest', 'discover', '-s', 'tools/tests', '-v']),
        ('compiler-check', [sys.executable, 'tools/compile_policy.py', '--nonstandard-consumables', 'tools/authored/nonstandard_consumables.json', '--output', str(args.policy.resolve()), '--report', 'tools/reports/catalogue-report.json', '--check']),
        ('acquisition-check', [sys.executable, 'tools/build_acquisition_data.py', '--kc-jar', str(args.kc_jar.resolve()), '--pack', str(args.pack.resolve()), '--check']),
        ('overlap-check', [sys.executable, 'tools/report_cutting_overlaps.py', '--policy', str(args.policy.resolve()), '--check']),
        ('review-queue-check', [sys.executable, 'tools/report_review_queue.py', '--policy', str(args.policy.resolve()), '--check'])]
    checks = []
    for name, command in commands:
        process = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        log = run / (name + '.log'); log.write_text(process.stdout)
        row = {'name': name, 'command': command, 'exit': process.returncode, 'log': str(log), 'log_sha256': sha(log)}
        if name == 'pure-tests':
            count = re.search(r'Ran (\d+) tests', process.stdout)
            row['tests'] = int(count[1]) if count else None
        checks.append(row)
        print(name, process.returncode)
        if process.returncode:
            (run / 'failed-check.json').write_text(json.dumps(row, indent=2) + '\n')
            raise SystemExit('pure/source check failed; original log retained at ' + str(log))
    policy = json.loads(args.policy.read_text())
    report = json.loads((HERE / 'reports/catalogue-report.json').read_text())
    receipt = {'schema': 'better_cooking:source_tooling_check.v2', 'generated': now.isoformat(),
        'scope': 'pure Python/Node and deterministic SOURCE output checks ONLY', 'native_acceptance': False,
        'qualification': policy['qualification'], 'policy': str(args.policy.resolve()), 'policy_sha256': sha(args.policy),
        'foods': len(policy['foods']), 'routes': len(policy['routes']), 'checks': checks,
        'authored_reviewed_foods': report['authored_reviewed_food_count'],
        'unreviewed_foods': len(policy['foods']) - report['authored_reviewed_food_count'],
        'migration_errors': policy['migration']['errors'],
        'tool_sources_sha256': {str(p.relative_to(ROOT)): sha(p) for p in sorted(HERE.rglob('*.py'))}}
    text = json.dumps(receipt, indent=2, sort_keys=True) + '\n'
    (run / 'receipt.json').write_text(text)
    (HERE / 'reports/tooling-check.json').write_text(text)
    repro = dict(receipt, schema='better_cooking:source_reproducibility_check.v2',
                 warning='Not native/client qualification; historical root archives/evidence remain immutable.')
    (HERE / 'reports/reproducibility-check.json').write_text(json.dumps(repro, indent=2, sort_keys=True) + '\n')
    print('source-only receipts refreshed; policy remains', policy['qualification'])


if __name__ == '__main__': main()
