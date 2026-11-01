"""Closed Root observation-source provenance. NEVER physical/default/native acceptance.

This factory owns the one original bounded raw read and authoritative parse
through native_json. No caller-supplied raw/tree or I/O in capability accessors.
See docs/native-observation-v2.md (r6).
"""
from collections.abc import Mapping
from datetime import datetime, timezone
from decimal import Decimal
import hashlib
import io
import os
from pathlib import Path
import re
import stat
import uuid
import zipfile

from native_json import read_bounded, parse_raw

SCHEMA = 'better_cooking:native_observation_binding.v2'
OBSERVATION_SCHEMA = 'better_cooking:native_catalogue.v2'
SCOPE = 'observation_source_only_not_native_acceptance'
CONTROLLER_SHA = 'b4052850b59aea3cf83c172fc983a0a8992e63d0411e09ad890f7bea40b0677c'
R14_SHA = 'cea06aeb1f7ff03474254b0f6123bc1fe5cf5298a3805aedcaa24fe5fcc8c7b4'
PROOFS = ('metadata','inputs_before','client_before','client_joined','client_receipt','getter_uuid',
          'client_disconnected','one_connection','game_java_observed','display_storage_observed',
          'server_stop_proof','inputs_after','client_after','client_final','wrappers_reaped','census_final','handles_closed')
ROLES = frozenset(('cooking_main','native_fixture','physiology','water','heat','spice','commerce','bumblezone','kaleidoscope'))
_HEX = re.compile('[0-9a-f]{64}')
_HOME = Path('/home/dev')
_SEAL = object()


def _need(condition, message):
    if not condition: raise ValueError('native observation binding: ' + message)


def _keys(value, names):
    _need(isinstance(value, Mapping) and set(value) == set(names.split()), 'closed fields: ' + names)


def _sha(value):
    _need(type(value) is str and _HEX.fullmatch(value) is not None, 'SHA256 spelling')
    return value


def _integer(value, minimum=0):
    _need(type(value) is int and value >= minimum, 'integer type/range')
    return value


def _utc(value):
    _need(type(value) is str, 'UTC timestamp type')
    try: result = datetime.fromisoformat(value.replace('Z', '+00:00'))
    except ValueError as exc: raise ValueError('native observation binding: invalid UTC timestamp') from exc
    _need(result.tzinfo is not None and result.utcoffset() == timezone.utc.utcoffset(result), 'aware UTC required')
    return result


def _uuid(value):
    _need(type(value) is str, 'UUID type')
    try: parsed = uuid.UUID(value)
    except (ValueError, AttributeError) as exc: raise ValueError('native observation binding: invalid UUID') from exc
    _need(value in (str(parsed), parsed.hex), 'canonical lowercase UUID required')
    return parsed


def _finite(value):
    _need(type(value) in (int, Decimal) and (type(value) is int or value.is_finite()), 'finite exact JSON number')
    return value


class _FrozenDict(dict):
    __slots__ = ()
    def __new__(cls, values):
        result = dict.__new__(cls)
        dict.update(result, values)
        return result
    def __init__(self, values):
        # Construction happened in __new__; calling this later cannot mutate it.
        pass
    def _refuse(self, *args, **kwargs): raise TypeError('immutable observation')
    __setitem__ = __delitem__ = clear = pop = popitem = setdefault = update = __ior__ = _refuse
    def __copy__(self): return self
    def __deepcopy__(self, memo): return self


def _freeze(value):
    if isinstance(value, Mapping): return _FrozenDict({key: _freeze(item) for key, item in value.items()})
    if isinstance(value, (list, tuple)): return tuple(_freeze(item) for item in value)
    return value


class _ValidatedBinding:
    __slots__ = ('_raw', '_snapshot', '_provenance', '_seal')
    def __init__(self, raw, snapshot, provenance, seal):
        _need(seal is _SEAL, 'private capability construction')
        self._raw, self._snapshot, self._provenance, self._seal = raw, _freeze(snapshot), _freeze(provenance), seal
    @property
    def raw_bytes(self): return self._raw
    @property
    def snapshot(self): return self._snapshot
    @property
    def provenance(self): return self._provenance


