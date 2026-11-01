"""UNTRUSTED synthetic controls. No complete passing binding/capability is minted."""
import copy
from decimal import Decimal
import hashlib
import inspect
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import uuid

import native_observation_binding as b


class BindingControls(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='cooking-binding-unit-', dir='/home/dev/.tmp')
        self.root = Path(self.temp.name)
    def tearDown(self): self.temp.cleanup()
    def file(self, name, raw=b'{}'):
        p = self.root/name; p.write_bytes(raw)
        return p, hashlib.sha256(raw).hexdigest()
    def test_factory_has_only_three_path_hash_arguments(self):
        self.assertEqual(list(inspect.signature(b.verify_binding).parameters),
                         ['binding_path','expected_binding_sha256','snapshot_path'])
        with self.assertRaises(TypeError): b.verify_binding(None,None,None,b'{}')
    def test_no_untrusted_capability_constructor(self):
        for value in (None, {}, object(), {'snapshot':{},'valid':True}):
            with self.subTest(value=type(value)), self.assertRaises(ValueError): b.require_binding(value)
        with self.assertRaises(ValueError): b._ValidatedBinding(b'{}',{}, {}, object())
    def test_bad_external_hash_refuses_before_io(self):
        for value in ('', 'A'*64, '0'*63, True, None):
            with self.subTest(value=value), self.assertRaises(ValueError): b.verify_binding('/missing',value,'/missing')
    def test_frozen_tree_and_decimal_facts(self):
        d = b._freeze({'array':[Decimal('0.375'),3,True,None], 'object':{'id':'test:result'}})
        self.assertIsInstance(d, dict); self.assertIsInstance(d['array'], tuple)
        self.assertIs(type(d['array'][0]), Decimal); self.assertIs(type(d['array'][1]), int)
        self.assertIs(d['array'][2], True); self.assertIsNone(d['array'][3])
        self.assertEqual(copy.deepcopy(d), d)
        for operation in (lambda:d.update({'x':1}),lambda:d.clear(),lambda:d.pop('array'),
                          lambda:d.setdefault('x',1),lambda:d.__setitem__('x',1),
                          lambda:d.__delitem__('array'),lambda:d['object'].update({'id':'bad'}),
                          lambda:d.__ior__({'x':1})):
            with self.subTest(operation=operation), self.assertRaises(TypeError): operation()
        d.__init__({'evil':True})
        self.assertNotIn('evil',d)
    def test_provenance_shape_can_remain_json_serializable(self):
        value=b._freeze({'changes':[{'path':'config/test.toml','before':None,'after':'a'*64}]})
        self.assertEqual(json.loads(json.dumps(value))['changes'][0]['before'],None)
    def test_noncanonical_original_string_spelling_refuses(self):
        p,sha=self.file('canonical.json')
        for spelling in (str(p).replace('/home/dev/', '/home/dev//'),
                         str(p).replace('/home/dev/', '/home/dev/./'),
                         str(p.parent)+'/../'+p.parent.name+'/'+p.name, str(p)+'/'):
            with self.subTest(spelling=spelling),self.assertRaises(ValueError):b._Evidence().bytes(spelling,sha)
    def test_exact_integer_not_boolean(self):
        for value in (False,True,Decimal('1'),1.0,-1,None):
            with self.subTest(value=value),self.assertRaises(ValueError):b._integer(value)
        self.assertEqual(b._integer(0),0)
    def test_uuid_spellings_only(self):
        value=uuid.UUID('ad569b78-eeda-39b5-81ab-a42769cb852b')
        self.assertEqual(b._uuid(value.hex),b._uuid(str(value)))
        for text in (str(value).upper(),'{'+str(value)+'}','urn:uuid:'+str(value),' '+str(value),False):
            with self.subTest(text=text),self.assertRaises(ValueError):b._uuid(text)
    def test_aware_utc_only(self):
        self.assertEqual(b._utc('2026-10-29T00:00:00Z'),b._utc('2026-10-29T00:00:00+00:00'))
        for text in ('2026-10-29T00:00:00','2026-10-29T00:00:00+01:00',False):
            with self.subTest(text=text),self.assertRaises(ValueError):b._utc(text)
    def test_symlink_and_hardlink_evidence_refuse(self):
        p,sha=self.file('original')
        sym=self.root/'sym';sym.symlink_to(p)
        with self.assertRaises(ValueError):b._Evidence().bytes(sym,sha)
        hard=self.root/'hard';os.link(p,hard)
        with self.assertRaises(ValueError):b._Evidence().bytes(p,sha)
    def test_repeated_same_file_read_and_parse_once(self):
        p,sha=self.file('evidence.json',b'{"value":0.375}')
        e=b._Evidence()
        with patch.object(b,'read_bounded',wraps=b.read_bounded) as reader,patch.object(b,'parse_raw',wraps=b.parse_raw) as parser:
            first=e.json(p,sha);self.assertIs(first,e.json(p,sha));e.hashed(p,sha,p.stat().st_size)
            self.assertEqual(reader.call_count,1);self.assertEqual(parser.call_count,1)
        self.assertIs(type(first['value']),Decimal)
    def test_changed_same_path_or_incorrect_hash_refuses(self):
        p,sha=self.file('mutable',b'{}');e=b._Evidence();e.bytes(p,sha)
        p.write_bytes(b'{ }')
        with self.assertRaises(ValueError):e.bytes(p,sha)
        with self.assertRaises(ValueError):b._Evidence().bytes(p,'0'*64)
    def test_distinct_path_inode_alias_refuses_after_rename(self):
        p,sha=self.file('before');e=b._Evidence();e.bytes(p,sha)
        moved=self.root/'after';p.rename(moved)
        with self.assertRaises(ValueError):e.bytes(moved,sha)
    def test_opaque_hash_does_not_trigger_second_read(self):
        p,sha=self.file('opaque');e=b._Evidence();e.hashed(p,sha,2)
        with self.assertRaises(ValueError):e.json(p,sha)
    def test_noncanonical_json_run_root_refuses_before_raw_read(self):
        for root in (str(self.root).replace('/home/dev/','/home/dev//'),
                     str(self.root).replace('/home/dev/','/home/dev/./'),str(self.root)+'/'):
            binding={'schema':b.SCHEMA,'scope':b.SCOPE,'run_root':root,'invocation_nonce':'unused','actor_uuid':'unused',
                     'raw':{},'producer':{},'cohort':{}}
            path,sha=self.file('root-spelling-binding.json',json.dumps(binding).encode())
            with self.subTest(root=root),patch.object(b,'read_bounded',wraps=b.read_bounded) as reader:
                with self.assertRaisesRegex(ValueError,'canonical HOME cohort'):b.verify_binding(path,sha,self.root/'not-read.json')
                self.assertEqual(reader.call_count,1)
    def test_factory_owns_single_raw_read_and_parse_but_legacy_refuses(self):
        # Deliberately incomplete synthetic evidence CANNOT pass production binding.
        for schema in ('better_cooking:native_catalogue.v1','better_cooking:native_catalogue.diagnostic.v1','unknown'):
            with self.subTest(schema=schema):
                raw=json.dumps({'schema':schema}).encode();rawp,rawsha=self.file('native-catalogue.json',raw)
                producer={k:'unused' for k in 'jar_path jar_sha256 source_manifest_path source_manifest_sha256 build_receipt_path build_receipt_sha256 contract_path contract_sha256'.split()}
                cohort={k:'unused' for k in 'server_metadata_path server_metadata_sha256 client_metadata_path client_metadata_sha256 authorization_path authorization_sha256 controller_path controller_sha256 terminal_report_path terminal_report_sha256'.split()}
                binding={'schema':b.SCHEMA,'scope':b.SCOPE,'run_root':str(self.root),'invocation_nonce':str(uuid.uuid4()),
                         'actor_uuid':str(uuid.uuid4()),'raw':{'path':str(rawp),'sha256':rawsha,'bytes':len(raw)},'producer':producer,'cohort':cohort}
                bp,bs=self.file('binding.json',json.dumps(binding).encode())
                with patch.object(b,'read_bounded',wraps=b.read_bounded) as reader,patch.object(b,'parse_raw',wraps=b.parse_raw) as parser:
                    with self.assertRaisesRegex(ValueError,'v2 observation'):b.verify_binding(bp,bs,rawp)
                    self.assertEqual(reader.call_count,2)  # binding + raw, no preread/reread
                    self.assertEqual(parser.call_count,2)


