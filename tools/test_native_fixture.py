"""Small synthetic inventories only: never copies or launches the real pack."""
import copy
import fcntl
import json
import multiprocessing
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import native_fixture as nf


def hold_lock(path, ready, release):
    with open(path, "a+b") as stream:
        fcntl.lockf(stream, fcntl.LOCK_EX)
        ready.set(); release.wait(10)


class FixtureTest(unittest.TestCase):
    def setUp(self):
        temporary = Path.home() / ".tmp"
        temporary.mkdir(exist_ok=True)
        self.tmp = tempfile.TemporaryDirectory(dir=temporary, prefix="cooking-fixture-test-")
        self.root = Path(self.tmp.name)
        self.base = self.root / "retained"
        self.project = self.root / "source"
        self.base.mkdir(); self.project.mkdir()
        self.output = self.project / "fresh"
        self.manifest = self.root / "inputs.json"
        self.lock = self.root / "runner.lock"
        self.put("libraries/forge/unix_args.txt", "cpw.mods.bootstraplauncher.BootstrapLauncher\n--launchTarget forgeserver\n")
        for launcher in sorted(nf.LAUNCHERS):
            self.put(launcher, "#!/bin/sh\n# synthetic preparation-only launcher\nexit 1\n")
        self.put("config/normal.toml", "enabled=true\n")
        self.roles = {}
        for role in sorted(nf.ROLES):
            destination = "mods/" + role + ".jar"
            self.jar(self.base / destination, nf.ROLE_IDS[role])
            self.roles[role] = destination
        # These root-only retained files are deliberately NOT whitelisted.
        self.put("world/playerdata/private.dat", "PLAYER SECRET")
        self.put("logs/latest.log", "LOG SECRET")
        self.put("launcher_accounts.json", "CREDENTIAL SECRET")
        self.put("run.json", "EVIDENCE SECRET")
        self.data = {"schema": nf.SCHEMA, "base_server": str(self.base),
                     "base_files": [{"path": p, "sha256": nf.digest(self.base / p)} for p in sorted(nf.base_inventory(self.base))],
                     "overlays": [], "roles": self.roles, "forge_args": "libraries/forge/unix_args.txt"}
        self.save()

    def tearDown(self):
        self.tmp.cleanup()

    def put(self, path, content):
        p = self.base / path
        p.parent.mkdir(parents=True, exist_ok=True); p.write_text(content)

    def jar(self, path, mod):
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w") as jar:
            jar.writestr("META-INF/mods.toml", '[[mods]]\nmodId="' + mod + '"\nversion="1"\n')

    def save(self):
        self.manifest.write_text(json.dumps(self.data))

    def prepare(self, target="forgeserver"):
        return nf.prepare(self.base, self.output, self.manifest, target,
                          source_root=self.project, lock_path=self.lock)

    def test_prepared_not_run_no_world_credentials_or_hardlinks(self):
        before = {p: nf.digest(p) for p in self.base.rglob("*") if p.is_file()}
        result = self.prepare()
        self.assertEqual("PREPARED_NOT_RUN", result["status"])
        self.assertFalse(result["acceptance"])
        self.assertFalse((self.output / "world").exists())
        for name in ["logs", "launcher_accounts.json", "run.json"]:
            self.assertFalse((self.output / name).exists())
        self.assertEqual(before, {p: nf.digest(p) for p in before})
        self.assertNotEqual((self.base / "config/normal.toml").stat().st_ino,
                            (self.output / "config/normal.toml").stat().st_ino)
        self.assertIn("-Xmx6G", (self.output / "user_jvm_args.txt").read_text())
        self.assertEqual(["./run.sh", "nogui"], result["planned_command"])
        self.assertEqual({"BC_JAVA": "java"}, result["planned_environment"])
        self.assertEqual("production_run_sh_lifecycle_supervisor", result["launch_contract"])
        for launcher in nf.LAUNCHERS:
            self.assertEqual(0o755, (self.output / launcher).stat().st_mode & 0o777)
            self.assertEqual((self.base / launcher).read_bytes(), (self.output / launcher).read_bytes())
        self.assertIn("online-mode=false", (self.output / "server.properties").read_text())

    def test_jvm_properties_are_frozen_before_main_not_program_arguments(self):
        args = ("-Dbetter_cooking.discoveryOnly=true", "-Dfml.disableVersionCheck=true",
                "-Duser.home=" + str(self.project / "fresh-home"))
        result = nf.prepare(self.base, self.output, self.manifest, source_root=self.project,
                            lock_path=self.lock, java="/bound/java17", jvm_args=args)
        self.assertEqual({"BC_JAVA": "/bound/java17"}, result["planned_environment"])
        self.assertEqual(["./run.sh", "nogui"], result["planned_command"])
        for arg in args:self.assertIn(arg, (self.output / "user_jvm_args.txt").read_text().splitlines())
        self.assertEqual(nf.digest(self.output / "user_jvm_args.txt"), result["files"]["user_jvm_args.txt"])
        self.assertFalse(result["lineage_state_copied_or_manually_initialized"])
        self.assertFalse((self.output / ".better_world_management").exists())

    def test_jvm_property_injection_and_escaping_refused_before_output(self):
        for args in [("-javaagent:fake.jar",), ("-Dmixin.env.ignoreRequired=true",),
                     ("-Duser.home=/outside",), ("-Duser.home=" + str(self.project / "../escape"),),
                     ("-Dbetter_cooking.discoveryOnly=true\n-Dfake=true",),
                     ("-Dbetter_cooking.discoveryOnly=true", "-Dbetter_cooking.discoveryOnly=false"),
                     ("-Dforge.enabledGameTestNamespaces=*",), ("-Duser.home=" + str(self.project / "space path"),)]:
            with self.subTest(args=args), self.assertRaises(nf.Refused):
                nf.prepare(self.base, self.output, self.manifest, source_root=self.project,
                           lock_path=self.lock, jvm_args=args)
            self.assertFalse(self.output.exists())

    def test_missing_supervisor_is_not_silently_replaced_by_direct_java(self):
        (self.base / "better-world-management-server.sh").unlink()
        self.data["base_files"] = [{"path": p, "sha256": nf.digest(self.base / p)} for p in sorted(nf.base_inventory(self.base))]
        self.save()
        with self.assertRaises(nf.Refused):self.prepare()
        self.assertFalse(self.output.exists())

    def add_bootstrap_inputs(self):
        paths = ("defaultresources/excavated_variants/excavated_variants/configs/config.json5",
                 "globalresources/pack/excavated_variants/pack/variants/ores.json5")
        for path in paths:
            self.put(path, '{"synthetic_bootstrap_input": true}\n')
        self.data["base_files"] = [{"path": p, "sha256": nf.digest(self.base / p)}
                                   for p in sorted(nf.base_inventory(self.base))]
        self.save()
        return paths

    def test_public_bootstrap_trees_are_complete_hashed_independent_copies(self):
        paths = self.add_bootstrap_inputs()
        result = self.prepare()
        for path in paths:
            source, copied = self.base / path, self.output / path
            self.assertEqual(source.read_bytes(), copied.read_bytes())
            self.assertNotEqual(source.stat().st_ino, copied.stat().st_ino)
            self.assertEqual(nf.digest(source), result["inputs"][path]["sha256"])
            self.assertEqual(nf.digest(copied), result["files"][path])

    def test_omitted_bootstrap_provider_refused_before_output(self):
        paths = self.add_bootstrap_inputs()
        self.data["base_files"] = [x for x in self.data["base_files"] if x["path"] != paths[0]]
        self.save()
        with self.assertRaises(nf.Refused): self.prepare()
        self.assertFalse(self.output.exists())

    def test_changed_bootstrap_provider_refused_before_output(self):
        paths = self.add_bootstrap_inputs()
        (self.base / paths[1]).write_text('{"changed": true}')
        with self.assertRaises(nf.Refused): self.prepare()
        self.assertFalse(self.output.exists())

    def test_new_bootstrap_tree_keeps_symlink_and_privacy_refusals(self):
        self.add_bootstrap_inputs()
        link = self.base / "globalresources/pack/linked.json5"
        link.symlink_to(self.base / "config/normal.toml")
        with self.assertRaises(nf.Refused): nf.base_inventory(self.base)
        link.unlink()
        for path in ("defaultresources/world/level.dat", "globalresources/credentials.json",
                     "globalresources/pack/access_token.json", "defaultresources/pack/secret.key",
                     "globalresources/../outside"):
            with self.subTest(path=path), self.assertRaises(nf.Refused): nf.relative(path)
        self.assertFalse(self.output.exists())

    def test_existing_output_refused(self):
        self.output.mkdir()
        with self.assertRaises(nf.Refused): self.prepare()
        self.assertEqual([], list(self.output.iterdir()))

    def test_target_derives_receipted_runtime_argfile_without_changing_original(self):
        before = (self.base / "libraries/forge/unix_args.txt").read_bytes()
        result = self.prepare("forgegametestserver")
        self.assertIn("forgegametestserver", (self.output / "fixture-forge-args.txt").read_text())
        self.assertEqual((self.output / "fixture-forge-args.txt").read_bytes(), (self.output / "libraries/forge/unix_args.txt").read_bytes())
        self.assertEqual(before, (self.base / "libraries/forge/unix_args.txt").read_bytes())
        row = result["inputs"]["libraries/forge/unix_args.txt"]
        self.assertEqual(nf.digest(self.base / "libraries/forge/unix_args.txt"), row["sha256"])
        self.assertEqual(nf.digest(self.output / "libraries/forge/unix_args.txt"), row["runtime_sha256"])
        self.assertNotEqual(row["sha256"], row["runtime_sha256"])
        self.assertEqual(["./run.sh", "nogui"], result["planned_command"])

    def test_missing_hash_refused(self):
        self.data["base_files"][0]["sha256"] = ""; self.save()
        with self.assertRaises(nf.Refused): self.prepare()
        self.assertFalse(self.output.exists())

    def test_incomplete_baseline_refused(self):
        self.data["base_files"].pop(); self.save()
        with self.assertRaises(nf.Refused): self.prepare()

    def test_escape_and_world_overlay_refused(self):
        for destination in ["../outside", "/absolute", "world/level.dat", "config/credentials.json"]:
            self.data["overlays"] = [{"source": str(self.base / "run.sh"), "destination": destination, "sha256": nf.digest(self.base / "run.sh")}]
            self.save()
            with self.assertRaises(nf.Refused): self.prepare()

    def test_symlink_source_and_output_ancestor_refused(self):
        (self.base / "config/link").symlink_to(self.base / "run.sh")
        with self.assertRaises(nf.Refused): self.prepare()
        (self.base / "config/link").unlink()
        link = self.project / "linked"; link.symlink_to(self.root, target_is_directory=True)
        self.output = link / "fresh"
        with self.assertRaises(nf.Refused): self.prepare()

    def test_duplicate_mod_id_refused(self):
        self.jar(self.base / "mods/water.jar", nf.ROLE_IDS["heat"])
        self.data["base_files"] = [{"path": p, "sha256": nf.digest(self.base / p)} for p in sorted(nf.base_inventory(self.base))]
        self.save()
        with self.assertRaises(nf.Refused): self.prepare()

    def test_duplicate_overlay_and_wrong_replace_hash_refused(self):
        item = {"source": str(self.base / "run.sh"), "destination": "run.sh", "sha256": nf.digest(self.base / "run.sh"), "replaces_sha256": "0" * 64}
        self.data["overlays"] = [item]; self.save()
        with self.assertRaises(nf.Refused): self.prepare()
        item["replaces_sha256"] = item["sha256"]
        self.data["overlays"] = [item, copy.deepcopy(item)]; self.save()
        with self.assertRaises(nf.Refused): self.prepare()

    def test_changed_input_during_copy_retains_failure(self):
        original = nf.shutil.copyfile
        def change(source, destination):
            original(source, destination)
            if Path(source).name == "normal.toml": Path(source).write_text("changed")
        with patch.object(nf.shutil, "copyfile", side_effect=change):
            with self.assertRaises(nf.Refused): self.prepare()
        self.assertTrue((self.output / "PREPARATION_FAILED.json").is_file())
        self.assertFalse((self.output / "fixture-metadata.json").exists())

    def test_external_args_refused_before_output(self):
        self.put("libraries/forge/unix_args.txt", "-p /etc/secret\n--launchTarget forgeserver\n")
        for item in self.data["base_files"]:
            item["sha256"] = nf.digest(self.base / item["path"])
        self.save()
        with self.assertRaises(nf.Refused): self.prepare()
        self.assertFalse(self.output.exists())

    def test_commerce_role_and_actual_fixture_id_required(self):
        del self.data["roles"]["commerce"]; self.save()
        with self.assertRaises(nf.Refused): self.prepare()
        self.data["roles"]["commerce"] = "mods/commerce.jar"
        self.jar(self.base / "mods/native_fixture.jar", "wrong_fixture_id")
        self.data["base_files"] = [{"path": p, "sha256": nf.digest(self.base / p)} for p in sorted(nf.base_inventory(self.base))]
        self.save()
        with self.assertRaises(nf.Refused): self.prepare()
        self.assertFalse(self.output.exists())

    def test_duplicate_mod_filename_refused(self):
        self.jar(self.base / "mods/nested/WATER.jar", "distinct")
        self.data["base_files"] = [{"path": p, "sha256": nf.digest(self.base / p)} for p in sorted(nf.base_inventory(self.base))]
        self.save()
        with self.assertRaises(nf.Refused): self.prepare()

    def test_duplicate_json_keys_refused(self):
        self.manifest.write_text('{"schema": "a", "schema": "b"}')
        with self.assertRaises(nf.Refused): self.prepare()
        self.assertFalse(self.output.exists())

    def test_active_posix_runner_lock_refused(self):
        ready = multiprocessing.Event(); release = multiprocessing.Event()
        child = multiprocessing.Process(target=hold_lock, args=(self.lock, ready, release))
        child.start()
        try:
            self.assertTrue(ready.wait(5))
            with self.assertRaises(nf.Refused): self.prepare()
            self.assertFalse(self.output.exists())
        finally:
            release.set(); child.join(5)
            if child.is_alive(): child.terminate(); child.join()

if __name__ == "__main__":
    unittest.main()
