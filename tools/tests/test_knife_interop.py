"""Exact source-authorized tag additions; not live tag/action acceptance."""
import json
from pathlib import Path
import unittest

PACK = Path('/home/dev/better-content-modpack')
BLADES = {
    'kaleidoscope_cookery:iron_kitchen_knife',
    'kaleidoscope_cookery:gold_kitchen_knife',
    'kaleidoscope_cookery:diamond_kitchen_knife',
    'kaleidoscope_cookery:netherite_kitchen_knife',
}

class KnifeInteropSourceTests(unittest.TestCase):
    def test_exact_four_registered_blades(self):
        tag = json.loads((PACK/'kubejs/data/forge/tags/items/tools/knives.json').read_text())
        self.assertFalse(tag['replace'])
        optional = [v for v in tag['values'] if isinstance(v, dict)]
        self.assertEqual({v['id'] for v in optional}, BLADES)
        self.assertEqual(len(optional), 4)
        for entry in optional:
            self.assertEqual(set(entry), {'id','required'})
            self.assertIs(entry['required'], False)
        self.assertEqual([v for v in tag['values'] if isinstance(v,str)], ['additionalweaponry:butcher_knife'])
    def test_no_reverse_tag_cycle_or_all_modifiable_tools(self):
        tag = json.loads((PACK/'kubejs/data/forge/tags/items/tools/knives.json').read_text())
        ids = [v if isinstance(v,str) else v['id'] for v in tag['values']]
        self.assertFalse(any(v.startswith('#') for v in ids))
        self.assertIn('additionalweaponry:butcher_knife', tag['values'])  # preserve existing user data

    def test_optional_members_present_or_absent_unit_model_not_native_tag_load(self):
        tag = json.loads((PACK/'kubejs/data/forge/tags/items/tools/knives.json').read_text())
        base = {'additionalweaponry:butcher_knife'}
        # UNIT-ONLY optional-item resolution model, NOT actual Forge tag observations.
        def resolve(registered):
            result = set()
            for row in tag['values']:
                item = row if isinstance(row,str) else row['id']
                if item in registered: result.add(item)
                elif isinstance(row,str) or row['required']: raise ValueError('missing required entry')
            return result
        self.assertEqual(resolve(base), base)
        self.assertEqual(resolve(base | BLADES), base | BLADES)

if __name__ == '__main__': unittest.main()