def require_binding(value):
    _need(type(value) is _ValidatedBinding and value._seal is _SEAL, 'genuine private binding required')
    return value


def _path(value):
    _need(type(value) in (str, Path) or isinstance(value, Path), 'path type')
    spelling = os.fspath(value)
    _need(type(spelling) is str, 'text path required')
    p = Path(spelling)
    _need(spelling == str(p) and p.is_absolute() and '..' not in p.parts
          and spelling == os.path.normpath(spelling), 'canonical absolute path')
    _need(p.is_relative_to(_HOME), 'HOME path required')
    for component in (*reversed(p.parents), p):
        _need(not stat.S_ISLNK(os.lstat(component).st_mode), 'symlink component')
    st = p.stat()
    _need(stat.S_ISREG(st.st_mode) and st.st_uid == os.getuid() and st.st_nlink == 1, 'owned regular single-link evidence')
    return p, st


def _identity(st): return st.st_dev, st.st_ino, st.st_size, st.st_mtime_ns, st.st_ctime_ns


class _Evidence:
    """Read/hash each selected canonical evidence object once; no arbitrary graph walk."""
    def __init__(self): self.cache, self.identities = {}, {}
    def path(self, value):
        p, st = _path(value)
        inode = (st.st_dev, st.st_ino)
        _need(inode not in self.identities or self.identities[inode] == p, 'distinct-path inode alias')
        self.identities[inode] = p
        return p, st
    def bytes(self, value, expected):
        p, before = self.path(value); expected = _sha(expected)
        if p in self.cache:
            entry = self.cache[p]
            _need(entry['sha'] == expected and _identity(before) == entry['identity'], 'cached evidence changed')
            _need('raw' in entry, 'opaque evidence cannot become a second read')
            return entry['raw']
        raw = read_bounded(p)
        _, after = self.path(p)
        actual = hashlib.sha256(raw).hexdigest()
        _need(actual == expected and _identity(before) == _identity(after), 'evidence hash/read stability')
        self.cache[p] = {'sha': actual, 'bytes': len(raw), 'identity': _identity(after), 'raw': raw}
        return raw
    def json(self, value, expected):
        p, _ = self.path(value)
        raw = self.bytes(p, expected)
        entry = self.cache[p]
        if 'json' not in entry: entry['json'] = parse_raw(raw)
        return entry['json']
    def hashed(self, value, expected, size=None):
        p, before = self.path(value); expected = _sha(expected)
        if p in self.cache:
            entry = self.cache[p]
            _need(entry['sha'] == expected and entry['identity'] == _identity(before), 'cached physical row drift')
            total = entry['bytes']
        else:
            # Component-relative opens prevent symlink substitution during opaque streaming.
            directory = os.open('/', os.O_RDONLY | os.O_DIRECTORY)
            try:
                for part in p.parts[1:-1]:
                    nxt = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=directory)
                    os.close(directory); directory = nxt
                fd = os.open(p.name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=directory)
                try:
                    opened = os.fstat(fd)
                    _need(_identity(opened) == _identity(before) and stat.S_ISREG(opened.st_mode), 'physical row open changed')
                    digest, total = hashlib.sha256(), 0
                    while True:
                        chunk = os.read(fd, 1048576)
                        if not chunk: break
                        digest.update(chunk); total += len(chunk)
                        _need(total <= before.st_size, 'physical row grew during hash')
                    _need(_identity(os.fstat(fd)) == _identity(before), 'physical row changed during hash')
                finally: os.close(fd)
            finally: os.close(directory)
            _, after = self.path(p)
            _need(_identity(before) == _identity(after) and total == before.st_size and digest.hexdigest() == expected,
                  'physical row hash/identity mismatch')
            self.cache[p] = {'sha': expected, 'bytes': total, 'identity': _identity(after)}
        if size is not None: _need(total == _integer(size), 'physical row byte count')
        return p


