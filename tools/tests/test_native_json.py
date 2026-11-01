"""Bounded synthetic parser controls. No native fixture, capability or CLI."""
from decimal import Decimal
from pathlib import Path
import os
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import native_json as nj


class NativeJsonTests(unittest.TestCase):
    def test_exact_scalar_types(self):
        result = nj.parse_raw(b'{"integer":3,"fraction":3.0,"exponent":3e0,"boolean":true,"null":null}')
        self.assertIs(type(result['integer']), int)
        self.assertIs(type(result['fraction']), Decimal)
        self.assertIs(type(result['exponent']), Decimal)
        self.assertIs(type(result['boolean']), bool)
        self.assertIsNone(result['null'])

    def test_decimal_precision_not_binary_float(self):
        self.assertEqual(nj.parse_raw(b'{"x":0.123456789012345678901234567890}')['x'], Decimal('0.123456789012345678901234567890'))

    def test_duplicate_decoded_keys_before_decoder(self):
        for raw in (b'{"a":1,"a":2}', b'{"a":1,"\\u0061":2}'):
            with patch.object(nj.json, 'loads', side_effect=AssertionError('aggregate decoder must not run')):
                with self.assertRaisesRegex(ValueError, 'duplicate'): nj.parse_raw(raw)

    def test_bad_utf8_surrogates_and_bom(self):
        for raw in (b'{"x":"\xff"}', b'{"x":"\\ud800"}', b'{"\\udc00":1}', b'\xef\xbb\xbf{}'):
            with self.subTest(raw=raw), self.assertRaises(ValueError): nj.parse_raw(raw)
        self.assertEqual(nj.parse_raw(b'{"x":"\\ud83d\\ude00"}')['x'], '\U0001f600')

    def test_numbers_nonfinite_grammar_trailing(self):
        for token in ('NaN', 'Infinity', '-Infinity', '01', '+1', '.2', '1.', '1e', '--1'):
            with self.subTest(token=token), self.assertRaises(ValueError): nj.parse_raw(('{"x":' + token + '}').encode())
        for raw in (b'{} {}', b'{"x":1,}', b'{"x":[1,]}', b'[]', b'null'):
            with self.assertRaises(ValueError): nj.parse_raw(raw)

    def test_large_integer_guard_not_disabled(self):
        # Preserve Python's configured historical unsupported-number refusal.
        limit = sys.get_int_max_str_digits()
        if limit:
            with self.assertRaises(ValueError): nj.parse_raw(b'{"n":' + b'9' * (limit + 1) + b'}')
        self.assertEqual(sys.get_int_max_str_digits(), limit)

    def test_depth_edges_before_aggregate_decode(self):
        good = b'{"x":' + b'[' * 63 + b'0' + b']' * 63 + b'}'
        nj.parse_raw(good)
        bad = b'{"x":' + b'[' * 64 + b'0' + b']' * 64 + b'}'
        with patch.object(nj.json, 'loads', side_effect=AssertionError('late budget check')):
            with self.assertRaisesRegex(ValueError, 'depth'): nj.parse_raw(bad)

    def test_private_node_edges_count_values_not_keys(self):
        nj._preflight('{"a":[null,true,1]}', max_nodes=5)
        with self.assertRaisesRegex(ValueError, 'node'): nj._preflight('{"a":[null,true,1]}', max_nodes=4)
        nj._preflight('{}', max_depth=0, max_nodes=1)
        with self.assertRaises(ValueError): nj._preflight('{}', max_nodes=nj.MAX_NODES + 1)

    def test_actual_node_edges_bounded_single_matrix(self):
        # root+array+(MAX_NODES-2)null values; one extra null must fail predecode.
        raw = b'{"x":[' + b'null,' * (nj.MAX_NODES - 3) + b'null]}'
        self.assertEqual(len(nj.parse_raw(raw)['x']), nj.MAX_NODES - 2)
        with self.assertRaisesRegex(ValueError, 'node'): nj.parse_raw(raw[:-2] + b',null]}')

    def test_actual_byte_edges_and_fixed_public_signature(self):
        raw = b'{}' + b' ' * (nj.MAX_RAW_BYTES - 2)
        self.assertEqual(nj.parse_raw(raw), {})
        with self.assertRaises(ValueError): nj.parse_raw(raw + b' ')
        with self.assertRaises(TypeError): nj.parse_raw(b'{}', max_bytes=nj.MAX_RAW_BYTES * 2)

    def test_safe_reader_and_no_symlink_hardlink_relative(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as td:
            root = Path(td); path = root / 'raw.json'; path.write_bytes(b'{"ok":true}\n')
            self.assertEqual(nj.read_bounded(path), path.read_bytes())
            symlink = root / 'link.json'; symlink.symlink_to(path)
            with self.assertRaises(ValueError): nj.read_bounded(symlink)
            hardlink = root / 'hard.json'; os.link(path, hardlink)
            with self.assertRaises(ValueError): nj.read_bounded(path)
            with self.assertRaises(ValueError): nj.read_bounded('relative.json')
            with self.assertRaises(ValueError): nj.read_bounded(root)
            for spelling in (str(root) + '//raw.json', str(root) + '/./raw.json'):
                with self.assertRaisesRegex(ValueError, 'spelling'): nj.read_bounded(spelling)

    def test_reader_fd_change_rejected(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as td:
            path = Path(td) / 'raw.json'; path.write_bytes(b'{}')
            actual_read = os.read
            def changed(fd, size):
                result = actual_read(fd, size)
                if result: path.write_bytes(b'{ }')
                return result
            with patch.object(nj.os, 'read', side_effect=changed):
                with self.assertRaisesRegex(ValueError, 'changed'): nj.read_bounded(path)

    def test_reader_rejects_cap_plus_one_without_reading_payload(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as td:
            path = Path(td) / 'large.json'
            with path.open('wb') as stream: stream.truncate(nj.MAX_RAW_BYTES + 1)
            with patch.object(nj.os, 'read', side_effect=AssertionError('oversize read')):
                with self.assertRaisesRegex(ValueError, 'cap'): nj.read_bounded(path)


if __name__ == '__main__': unittest.main()
