"""Production native admission is v2-only, externally bound and ALL-quarantined.

No v1 importer, serializer whitelist, resource normalization or native-food
mutation path remains. Structural helpers in native_import_v2 are UNTRUSTED.
"""
from collections.abc import Mapping
from native_import_v2 import SCHEMA, admit_bound, require_bound_catalogue
from source_input import require_source_baseline


def import_native(frozen, snapshot, *, binding=None):
    if not isinstance(snapshot, Mapping) or snapshot.get('schema') != SCHEMA:
        raise ValueError('wrong native snapshot schema; v2 only')
    if binding is None: raise ValueError('native observation binding required')
    return admit_bound(frozen, snapshot, binding)


def load_native(frozen, path, *, binding_path=None, binding_sha256=None):
    require_source_baseline(frozen)
    if binding_path is None or binding_sha256 is None:
        raise ValueError('native snapshot, binding and binding SHA required together')
    from native_observation_binding import verify_binding
    # r6: Root factory owns the ONE raw read and ONE authoritative shared parse.
    # Never supply an unsealed raw/path pair or a caller-owned parsed tree.
    cap = verify_binding(binding_path, binding_sha256, path)
    return import_native(frozen, cap.snapshot, binding=cap)