def _event(report, name):
    _need(type(report['events']) is list, 'events array')
    rows = [row for row in report['events'] if isinstance(row, dict) and row.get('name') == name]
    _need(len(rows) == 1, 'exactly one ' + name)
    return rows[0]


def _command(report, command):
    rows = [row for row in report['events'] if isinstance(row, dict) and row.get('name') == 'console_command' and row.get('command') == command]
    _need(len(rows) == 1, 'exactly one console ' + command)
    return rows[0]


def _terminal(t, auth, binding, raw_sha, raw_size, actor):
    _need(t['schema'] == 'better_cooking:connected_getter_discovery.v4' and t['status'] == 'connected_getters_observed_not_qualified', 'connected terminal purpose')
    for key in ('acceptance','gameplay_acceptance','render_acceptance','current_physical_domain_qualified','defaults_installed'):
        _need(t[key] is False, 'observation-only terminal ' + key)
    _need(t['driver_sha256'] == CONTROLLER_SHA == auth['controller_sha256'], 'terminal driver')
    for key in ('client_metadata_sha256','server_metadata_sha256'): _need(t[key] == auth[key], 'terminal metadata ' + key)
    _need(t['startup_budget_seconds'] == 1200 and type(t['startup_budget_seconds']) is int and
          t['client_join_budget_seconds'] == 1200 and type(t['client_join_budget_seconds']) is int and
          t['normal_server_stop_seconds'] == 180 and type(t['normal_server_stop_seconds']) is int and
          t['one_client_connection_only'] is True, 'original lifecycle budgets')
    _need(isinstance(t['proofs'], dict) and set(t['proofs']) == set(PROOFS) and all(v is True for v in t['proofs'].values()), 'exact seventeen literal proofs')
    for key in ('errors','ownership_unknown','verified_known_remaining'): _need(type(t[key]) is list and not t[key], 'terminal ' + key)
    _need(t['server_normal_stop'] is True and t['runner_guard_released_after_cleanup'] is True, 'terminal cleanup/guard')
    s = t['server_stop']
    _keys(s, 'attempted command_attempted offset deadline failed proof')
    _need(s['attempted'] is True and s['command_attempted'] is True and s['proof'] is True and s['failed'] is False, 'one original stop proof')
    _integer(s['offset']); _need(_finite(s['deadline']) > 0, 'original absolute deadline')
    _need(type(t['wrapper_exit_codes']['server']) is int and t['wrapper_exit_codes']['server'] == 0, 'server exit integer zero')
    observed, saved = _event(t, 'native_catalogue_observed'), _event(t, 'server_saved_and_stopped')
    getter, stop = _command(t, 'cooking-fixture catalogue'), _command(t, 'stop')
    _need(observed['raw_sha256'] == raw_sha and type(observed['raw_bytes']) is int and observed['raw_bytes'] == raw_size, 'actual terminal raw bytes')
    _need(_uuid(observed['actual_server_player_uuid']) == actor == _uuid(t['identity']['uuid']), 'actual observed actor')
    _need(type(saved['exit_code']) is int and saved['exit_code'] == 0 and type(saved['original_offset']) is int and saved['original_offset'] == s['offset'], 'fresh save/original offset')
    created, finished = _utc(t['created_utc']), _utc(t['finished_utc'])
    times = [_utc(row['utc']) for row in (getter, observed, stop, saved)]
    _need(_utc(auth['created_utc']) <= created <= times[0] <= times[1] <= times[2] <= times[3] <= finished, 'terminal chronology')
    _need((times[3] - times[2]).total_seconds() <= 180, 'original stop budget')
    return observed