class TerminalFactControls(unittest.TestCase):
    """Pure terminal facts are not source admission, NativeAcceptance or a capsule."""
    def fixture(self):
        actor=uuid.UUID('ad569b78-eeda-39b5-81ab-a42769cb852b');sha='a'*64
        auth={'controller_sha256':b.CONTROLLER_SHA,'server_metadata_sha256':'b'*64,
              'client_metadata_sha256':'c'*64,'created_utc':'2026-10-29T00:00:00Z'}
        def event(name,sec,**kw):return {'name':name,'utc':f'2026-10-29T00:00:{sec:02d}Z',**kw}
        t={'schema':'better_cooking:connected_getter_discovery.v4','status':'connected_getters_observed_not_qualified',
           'driver_sha256':b.CONTROLLER_SHA,'server_metadata_sha256':'b'*64,'client_metadata_sha256':'c'*64,
           'startup_budget_seconds':1200,'client_join_budget_seconds':1200,'normal_server_stop_seconds':180,
           'one_client_connection_only':True,'proofs':dict.fromkeys(b.PROOFS,True),'errors':[],
           'ownership_unknown':[],'verified_known_remaining':[],'server_normal_stop':True,
           'runner_guard_released_after_cleanup':True,'identity':{'uuid':actor.hex},
           'server_stop':{'attempted':True,'command_attempted':True,'offset':20,'deadline':Decimal('150.25'),'failed':False,'proof':True},
           'wrapper_exit_codes':{'server':0},'created_utc':'2026-10-29T00:00:01Z','finished_utc':'2026-10-29T00:00:10Z',
           'events':[event('console_command',2,command='cooking-fixture catalogue'),
                     event('native_catalogue_observed',3,raw_sha256=sha,raw_bytes=7,actual_server_player_uuid=str(actor)),
                     event('console_command',4,command='stop'),event('server_saved_and_stopped',5,exit_code=0,original_offset=20)]}
        for key in ('acceptance','gameplay_acceptance','render_acceptance','current_physical_domain_qualified','defaults_installed'):t[key]=False
        return t,auth,sha,actor
    def test_all_literal_facts_still_cannot_mint_capability(self):
        t,a,s,actor=self.fixture();observed=b._terminal(t,a,{},s,7,actor)
        with self.assertRaises(ValueError):b.require_binding(observed)
    def test_missing_false_integer_bool_and_mismatched_facts_refuse(self):
        mutations=(lambda t:t['proofs'].update(handles_closed=1),lambda t:t['proofs'].pop('client_after'),
                   lambda t:t.update(startup_budget_seconds=True),lambda t:t['server_stop'].update(offset=False),
                   lambda t:t['wrapper_exit_codes'].update(server=False),lambda t:t.update(defaults_installed=True),
                   lambda t:t['events'][1].update(raw_bytes=True),lambda t:t['events'][1].update(raw_sha256='d'*64),
                   lambda t:t['events'][3].update(original_offset=21),lambda t:t.update(errors=['sticky']),
                   lambda t:t.update(runner_guard_released_after_cleanup=False),lambda t:t['server_stop'].update(deadline=Decimal('NaN')),
                   lambda t:t['events'].append(copy.deepcopy(t['events'][2])),lambda t:t['events'][1].update(utc='2026-10-29T00:00:03'))
        for index,mutation in enumerate(mutations):
            t,a,s,actor=self.fixture();mutation(t)
            with self.subTest(index=index),self.assertRaises(ValueError):b._terminal(t,a,{},s,7,actor)
    def test_original_180s_stop_deadline_not_reset(self):
        t,a,s,actor=self.fixture();t['events'][3]['utc']='2026-10-29T00:03:05Z';t['finished_utc']='2026-10-29T00:03:06Z'
        with self.assertRaisesRegex(ValueError,'stop budget'):b._terminal(t,a,{},s,7,actor)


if __name__ == '__main__':unittest.main()
