"""Positive actual pinned SOURCE origin, never synthetic native acceptance."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import source_input
from source_input import load_source_baseline, require_source_baseline, SOURCE_SHA256, SOURCE_BYTES, DECLARATION_SHA256, DECLARATION_BYTES, COMPOSED_SHA256, COMPOSED_BYTES
from extract_catalogue import encoded
from compile_policy import compile_policy, _compile_policy_model

TOOLS = Path(__file__).resolve().parents[1]


class SourceInputTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.source = load_source_baseline(TOOLS / 'frozen/catalogue_inputs.json')

    def test_independent_pin_raw_and_canonical(self):
        raw = (TOOLS / 'frozen/catalogue_inputs.json').read_bytes()
        self.assertEqual(len(raw), SOURCE_BYTES)
        self.assertEqual(hashlib.sha256(raw).hexdigest(), SOURCE_SHA256)
        self.assertEqual(hashlib.sha256(encoded(self.source).encode()).hexdigest(), SOURCE_SHA256)
        self.assertIs(require_source_baseline(self.source), self.source)

    def test_equal_byte_owned_copy_allowed(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as td:
            path = Path(td) / 'source.json'; path.write_bytes((TOOLS / 'frozen/catalogue_inputs.json').read_bytes())
            self.assertEqual(load_source_baseline(path), self.source)

    def test_changed_raw_refuses_before_any_model_conversion(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as td:
            path = Path(td) / 'changed.json'; path.write_bytes(b'{}')
            with self.assertRaisesRegex(ValueError, 'SHA/size'): load_source_baseline(path)

    def test_plain_deep_json_copy_cannot_recreate_source_capability(self):
        for value in (dict(self.source), copy.copy(self.source), copy.deepcopy(self.source), json.loads(encoded(self.source)), {'trusted_source': True}):
            with self.assertRaisesRegex(ValueError, 'SourceBaseline'): require_source_baseline(value)
            with self.assertRaises(ValueError): compile_policy(value)

    def test_at_use_hash_revalidation(self):
        source = load_source_baseline(TOOLS / 'frozen/catalogue_inputs.json')
        source['recipes'] = {}
        with self.assertRaisesRegex(ValueError, 'content changed'): require_source_baseline(source)
        with self.assertRaises(ValueError): compile_policy(source)

    def test_source_cap_no_caller_pin_or_public_constructor(self):
        with self.assertRaises(TypeError): load_source_baseline(TOOLS / 'frozen/catalogue_inputs.json', expected_sha256=SOURCE_SHA256)
        with self.assertRaises(ValueError): type(self.source)({}, object())

    def test_exact_finite_composition_and_current_policy_golden_bytes(self):
        source = load_source_baseline(TOOLS / 'frozen/catalogue_inputs.json', nonstandard_path=TOOLS / 'authored/nonstandard_consumables.json')
        raw = encoded(source).encode()
        self.assertEqual(len(raw), COMPOSED_BYTES)
        self.assertEqual(hashlib.sha256(raw).hexdigest(), COMPOSED_SHA256)
        declaration = (TOOLS / 'authored/nonstandard_consumables.json').read_bytes()
        self.assertEqual(len(declaration), DECLARATION_BYTES)
        self.assertEqual(hashlib.sha256(declaration).hexdigest(), DECLARATION_SHA256)
        authored = json.loads((TOOLS / 'authored/decisions.json').read_bytes())
        result = compile_policy(source, authored=authored)
        expected = Path('/home/dev/better-content-modpack/kubejs/config/culinary_policy.json').read_bytes()
        self.assertEqual(hashlib.sha256(expected).hexdigest(), 'eb019ae43e6f70d2e55f80a74d16ded686349365fb0c115255300c13741a71c0')
        self.assertEqual(encoded(result).encode(), expected)

    def test_equal_byte_declaration_copy_keeps_historical_source_id_not_copy_path(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as td:
            path = Path(td) / 'declaration.json'; path.write_bytes((TOOLS / 'authored/nonstandard_consumables.json').read_bytes())
            source = load_source_baseline(TOOLS / 'frozen/catalogue_inputs.json', nonstandard_path=path)
            self.assertEqual(source['manifest'][str(TOOLS / 'authored/nonstandard_consumables.json')], DECLARATION_SHA256)
            self.assertNotIn(str(path), source['manifest'])
            source['recipes'] = {}
            with self.assertRaises(ValueError): require_source_baseline(source)

    def test_composition_reads_parses_each_pinned_source_once_no_helper_path(self):
        from nonstandard_consumables import attach_source
        with patch.object(source_input, 'read_bounded', wraps=source_input.read_bounded) as reader, \
                patch.object(source_input, 'parse_raw', wraps=source_input.parse_raw) as parser, \
                patch('nonstandard_consumables.attach_source', wraps=attach_source) as attach:
            load_source_baseline(TOOLS / 'frozen/catalogue_inputs.json', nonstandard_path=TOOLS / 'authored/nonstandard_consumables.json')
        self.assertEqual(reader.call_count, 2); self.assertEqual(parser.call_count, 2)
        self.assertEqual(len(attach.call_args.args), 2); self.assertEqual(attach.call_args.kwargs, {})

    def test_arbitrary_declaration_and_composer_inputs_refused(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as td:
            path = Path(td) / 'declaration.json'; path.write_bytes(b'{}')
            with self.assertRaisesRegex(ValueError, 'declaration raw SHA/size'): load_source_baseline(TOOLS / 'frozen/catalogue_inputs.json', nonstandard_path=path)
        with self.assertRaises(TypeError): load_source_baseline(TOOLS / 'frozen/catalogue_inputs.json', overlay={})

    def test_positive_public_source_compile_preserves_speculative_model_bytes(self):
        before = encoded(self.source)
        public = compile_policy(self.source)
        private = _compile_policy_model(json.loads(before))
        self.assertEqual(encoded(public), encoded(private))
        self.assertEqual(public['qualification'], 'unqualified')
        self.assertEqual(encoded(self.source), before)


if __name__ == '__main__': unittest.main()