def _archive(e, manifest, manifest_path, build):
    _need(manifest['schema'] == 'better_cooking:discovery_input_freeze.v1' and manifest['status'] == 'FROZEN_NOT_PREPARED_NOT_RUN'
          and manifest['acceptance'] is False and manifest['qualification'] is False, 'complete frozen archive purpose')
    _need(type(manifest['source_files']) is dict and type(manifest['all_physical_frozen_rows']) is list, 'complete source/physical closure')
    # Parse explicitly selected JSON before opaque closure walking; never reread it.
    inventory = e.json(build['input_inventory_path'], build['input_inventory_sha256'])
    root, rows, sources = Path(manifest_path).parent, {}, {}
    for row in manifest['all_physical_frozen_rows']:
        _keys(row, 'source snapshot sha256 bytes')
        rel = Path(row['snapshot'])
        _need(type(row['snapshot']) is str and not rel.is_absolute() and '..' not in rel.parts and str(rel) == row['snapshot'], 'safe frozen relative path')
        _need(row['snapshot'] not in rows, 'duplicate frozen snapshot')
        e.hashed(root / rel, row['sha256'], row['bytes'])
        rows[row['snapshot']] = row
        sources.setdefault(row['source'], []).append(row)
    prefix = 'inputs/cooking-source/'
    copies = {name[len(prefix):]: row['sha256'] for name, row in rows.items() if name.startswith(prefix)}
    _need(copies == manifest['source_files'], 'exact source copy mapping')
    for name in ('build.gradle','settings.gradle','gradle.properties','docs/native-observation-v2.md',
                 'src/nativeFixture/java/com/bettercontent/bettercooking/fixture/NativeCatalogueCollector.java',
                 'src/nativeFixture/java/com/bettercontent/bettercooking/fixture/NativeRecipeIdentityTable.java',
                 'src/test/java/com/bettercontent/bettercooking/fixture/NativeRecipeIdentityTableTest.java'):
        _need(name in copies, 'required v2 source ' + name)
    _need(copies['docs/native-observation-v2.md'] == build['contract_sha256'], 'frozen contract/build equality')
    _need(inventory['schema'] == 'better_cooking.build_input_inventory.v1' and type(inventory['files']) is list
          and len(inventory['files']) == _integer(build['actual_input_count'], 1), 'actual source inventory')
    seen = set()
    for row in inventory['files']:
        _keys(row, 'path sha256 bytes'); _integer(row['bytes']); _sha(row['sha256'])
        _need(type(row['path']) is str and Path(row['path']).is_absolute() and '..' not in Path(row['path']).parts
              and str(Path(row['path'])) == row['path'], 'absolute canonical inventory path')
        _need(row['path'] not in seen, 'duplicate inventory path'); seen.add(row['path'])
        matches = [r for r in sources.get(row['path'], []) if r['sha256'] == row['sha256'] and r['bytes'] == row['bytes']]
        _need(len(matches) == 1, 'unambiguous complete frozen input mapping ' + row['path'])
    module = '/home/dev/mod_source/better-cooking/'
    required = {module + name for name in copies if name.startswith('src/') or name in ('build.gradle','settings.gradle','gradle.properties')}
    actual = {name for name in seen if name.startswith(module + 'src/') or name in {module + x for x in ('build.gradle','settings.gradle','gradle.properties')}}
    _need(actual == required, 'all actual src/build inputs closed')
    policy = '/home/dev/better-content-modpack/kubejs/config/culinary_policy.json'
    _need(policy in seen and build['policy_sha256'] == manifest['policy_sha256'] and
          any(row['path'] == policy and row['sha256'] == build['policy_sha256'] for row in inventory['files']), 'policy source closure')
    return manifest['runtime_roles']


