"""Positive pinned SOURCE origin. Never a native importer or acceptance receipt."""
import copy
from decimal import Decimal
import hashlib
from extract_catalogue import encoded
from native_json import read_bounded, parse_raw

SOURCE_SHA256 = '3a853c1495d4e81d774aa9adeb8c42f93d1f2ee3d51c7c13a7a0cdf89c765a88'
SOURCE_BYTES = 7710745
DECLARATION_SHA256 = 'c20e97df2ccaa8e35cc904099acbf1b38381ed2bdcc912b1e74fca073f930e14'
DECLARATION_BYTES = 31121
COMPOSED_SHA256 = '0199028d0fa7dfe1707772ea4ce55eb526bea09b7b574979953577e9014259b3'
COMPOSED_BYTES = 7801654
_DECLARATION_SOURCE_ID = '/home/dev/mod_source/better-cooking/tools/authored/nonstandard_consumables.json'


def _source_api():
    secret = object()

    class SourceBaseline(dict):
        __slots__ = ('_original_sha256', '_declaration_sha256')

        def __init__(self, model, token, declaration_sha256=None):
            if token is not secret: raise ValueError('source capability constructor is private')
            super().__init__(model)
            self._original_sha256 = SOURCE_SHA256
            self._declaration_sha256 = declaration_sha256

        def __copy__(self): return dict(self)
        def __deepcopy__(self, memo): return copy.deepcopy(dict(self), memo)

    def legacy_model(value):
        # Permitted ONLY after exact legacy source raw SHA/size validation.
        if isinstance(value, Decimal): return float(value)
        if isinstance(value, dict): return {k: legacy_model(v) for k, v in value.items()}
        if isinstance(value, list): return [legacy_model(v) for v in value]
        return value

    def require_source_baseline(value):
        if type(value) is not SourceBaseline or value._original_sha256 != SOURCE_SHA256:
            raise ValueError('genuine pinned SourceBaseline required')
        if value._declaration_sha256 is None:
            expected, size = SOURCE_SHA256, SOURCE_BYTES
        elif value._declaration_sha256 == DECLARATION_SHA256:
            expected, size = COMPOSED_SHA256, COMPOSED_BYTES
        else:
            raise ValueError('unknown source composition provenance')
        try:
            raw_model = encoded(value).encode('utf-8')
        except (TypeError, ValueError, UnicodeError, RecursionError) as exc:
            raise ValueError('invalid pinned source content') from exc
        if len(raw_model) != size or hashlib.sha256(raw_model).hexdigest() != expected:
            raise ValueError('pinned source content changed')
        return value

    def load_source_baseline(path, *, nonstandard_path=None):
        raw = read_bounded(path)
        if len(raw) != SOURCE_BYTES or hashlib.sha256(raw).hexdigest() != SOURCE_SHA256:
            raise ValueError('source baseline raw SHA/size mismatch')
        model = legacy_model(parse_raw(raw))
        # Validate the base canonical pin independently BEFORE any composition.
        require_source_baseline(SourceBaseline(model, secret))
        declaration_sha = None
        if nonstandard_path is not None:
            declaration_raw = read_bounded(nonstandard_path)
            if len(declaration_raw) != DECLARATION_BYTES or hashlib.sha256(declaration_raw).hexdigest() != DECLARATION_SHA256:
                raise ValueError('source declaration raw SHA/size mismatch')
            declaration = legacy_model(parse_raw(declaration_raw))
            from nonstandard_consumables import attach_source
            model = attach_source(model, declaration)  # NO path; no second read.
            model['manifest'][_DECLARATION_SOURCE_ID] = DECLARATION_SHA256
            declaration_sha = DECLARATION_SHA256
        result = SourceBaseline(model, secret, declaration_sha)
        return require_source_baseline(result)

    return load_source_baseline, require_source_baseline


load_source_baseline, require_source_baseline = _source_api()
del _source_api