def _build(e, b, auth):
    _keys(b, 'schema status acceptance created_utc all_recorded_inputs_unchanged actual_input_count input_inventory_path input_inventory_sha256 build_control_path build_control_sha256 build_verifier_path build_verifier_sha256 contract_path contract_sha256 policy_sha256 main_sha256 fixture_sha256 junit package_overlap')
    _need(b['schema'] == 'better_cooking:build_verification.v2' and b['status'] == 'BUILD_CHECKED_NOT_NATIVE_ACCEPTANCE'
          and b['acceptance'] is False and b['all_recorded_inputs_unchanged'] is True, 'v2 successful build purpose')
    _integer(b['actual_input_count'], 1); _need(type(b['package_overlap']) is list and not b['package_overlap'], 'main/fixture package isolation')
    _keys(b['junit'], 'tests failures errors skipped suites')
    for key, value in b['junit'].items(): _integer(value, 1 if key in ('tests','suites') else 0)
    _need(all(b['junit'][key] == 0 for key in ('failures','errors','skipped')), 'complete JUnit pass')
    _need(b['fixture_sha256'] == auth['runtime_fixture_sha256'] and b['main_sha256'] == auth['runtime_main_sha256']
          and b['contract_path'] == auth['contract_path'] and b['contract_sha256'] == auth['contract_sha256'], 'build/auth identities')
    e.hashed(b['build_verifier_path'], b['build_verifier_sha256'])
    control = e.json(b['build_control_path'], b['build_control_sha256'])
    _need(control['schema'] == 'better_cooking:source_build_control.v1' and control['status'] == 'BUILD_CHECKED_NOT_NATIVE_ACCEPTANCE'
          and type(control['gradle_exit']) is int and control['gradle_exit'] == 0 and control['artifact_verification'] is True
          and control['handles_closed'] is True and control['acceptance'] is False, 'actual source build control')
    for key in ('errors','ownership_unknown','verified_known_remaining'): _need(type(control[key]) is list and not control[key], 'build cleanup ' + key)
    _need(control['actual_test_worker_resource_flags'] == ['-XX:ActiveProcessorCount=2','-Xmx2g','-Djava.io.tmpdir=/home/dev/.tmp'], 'actual bounded test worker')
    _need(_utc(control['finished_utc']) <= _utc(b['created_utc']) <= _utc(auth['created_utc']), 'build chronology')


def _prepare(e, binding, auth, roles):
    cohort, root = binding['cohort'], Path(binding['run_root'])
    sm = e.json(cohort['server_metadata_path'], cohort['server_metadata_sha256'])
    cm = e.json(cohort['client_metadata_path'], cohort['client_metadata_sha256'])
    _need(sm['schema'] == 'bc.cooking.native_fixture.v1' and cm['schema'] == 'bc.cooking.native_client.v1'
          and sm['status'] == cm['status'] == 'PREPARED_NOT_RUN', 'preparation schemas/statuses')
    for key in ('acceptance','worlds_copied','credentials_copied'): _need(sm[key] is False and cm[key] is False, 'fresh preparation ' + key)
    _need(sm['lineage_state_copied_or_manually_initialized'] is False and cm['old_options_copied'] is False, 'fresh lineage/client')
    _need(sm['planned_command'] == ['./run.sh','nogui'] and sm['planned_cwd'] == str(root/'server')
          and sm['launch_contract'] == 'production_run_sh_lifecycle_supervisor' and sm['launch_target'] == 'forgeserver', 'supervised server launch')
    _need(cm['planned_cwd'] == str(root/'client') and cm['launch_target'] == 'forgeclient'
          and cm['discovery_only'] is True and cm['fixture_namespace_classification'] is True, 'discovery client launch')
    _need(cm['server_metadata'] == cohort['server_metadata_path'] and cm['server_metadata_sha256'] == cohort['server_metadata_sha256'], 'one client/server cohort')
    _need(sm['input_manifest'] == str(root/'server-inputs.json'), 'server input manifest path')
    inputs = e.json(sm['input_manifest'], sm['input_manifest_sha256'])
    _need(inputs['schema'] == 'bc.cooking.native_fixture.v1' and type(inputs['roles']) is dict and set(inputs['roles']) == ROLES
          and len(set(inputs['roles'].values())) == len(ROLES) and inputs['roles'] == cm['roles'] and set(roles) == ROLES, 'nine explicit matching roles')
    overlays = {}
    for row in inputs['overlays']:
        _need(row['destination'] not in overlays, 'duplicate overlay destination'); overlays[row['destination']] = row
    for role, destination in inputs['roles'].items():
        _need(type(destination) is str and destination.startswith('mods/') and '..' not in Path(destination).parts, 'role destination')
        frozen, overlay, meta = roles[role], overlays[destination], sm['inputs'][destination]
        expected = _sha(frozen['sha256'])
        _need(frozen['destination'] == destination and overlay['source'] == frozen['frozen'] and overlay['sha256'] == expected
              and meta['source'] == frozen['frozen'] and meta['sha256'] == meta['runtime_sha256'] == expected
              and sm['files'][destination] == cm['files'][destination] == expected, 'exact source/overlay/runtime role ' + role)
        e.hashed(frozen['frozen'], expected); e.hashed(root/'server'/destination, expected); e.hashed(root/'client'/destination, expected)
    _need(cm['preparation_manifest'] == str(root/'client-inputs.json') and cm['inputs'][cohort['server_metadata_path']] == cohort['server_metadata_sha256'], 'client preparation reference')
    prep = e.json(cm['preparation_manifest'], cm['inputs'][cm['preparation_manifest']])
    _need(prep['schema'] == 'bc.cooking.native_client.v1' and prep['roles'] == inputs['roles']
          and prep['server_metadata'] == cohort['server_metadata_path'] and prep['server_metadata_sha256'] == cohort['server_metadata_sha256'], 'client preparation manifest links')
    arguments = sm['jvm_arguments']
    _need(type(arguments) is list and arguments.count('-Dbetter_cooking.discoveryOnly=true') == 1
          and arguments.count('-Dbetter_cooking.nativeCatalogueOutput=' + str(root/'native-catalogue.json')) == 1
          and sum(x.startswith('-Dbetter_cooking.discoveryOnly=') for x in arguments) == 1
          and sum(x.startswith('-Dbetter_cooking.nativeCatalogueOutput=') for x in arguments) == 1, 'premain observation arguments')
    return cm


def verify_binding(binding_path, expected_binding_sha256, snapshot_path):
    """Three-argument factory. Own ONE bounded raw read and ONE strict parse."""
    _sha(expected_binding_sha256)
    try:
        return _verify(binding_path, expected_binding_sha256, snapshot_path)
    except (KeyError, TypeError, OSError, zipfile.BadZipFile, AttributeError) as exc:
        raise ValueError('native observation binding: malformed/unsafe closed evidence') from exc


def _verify(binding_path, expected_binding_sha256, snapshot_path):
    e = _Evidence(); binding = e.json(binding_path, expected_binding_sha256)
    _keys(binding, 'schema scope run_root invocation_nonce actor_uuid raw producer cohort')
    _need(binding['schema'] == SCHEMA and binding['scope'] == SCOPE, 'binding version/purpose')
    root = Path(binding['run_root']); _need(type(binding['run_root']) is str and binding['run_root'] == str(root)
          and root.is_absolute() and root.is_relative_to(_HOME) and '..' not in root.parts
          and str(root) == os.path.normpath(str(root)), 'canonical HOME cohort root')
    raw, producer, cohort = binding['raw'], binding['producer'], binding['cohort']
    _keys(raw, 'path sha256 bytes')
    _keys(producer, 'jar_path jar_sha256 source_manifest_path source_manifest_sha256 build_receipt_path build_receipt_sha256 contract_path contract_sha256')
    _keys(cohort, 'server_metadata_path server_metadata_sha256 client_metadata_path client_metadata_sha256 authorization_path authorization_sha256 controller_path controller_sha256 terminal_report_path terminal_report_sha256')
    _need(raw['path'] == str(snapshot_path) == str(root/'native-catalogue.json'), 'bound original raw path')
    # Factory owns BOTH the path/bytes pairing and bytes/tree pairing.
    raw_bytes = e.bytes(snapshot_path, raw['sha256'])
    snapshot = parse_raw(raw_bytes)
    _need(snapshot['schema'] == OBSERVATION_SCHEMA and snapshot['qualification'] is False and snapshot['discovery_only'] is True
          and snapshot['importable'] is True and snapshot['status'] == 'observed_unqualified', 'v2 observation envelope required')
    digest = raw['sha256']  # Already computed/checked by e.bytes on these original bytes.
    _need(digest != R14_SHA, 'archived diagnostic never admissible')
    _need(type(raw['bytes']) is int and raw['bytes'] == len(raw_bytes), 'bound original raw bytes')
    for key, suffix in (('server_metadata_path','server/fixture-metadata.json'),('client_metadata_path','client/client-metadata.json'),
                        ('authorization_path','root-single-run-authorization.json'),('terminal_report_path','connected-discovery.json')):
        _need(cohort[key] == str(root/suffix), 'fixed cohort ' + key)
    actor = _uuid(binding['actor_uuid']); nonce = _uuid(binding['invocation_nonce'])
    _need(str(nonce) == binding['invocation_nonce'] == snapshot['recipe_identity']['invocation_nonce'] and nonce.version == 4,
          'actual invocation-local nonce')
    _need(actor == _uuid(snapshot['context']['player']), 'actual raw actor')
    auth = e.json(cohort['authorization_path'], cohort['authorization_sha256'])
    _keys(auth, 'schema scope created_utc candidate controller controller_sha256 controller_review_path controller_review_sha256 source_review_path source_review_sha256 source_manifest_path source_manifest_sha256 build_receipt_path build_receipt_sha256 contract_path contract_sha256 server_metadata_sha256 client_metadata_sha256 runtime_fixture_sha256 runtime_main_sha256 raw_output_path expected_observation_schema budget_startup_seconds budget_join_seconds budget_catalogue_seconds normal_stop_one_attempt_seconds required_proofs shared_POSIX_guard_through_cleanup one_connection_only no_retry_reuse_reconnect acceptance')
    _need(auth['schema'] == 'better_cooking:root_native_observation_authorization.v2' and auth['scope'] == 'one_fresh_v2_observation_not_native_acceptance'
          and auth['candidate'] == str(root) and auth['raw_output_path'] == raw['path'] and auth['expected_observation_schema'] == OBSERVATION_SCHEMA
          and auth['acceptance'] is False and auth['required_proofs'] == list(PROOFS), 'explicit new v2 authorization')
    for key, expected in (('budget_startup_seconds',1200),('budget_join_seconds',1200),('budget_catalogue_seconds',180),('normal_stop_one_attempt_seconds',180)):
        _need(type(auth[key]) is int and auth[key] == expected, 'authorized original budget ' + key)
    for key in ('shared_POSIX_guard_through_cleanup','one_connection_only','no_retry_reuse_reconnect'): _need(auth[key] is True, 'authorized lifecycle ' + key)
    for key in ('source_manifest_path','source_manifest_sha256','build_receipt_path','build_receipt_sha256','contract_path','contract_sha256'):
        _need(auth[key] == producer[key], 'producer/auth ' + key)
    for key in ('server_metadata_sha256','client_metadata_sha256'): _need(auth[key] == cohort[key], 'authorized metadata ' + key)
    _need(auth['controller'] == cohort['controller_path'] and auth['controller_sha256'] == cohort['controller_sha256'] == CONTROLLER_SHA, 'exact supported controller')
    e.hashed(auth['controller'], CONTROLLER_SHA); e.hashed(producer['contract_path'], producer['contract_sha256'])
    review = e.json(auth['controller_review_path'], auth['controller_review_sha256'])
    _need(review['schema'] == 'better_cooking:root_controller_v4_static_review.v1' and review['controller_sha256'] == CONTROLLER_SHA
          and review['all_25_protected_ast_identical'] is True and type(review['root_read_complete_lines']) is list
          and all(type(n) is int for n in review['root_read_complete_lines']) and review['root_read_complete_lines'] == [1,784]
          and review['not_native_acceptance'] is True, 'actual bounded controller review')
    e.hashed(review['independent_review'], review['independent_review_sha256'])
    e.hashed(review['external_exact_display_trace'], review['external_exact_display_trace_sha256'])
    build = e.json(producer['build_receipt_path'], producer['build_receipt_sha256']); _build(e, build, auth)
    manifest = e.json(producer['source_manifest_path'], producer['source_manifest_sha256'])
    # Read producer bytes before the archive walk; subsequent closure checks reuse this read.
    jar = e.bytes(producer['jar_path'], producer['jar_sha256'])
    with zipfile.ZipFile(io.BytesIO(jar)) as z:
        names = z.namelist(); _need(len(names) == len(set(names)), 'duplicate producer JAR entries')
        code = z.read('com/bettercontent/bettercooking/fixture/NativeCatalogueCollector.class')
        _need(OBSERVATION_SCHEMA.encode() in code and b'better_cooking:recipe_identity_observation.v2' in code
              and 'com/bettercontent/bettercooking/fixture/NativeRecipeIdentityTable.class' in names
              and not any(n.endswith('NativeRecipeIdentityTableTest.class') for n in names), 'actual v2-only nonshipping producer marker')
    source_review = e.json(auth['source_review_path'], auth['source_review_sha256'])
    roles = _archive(e, manifest, producer['source_manifest_path'], build)
    _need(roles['native_fixture']['frozen'] == producer['jar_path'] and roles['native_fixture']['sha256'] == producer['jar_sha256'] == build['fixture_sha256']
          and roles['cooking_main']['sha256'] == build['main_sha256'], 'actual built frozen artifacts')
    _need(source_review['schema'] == 'better_cooking:root_native_v2_source_review.v1' and source_review['status'] == 'SOURCE_CHECKED_NOT_NATIVE_ACCEPTANCE'
          and source_review['acceptance'] is False and source_review['contract_sha256'] == producer['contract_sha256'], 'Root source-only review')
    for group in ('producer_source_files','consumer_source_files'):
        _need(type(source_review[group]) is dict and bool(source_review[group]), 'complete Root review source map')
        for relative, expected in source_review[group].items(): _need(manifest['source_files'][relative] == expected, 'review/frozen source ' + relative)
    client = _prepare(e, binding, auth, roles)
    terminal = e.json(cohort['terminal_report_path'], cohort['terminal_report_sha256'])
    _terminal(terminal, auth, binding, digest, len(raw_bytes), actor)
    _need(terminal['identity'] == client['identity'] and actor == _uuid(client['identity']['uuid']), 'same actual fresh client identity')
    changes = terminal['copied_runtime_file_changes']
    _need(type(changes) is list, 'unqualified runtime mutations retained')
    for change in changes:
        _keys(change, 'path before after')
        _need(type(change['path']) is str, 'runtime change path string')
        for key in ('before','after'):
            if change[key] is not None: _sha(change[key])
    provenance = {'schema':'better_cooking:native_observation_provenance.v2','scope':SCOPE,
                  'raw_path':raw['path'],'raw_sha256':digest,'raw_bytes_count':len(raw_bytes),
                  'binding_path':str(Path(binding_path)),'binding_sha256':expected_binding_sha256,
                  'contract_path':producer['contract_path'],'contract_sha256':producer['contract_sha256'],
                  'invocation_nonce':binding['invocation_nonce'],'actor_uuid':str(actor),'run_root':str(root),
                  'producer_jar_sha256':producer['jar_sha256'],'source_manifest_sha256':producer['source_manifest_sha256'],
                  'terminal_report_sha256':cohort['terminal_report_sha256'],
                  'copied_runtime_file_changes':terminal['copied_runtime_file_changes']}
    return _ValidatedBinding(raw_bytes, snapshot, provenance, _SEAL)
