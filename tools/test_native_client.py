#!/usr/bin/env python3
"""Synthetic-only client preparation regressions. No real pack/cache/client is read or run."""
import copy
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import native_client as client


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value) if isinstance(value, dict) else value)
    return path


def jar(path, mod=None, native=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as archive:
        if mod:
            archive.writestr("META-INF/mods.toml", f'[[mods]]\nmodId="{mod}"\nversion="1"\n')
        if native:
            archive.writestr("linux/x64/liblwjgl.so", b"synthetic native NOT executable")
        archive.writestr("synthetic.txt", "unit test only")
    return path


def sha1(path):
    return hashlib.sha1(path.read_bytes()).hexdigest()


class NativeClientTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="cooking-client-pure-", dir="/home/dev/.tmp")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.display_program_rows = {}
        for name in ("unshare", "mount", "python3", "Xvfb"):
            program = write(self.root / "synthetic-programs" / name, "NONEXECUTED synthetic " + name)
            program.chmod(0o755)
            self.display_program_rows["/usr/bin/" + name] = {"physical_path": str(program), "sha256": client.digest(program)}
        programs_patch = patch.object(client, "display_programs", side_effect=lambda: copy.deepcopy(self.display_program_rows))
        programs_patch.start(); self.addCleanup(programs_patch.stop)
        target_patch = patch.object(client, "xkb_host_identity", return_value=[10, 20, 30, 40])
        target_patch.start(); self.addCleanup(target_patch.stop)
        decision = write(self.root / "synthetic-decision.json", {"scope": "UNIT ONLY NOT APPROVED"})
        decision_patch = patch.object(client, "display_storage_decision", return_value={"path": str(decision), "sha256": client.digest(decision)})
        decision_patch.start(); self.addCleanup(decision_patch.stop)
        self.project = self.root / "project"
        self.base = self.root / "accepted-client"
        self.cache = self.root / "cache"
        self.server = self.project / "prepared-server"
        self.output = self.project / "fresh-client"
        self.manifest = self.root / "client-input.json"
        self.project.mkdir()
        self.roles = {role: f"mods/{mod}.jar" for role, mod in client.ROLE_IDS.items()}
        self.roles["bumblezone"] = client.BUMBLEZONE_JAR
        for value in ["saves/old-world/level.dat", "logs/latest.log", "options.txt", "accounts.json", "usercache.json", "servers.dat"]:
            write(self.base / value, "OLD IDENTITY MUST NOT COPY")
        jar(self.base / "mods/emi.jar", "emi")
        jar(self.base / "mods/render.jar", "render_mod")
        write(self.base / "config/render.toml", "renderer = 'original'\n")
        for role, dest in self.roles.items():
            jar(self.server / dest, client.ROLE_IDS[role])
        frozen = write(self.server / "fixture-input-manifest.json", {"roles": self.roles})
        server_meta = {"schema": "bc.cooking.native_fixture.v1", "status": "PREPARED_NOT_RUN",
                       "launch_target": "forgeserver", "planned_cwd": str(self.server), "port": 25471,
                       "input_manifest_sha256": client.digest(frozen),
                       "inputs": {dest: {"sha256": client.digest(self.server / dest)} for dest in self.roles.values()},
                       "files": {dest: client.digest(self.server / dest) for dest in self.roles.values()}}
        self.server_meta = write(self.server / "fixture-metadata.json", server_meta)
        jdk = self.root / "jdk"
        java = write(jdk / "bin/java", "synthetic NONEXECUTABLE Java17 placeholder")
        release = write(jdk / "release", 'JAVA_VERSION="17.0.17"\n')
        library = jar(self.cache / "libraries/org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-linux.jar", native=True)
        artifact = {"name": "org.lwjgl:lwjgl:3.3.1:natives-linux", "downloads": {"artifact": {
            "path": str(library.relative_to(self.cache / "libraries")), "sha1": sha1(library)}}}
        for name in ["net/minecraftforge/forge/1.20.1-47.4.22/forge-1.20.1-47.4.22-client.jar",
                     "net/minecraftforge/forge/1.20.1-47.4.22/forge-1.20.1-47.4.22-universal.jar",
                     "net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-extra.jar",
                     "net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-srg.jar",
                     *[f"net/minecraftforge/{name}/1.20.1-47.4.22/{name}-1.20.1-47.4.22.jar"
                       for name in ["fmlcore", "javafmllanguage", "lowcodelanguage", "mclanguage"]]]:
            jar(self.cache / "libraries" / name)
        vanilla_jar = jar(self.cache / f"versions/{client.VERSION}/{client.VERSION}.jar")
        asset = b"unit asset"
        h = hashlib.sha1(asset).hexdigest()
        obj = self.cache / f"assets/objects/{h[:2]}/{h}"
        obj.parent.mkdir(parents=True); obj.write_bytes(asset)
        index = write(self.cache / "assets/indexes/5.json", {"objects": {"unit": {"hash": h, "size": len(asset)}}})
        self.vanilla_path = self.cache / "versions/1.20.1/1.20.1.json"
        write(self.vanilla_path, {"id": "1.20.1", "javaVersion": {"majorVersion": 17},
            "downloads": {"client": {"sha1": sha1(vanilla_jar)}}, "assetIndex": {"id": "5", "sha1": sha1(index)},
            "libraries": [artifact], "arguments": {
                "jvm": ["-Djava.library.path=${natives_directory}", "-cp", "${classpath}"],
                "game": ["--username", "${auth_player_name}", "--uuid", "${auth_uuid}",
                         "--accessToken", "${auth_access_token}", "--gameDir", "${game_directory}",
                         "--assetsDir", "${assets_root}", "--assetIndex", "${assets_index_name}",
                         {"rules": [{"action": "allow", "features": {"is_quick_play_multiplayer": True}}],
                          "value": ["--quickPlayMultiplayer", "${quickPlayMultiplayer}"]}]}})
        self.forge_path = self.cache / f"versions/{client.VERSION}/{client.VERSION}.json"
        write(self.forge_path, {"id": client.VERSION, "inheritsFrom": "1.20.1",
            "mainClass": "cpw.mods.bootstraplauncher.BootstrapLauncher", "libraries": [],
            "arguments": {"jvm": ["-DlibraryDirectory=${library_directory}"],
                          "game": ["--launchTarget", "forgeclient"]}})
        self.m = {"schema": client.SCHEMA, "base_client": str(self.base), "launcher_cache": str(self.cache),
                  "java": str(java), "java_sha256": client.digest(java), "java_release_sha256": client.digest(release),
                  "server_port": 25471, "roles": self.roles, "server_metadata": str(self.server_meta),
                  "server_metadata_sha256": client.digest(self.server_meta), "overlays": [
                      {"destination": dest, "source": str(self.server / dest), "sha256": client.digest(self.server / dest)}
                      for dest in self.roles.values()]}
        self.freeze()

    def freeze(self):
        for key, root, trees in [("base_files", self.base, client.TREES), ("cache_files", self.cache, client.CACHE_TREES)]:
            self.m[key] = [{"path": name, "sha256": client.digest(root / name)} for name in sorted(client.inventory(root, trees))]
        write(self.manifest, self.m)

    def prepare(self):
        write(self.manifest, self.m)
        return client.prepare(self.manifest, self.output, source_root=self.project,
                              baseline=self.base, cache_root=self.cache, lock_path=self.root / "runner.lock")

    def test_fresh_real_client_plan_keeps_original_emi_renderer_and_nine_server_roles(self):
        result = self.prepare()
        self.assertEqual(result["status"], "PREPARED_NOT_RUN")
        self.assertFalse(result["acceptance"])
        command = result["planned_command"]
        self.assertEqual(command[command.index("--launchTarget") + 1], "forgeclient")
        self.assertEqual(command[command.index("--quickPlayMultiplayer") + 1], "127.0.0.1:25471")
        self.assertEqual(command[command.index("--accessToken") + 1], "0")
        self.assertNotEqual(result["identity"], client.fresh_identity())
        self.assertRegex(result["identity"]["username"], r"^Cooking_[0-9a-f]{8}$")
        self.assertEqual((self.output / "config/render.toml").read_bytes(), (self.base / "config/render.toml").read_bytes())
        for name in ["emi.jar", "render.jar"]:
            self.assertEqual(client.digest(self.output / "mods" / name), client.digest(self.base / "mods" / name))
        for dest in self.roles.values():
            self.assertEqual(client.digest(self.output / dest), client.digest(self.server / dest))
        self.assertTrue((self.output / "natives/liblwjgl.so").is_file())
        client.verify_inputs(self.output / "client-metadata.json")

    def test_exact_fancymenu_global_options_preserves_hash_bytes_and_independent_copy(self):
        name = "config/fancymenu/options.txt"
        source = self.base / name
        source.parent.mkdir(parents=True)
        payload = b"##[window]\r\nS:custom_window_title = '';\r\n##[ui]\r\nS:ui_theme = 'dark';\r\n"
        source.write_bytes(payload)
        expected = client.digest(source)
        self.freeze()
        value = self.prepare()
        target = self.output / name
        self.assertEqual(source.read_bytes(), payload)
        self.assertEqual(target.read_bytes(), payload)
        self.assertEqual(value["inputs"][str(source)], expected)
        self.assertEqual(value["files"][name], expected)
        self.assertNotEqual((source.stat().st_dev, source.stat().st_ino),
                            (target.stat().st_dev, target.stat().st_ino))
        self.assertEqual(target.stat().st_nlink, 1)
        self.assertNotIn("OLD IDENTITY", (self.output / "options.txt").read_text())
        client.verify_inputs(self.output / "client-metadata.json")
        source.write_bytes(payload + b"changed")
        with self.assertRaises(client.Refused):
            client.verify_inputs(self.output / "client-metadata.json")

    def test_fancymenu_exception_is_not_a_basename_sensitive_or_scope_exception(self):
        for name in ["options.txt", "config/other/options.txt", "config/fancymenu/OPTIONS.TXT",
                     "config/FancyMenu/options.txt", "config/fancymenu/sub/options.txt",
                     "config/fancymenu/optionsof.txt", "config/fancymenu/accounts.json",
                     "config/fancymenu/credentials.json", "config/fancymenu/uuid.json",
                     "config/fancymenu/session.lock", "config/fancymenu/usercache.json",
                     "config/fancymenu/access_token.json", "config/fancymenu/client.key",
                     "config/fancymenu/options.txt/child", "config/fancymenu/../fancymenu/options.txt"]:
            with self.subTest(name=name), self.assertRaises(client.Refused):
                client.rel(name, client.TREES)
        for trees in [client.CACHE_TREES, {"config"}, client.CACHE_TREES | {"config"}]:
            with self.subTest(trees=trees), self.assertRaises(client.Refused):
                client.rel("config/fancymenu/options.txt", trees)
        self.assertEqual(client.rel("config/fancymenu/options.txt", set(client.TREES)),
                         "config/fancymenu/options.txt")

    def test_exact_global_options_still_refuses_symlink_hardlink_and_directory(self):
        source = self.base / "config/fancymenu/options.txt"
        source.parent.mkdir(parents=True)
        source.symlink_to(self.base / "config/render.toml")
        with self.assertRaises(client.Refused): client.inventory(self.base, client.TREES)
        source.unlink()
        os.link(self.base / "config/render.toml", source)
        with self.assertRaises(client.Refused): client.inventory(self.base, client.TREES)
        source.unlink()
        source.mkdir()
        with self.assertRaises(client.Refused): client.inventory(self.base, client.TREES)
        self.assertFalse(self.output.exists())

    def test_other_options_and_sensitive_content_inventory_is_not_silently_skipped(self):
        for name in ["config/other/options.txt", "config/fancymenu/uuid.json",
                     "config/fancymenu/credentials.json", "config/fancymenu/session.lock"]:
            path = write(self.base / name, "MUST REFUSE, NOT SKIP")
            with self.subTest(name=name), self.assertRaises(client.Refused):
                client.inventory(self.base, client.TREES)
            path.unlink()
        self.assertFalse(self.output.exists())

    def test_three_exact_public_material_stats_preserve_all_hashes_and_independent_bytes(self):
        payloads = {}
        names = sorted(name for name in client.PUBLIC_MATERIAL_STATS_FILES if name.startswith(client.PUBLIC_MATERIAL_STATS_DIR + "/"))
        self.assertEqual(len(names), 3)
        for index, name in enumerate(names):
            source = self.base / name
            source.parent.mkdir(parents=True, exist_ok=True)
            payload = ('{\r\n  "stats": {"tconstruct:head": {"durability": 1500, "mining_speed": 8, '
                       '"melee_attack": 3, "mining_tier": "minecraft:netherite"}}, '
                       '"synthetic_variant": %d\r\n}\r\n' % index).encode()
            source.write_bytes(payload)
            payloads[name] = payload
        self.freeze()
        result = self.prepare()
        for name, payload in payloads.items():
            source, target = self.base / name, self.output / name
            expected = hashlib.sha256(payload).hexdigest()
            self.assertEqual(source.read_bytes(), payload)
            self.assertEqual(target.read_bytes(), payload)
            self.assertEqual(result["inputs"][str(source)], expected)
            self.assertEqual(result["files"][name], expected)
            self.assertNotEqual((source.stat().st_dev, source.stat().st_ino),
                                (target.stat().st_dev, target.stat().st_ino))
            self.assertEqual(target.stat().st_nlink, 1)
        client.verify_inputs(self.output / "client-metadata.json")

    def test_stats_exception_is_exact_names_parent_and_client_scope_only(self):
        parent = client.PUBLIC_MATERIAL_STATS_DIR
        for name in ["config/stats/a.json", "world/stats/a.json", "kubejs/data/other/stats/a.json",
                     parent + "/arbitrary.json", parent + "/dragonsteel_fire.JSON",
                     parent + "/dragonsteel_fire.json/child", parent + "/sub/dragonsteel_fire.json",
                     parent + "/credentials.json", parent + "/uuid.json", parent + "/session.lock",
                     parent + "/https://example.com/dragonsteel_fire.json",
                     parent + "/../stats/dragonsteel_fire.json", "https://example.com/dragonsteel_fire.json"]:
            with self.subTest(name=name), self.assertRaises(client.Refused):
                client.rel(name, client.TREES)
        for trees in [client.CACHE_TREES, {"kubejs"}, client.CACHE_TREES | {"kubejs"}]:
            for name in [parent, *client.PUBLIC_MATERIAL_STATS_FILES]:
                with self.subTest(name=name, trees=trees), self.assertRaises(client.Refused):
                    client.rel(name, trees)
        for name in [parent, *client.PUBLIC_MATERIAL_STATS_FILES]:
            self.assertEqual(client.rel(name, set(client.TREES)), name)
        path = write(self.base / parent / "arbitrary.json", "not approved")
        with self.assertRaises(client.Refused): client.inventory(self.base, client.TREES)
        path.unlink()

    def test_exact_material_stats_cannot_be_links_directories_or_file_parent(self):
        parent = self.base / client.PUBLIC_MATERIAL_STATS_DIR
        parent.parent.mkdir(parents=True)
        parent.write_text("directory substitution")
        with self.assertRaises(client.Refused): client.inventory(self.base, client.TREES)
        parent.unlink(); parent.mkdir()
        for name in sorted(client.PUBLIC_MATERIAL_STATS_FILES):
            source = self.base / name
            source.parent.mkdir(parents=True, exist_ok=True)
            source.symlink_to(self.base / "config/render.toml")
            with self.assertRaises(client.Refused): client.inventory(self.base, client.TREES)
            source.unlink(); os.link(self.base / "config/render.toml", source)
            with self.assertRaises(client.Refused): client.inventory(self.base, client.TREES)
            source.unlink(); source.mkdir()
            with self.assertRaises(client.Refused): client.inventory(self.base, client.TREES)
            source.rmdir()
        self.assertFalse(self.output.exists())

    def test_exact_public_wood_stats_preserves_bytes_hash_and_scope_precision(self):
        name = "kubejs/data/tconstruct/tinkering/materials/stats/wood.json"
        source = self.base / name
        source.parent.mkdir(parents=True)
        payload = b'{\r\n"stats":{"tconstruct:limb":{"durability":60}}\r\n}\r\n'
        source.write_bytes(payload)
        self.freeze(); result = self.prepare()
        target = self.output / name
        expected = hashlib.sha256(payload).hexdigest()
        self.assertEqual(source.read_bytes(), payload)
        self.assertEqual(target.read_bytes(), payload)
        self.assertEqual(result["inputs"][str(source)], expected)
        self.assertEqual(result["files"][name], expected)
        self.assertNotEqual((source.stat().st_dev, source.stat().st_ino), (target.stat().st_dev, target.stat().st_ino))
        self.assertEqual(target.stat().st_nlink, 1)
        client.verify_inputs(self.output / "client-metadata.json")
        for rejected in [name.replace("wood.json", "other.json"), name.replace("wood.json", "Wood.json"),
                         name + "/child", name.replace("wood.json", "sub/wood.json"),
                         name.replace("wood.json", "credentials.json"), name.replace("wood.json", "uuid.json"),
                         name.replace("wood.json", "https://example.com/wood.json"),
                         "config/stats/wood.json", "world/stats/wood.json", "kubejs/data/other/tinkering/materials/stats/wood.json"]:
            with self.subTest(path=rejected), self.assertRaises(client.Refused): client.rel(rejected, client.TREES)
        for trees in [client.CACHE_TREES, {"kubejs"}, client.CACHE_TREES | {"kubejs"}]:
            for path in [name, client.PUBLIC_WOOD_STATS_DIR]:
                with self.subTest(path=path, trees=trees), self.assertRaises(client.Refused): client.rel(path, trees)
        source.write_bytes(payload + b"changed")
        with self.assertRaises(client.Refused): client.verify_inputs(self.output / "client-metadata.json")

    def test_wood_parent_must_remain_a_directory(self):
        path = self.base / client.PUBLIC_WOOD_STATS_DIR
        path.parent.mkdir(parents=True)
        path.write_text("not a directory")
        with self.assertRaises(client.Refused): client.inventory(self.base, client.TREES)
        self.assertFalse(self.output.exists())

    def test_old_eight_role_tuple_without_bumblezone_is_refused(self):
        dest = self.m["roles"].pop("bumblezone")
        self.m["overlays"] = [r for r in self.m["overlays"] if r["destination"] != dest]
        with self.assertRaises(client.Refused): self.prepare()
        self.assertFalse(self.output.exists())

    def test_bumblezone_role_cannot_be_optional_or_wrong_destination(self):
        dest = self.roles["bumblezone"]
        self.m["overlays"] = [r for r in self.m["overlays"] if r["destination"] != dest]
        with self.assertRaises(client.Refused): self.prepare()
        self.m["overlays"].append({"source": str(self.server / dest), "sha256": client.digest(self.server / dest),
                                   "destination": "mods/renamed-bcc.jar"})
        self.m["roles"]["bumblezone"] = "mods/renamed-bcc.jar"
        with self.assertRaises(client.Refused): self.prepare()

    def test_bumblezone_server_hash_and_mod_id_are_both_required(self):
        row = next(r for r in self.m["overlays"] if r["destination"] == client.BUMBLEZONE_JAR)
        for mod in ["wrong_crops_mod", "better_bumblezone_crops"]:
            other = jar(self.root / (mod + ".jar"), mod)
            with zipfile.ZipFile(other, "a") as archive: archive.writestr("changed.txt", "not server bytes")
            row.update(source=str(other), sha256=client.digest(other))
            with self.assertRaises(client.Refused): self.prepare()
        self.assertFalse(self.output.exists())

    def test_no_previous_world_options_or_identity_copied_no_shared_inodes(self):
        self.prepare()
        for name in ["saves", "logs", "accounts.json", "usercache.json", "servers.dat", "bin"]:
            self.assertFalse((self.output / name).exists())
        self.assertNotIn("OLD IDENTITY", (self.output / "options.txt").read_text())
        self.assertNotEqual((self.output / "mods/emi.jar").stat().st_ino, (self.base / "mods/emi.jar").stat().st_ino)
        self.assertEqual((self.output / "mods/emi.jar").stat().st_nlink, 1)

    def test_output_reuse_and_escape_refused(self):
        self.output.mkdir()
        with self.assertRaises(client.Refused): self.prepare()
        self.output = self.root / "outside"
        with self.assertRaises(client.Refused): self.prepare()

    def test_incomplete_base_or_cache_inventory_refused(self):
        for key in ["base_files", "cache_files"]:
            original = self.m[key]
            self.m[key] = original[1:]
            with self.assertRaises(client.Refused): self.prepare()
            self.m[key] = original
        self.assertFalse(self.output.exists())

    def test_duplicate_json_and_duplicate_inventory_refused(self):
        self.m["base_files"].append(copy.deepcopy(self.m["base_files"][0]))
        with self.assertRaises(client.Refused): self.prepare()
        write(self.manifest, '{"schema":"a","schema":"b"}')
        with self.assertRaises(client.Refused): client.load(self.manifest)

    def test_symlink_and_hardlink_inputs_refused(self):
        p = self.base / "config/link"
        p.symlink_to(self.base / "config/render.toml")
        with self.assertRaises(client.Refused): self.prepare()
        p.unlink(); os.link(self.base / "config/render.toml", p)
        with self.assertRaises(client.Refused): self.prepare()

    def test_original_replacement_hash_is_required(self):
        dest = self.roles["water"]
        jar(self.base / dest, client.ROLE_IDS["water"])
        self.freeze()
        with self.assertRaises(client.Refused): self.prepare()
        row = next(x for x in self.m["overlays"] if x["destination"] == dest)
        row["replaces_sha256"] = client.digest(self.base / dest)
        self.prepare()

    def test_server_role_mismatch_and_actual_fixture_mutation_refused(self):
        row = self.m["overlays"][0]
        other = jar(self.root / "other.jar", client.ROLE_IDS["cooking_main"])
        with zipfile.ZipFile(other, "a") as z: z.writestr("changed.txt", "different")
        row.update(source=str(other), sha256=client.digest(other))
        with self.assertRaises(client.Refused): self.prepare()
        row.update(source=str(self.server / self.roles["cooking_main"]), sha256=client.digest(self.server / self.roles["cooking_main"]))
        with (self.server / self.roles["water"]).open("ab") as stream: stream.write(b"mutated")
        with self.assertRaises(client.Refused): self.prepare()

    def test_duplicate_mod_id_and_extra_overlay_refused(self):
        jar(self.base / "mods/duplicate.jar", "emi"); self.freeze()
        with self.assertRaises(client.Refused): self.prepare()
        (self.base / "mods/duplicate.jar").unlink(); self.freeze()
        self.m["overlays"].append({"source": str(self.base / "mods/emi.jar"), "destination": "mods/extra.jar", "sha256": client.digest(self.base / "mods/emi.jar")})
        with self.assertRaises(client.Refused): self.prepare()

    def test_credentials_under_whitelisted_content_refused(self):
        write(self.base / "config/credentials.json", "secret")
        with self.assertRaises(client.Refused): self.prepare()

    def test_paths_cannot_escape_or_supply_old_options(self):
        for name in ["../mods/evil.jar", "mods/../evil.jar", "/mods/evil.jar", "mods//evil.jar", "mods\\evil.jar", "options.txt", "config/usercache.json", "mods/x:y.jar"]:
            with self.subTest(name=name), self.assertRaises(client.Refused): client.rel(name, client.TREES)

    def test_broken_forge_target_and_unknown_placeholders_not_weakened(self):
        f = client.load(self.forge_path)
        f["arguments"]["game"] = ["--launchTarget", "forgeserver"]
        write(self.forge_path, f); self.freeze()
        with self.assertRaises(client.Refused): self.prepare()
        f["arguments"]["game"] = ["--launchTarget", "forgeclient", "${unknown}"]
        write(self.forge_path, f); self.freeze()
        with self.assertRaises(client.Refused): self.prepare()

    def test_asset_and_jar_integrity_not_just_new_manifest_hash(self):
        obj = next(p for p in (self.cache / "assets/objects").rglob("*") if p.is_file())
        obj.write_bytes(b"modified")
        self.freeze()
        with self.assertRaises(client.Refused): self.prepare()

    def test_external_baseline_and_wrong_java_release_refused(self):
        self.m["base_client"] = str(self.root / "not-approved")
        with self.assertRaises(client.Refused): self.prepare()
        self.m["base_client"] = str(self.base)
        release = Path(self.m["java"]).parent.parent / "release"
        release.write_text('JAVA_VERSION="21.0.1"\n')
        self.m["java_release_sha256"] = client.digest(release)
        with self.assertRaises(client.Refused): self.prepare()

    def test_before_after_mutation_detected_and_failed_fixture_retained(self):
        original = client.shutil.copyfile
        def mutate(source, target):
            value = original(source, target)
            if source == self.base / "config/render.toml": source.write_text("MUTATED")
            return value
        with patch.object(client.shutil, "copyfile", side_effect=mutate), self.assertRaises(client.Refused): self.prepare()
        self.assertTrue((self.output / "PREPARATION_FAILED.json").is_file())

    def test_post_launch_verifier_detects_source_mutation(self):
        self.prepare()
        (self.base / "config/render.toml").write_text("modified later")
        with self.assertRaises(client.Refused): client.verify_inputs(self.output / "client-metadata.json")

    def test_native_archive_escape_and_symlink_refused(self):
        p = self.root / "unsafe.jar"
        with zipfile.ZipFile(p, "w") as z: z.writestr("../evil.so", b"bad")
        with self.assertRaises(client.Refused): client.extract_natives([p], self.root / "bad-natives")

    def test_missing_installed_forge_runtime_is_not_rebuilt_or_downloaded(self):
        (self.cache / "libraries/net/minecraftforge/fmlcore/1.20.1-47.4.22/fmlcore-1.20.1-47.4.22.jar").unlink()
        self.freeze()
        with self.assertRaises(client.Refused): self.prepare()
        self.assertFalse(self.output.exists())

    def test_optional_display_is_only_a_fresh_tcp_authority_plan(self):
        self.m["display_number"] = 5432
        value = self.prepare()["display"]
        self.assertFalse(value["started"])
        self.assertIn("unix", value["inner_command"])
        self.assertNotIn("-nolock", value["inner_command"])
        self.assertEqual(value["inner_command"][value["inner_command"].index("-displayfd") + 1], "1")
        self.assertEqual(value["planned_command"][:7], ["/usr/bin/unshare", "--user", "--map-root-user", "--mount", "--propagation", "private", "--"])
        self.assertEqual((self.output / "runtime-xkb").stat().st_mode & 0o777, 0o700)
        self.assertEqual(value["environment"]["DISPLAY"], "127.0.0.1:5432")
        self.assertFalse((self.output / "Xauthority").exists())
        other = client.display_plan(self.output, 5432)
        self.assertNotEqual(value["prepare_authority_command"][-1], other["prepare_authority_command"][-1])
        with self.assertRaises(client.Refused): client.display_plan(self.output, 60000)

    def test_private_xkb_exact_plan_and_original_program_input_binding(self):
        self.m["display_number"] = 5432
        metadata = self.prepare()
        plan = metadata["display"]
        self.assertEqual(plan["private_xkb_storage"]["schema"], "bc.cooking.private_xkb_storage.v1")
        self.assertEqual(plan["private_xkb_storage"]["source"], str(self.output / "runtime-xkb"))
        self.assertEqual(plan["private_xkb_storage"]["target"], "/var/lib/xkb")
        self.assertEqual(plan["planned_command"][7:11], ["/usr/bin/python3", "-I", "-S", "-c"])
        self.assertEqual(json.loads(plan["planned_command"][-1]), plan["private_xkb_storage"])
        for row in self.display_program_rows.values():
            self.assertEqual(metadata["inputs"][row["physical_path"]], row["sha256"])
        client.verify_inputs(self.output / "client-metadata.json")

    def test_private_xkb_plan_mutations_refuse(self):
        self.m["display_number"] = 5432
        original = self.prepare()
        for mutate in [
            lambda p: p["planned_command"].append("--fork"),
            lambda p: p["planned_command"].__setitem__(11, "print('unapproved bootstrap')"),
            lambda p: p["inner_command"].append("-ac"),
            lambda p: p["private_xkb_storage"].__setitem__("target", "/tmp"),
            lambda p: p["private_xkb_storage"].__setitem__("source", str(self.output / "other")),
            lambda p: p["private_xkb_storage"].__setitem__("schema", "unapproved"),
            lambda p: p["private_xkb_storage"].__setitem__("host_uid", -1),
            lambda p: p["private_xkb_storage"].__setitem__("host_mount_namespace", "mnt:fake"),
            lambda p: p["private_xkb_storage"]["source_identity"].__setitem__("device_inode", [0, 0]),
            lambda p: p.__setitem__("environment", {"DISPLAY": ":0"}),
            lambda p: p.__setitem__("started", True),
            lambda p: p.__setitem__("extra_overlay", "arbitrary"),
        ]:
            with self.subTest(mutate=mutate):
                value = copy.deepcopy(original); mutate(value["display"])
                with self.assertRaises(client.Refused): client.verify_display(value)

    def test_private_xkb_source_mode_change_refuses(self):
        self.m["display_number"] = 5432
        metadata = self.prepare()
        (self.output / "runtime-xkb").chmod(0o755)
        with self.assertRaises(client.Refused): client.verify_display(metadata)

    def test_private_xkb_source_replacement_refuses(self):
        self.m["display_number"] = 5432
        metadata = self.prepare()
        source = self.output / "runtime-xkb"
        source.rename(self.output / "old-xkb")
        source.mkdir(mode=0o700)
        with self.assertRaises(client.Refused): client.verify_display(metadata)

    def test_private_xkb_source_symlink_refuses(self):
        self.m["display_number"] = 5432
        metadata = self.prepare()
        source = self.output / "runtime-xkb"
        source.rename(self.output / "old-xkb")
        source.symlink_to(self.output / "old-xkb", target_is_directory=True)
        with self.assertRaises(client.Refused): client.verify_display(metadata)

    def test_private_xkb_host_target_or_program_change_refuses(self):
        self.m["display_number"] = 5432
        metadata = self.prepare()
        with patch.object(client, "xkb_host_identity", return_value=[10, 99, 30, 40]):
            with self.assertRaises(client.Refused): client.verify_display(metadata)
        self.display_program_rows["/usr/bin/Xvfb"]["sha256"] = "0" * 64
        with self.assertRaises(client.Refused): client.verify_display(metadata)

    def test_private_xkb_missing_program_input_refuses(self):
        self.m["display_number"] = 5432
        metadata = self.prepare()
        del metadata["inputs"][self.display_program_rows["/usr/bin/Xvfb"]["physical_path"]]
        with self.assertRaises(client.Refused): client.verify_display(metadata)

    def test_private_xkb_program_bytes_change_refuses_full_verification(self):
        self.m["display_number"] = 5432
        self.prepare()
        Path(self.display_program_rows["/usr/bin/Xvfb"]["physical_path"]).write_text("changed synthetic bytes")
        with self.assertRaises(client.Refused): client.verify_inputs(self.output / "client-metadata.json")

    def test_private_xkb_cannot_downgrade_manifest_requested_display(self):
        self.m["display_number"] = 5432
        metadata = self.prepare()
        metadata["display"] = {"started": False, "requires_coordinator_display": True}
        with self.assertRaises(client.Refused): client.verify_display(metadata)

    def test_private_xkb_cannot_change_manifest_display_number(self):
        self.m["display_number"] = 5432
        metadata = self.prepare()
        metadata["display"]["number"] = 5433
        with self.assertRaises(client.Refused): client.verify_display(metadata)

    def test_private_xkb_bootstrap_static_no_shell_assert_or_fallback(self):
        import ast
        tree = ast.parse(client.PRIVATE_XKB_BOOTSTRAP)
        self.assertFalse(any(isinstance(n, ast.Assert) for n in ast.walk(tree)))
        self.assertNotIn("shell=True", client.PRIVATE_XKB_BOOTSTRAP)
        self.assertNotIn("/tmp", client.PRIVATE_XKB_BOOTSTRAP)
        self.assertIn('"--no-mtab","--bind"', client.PRIVATE_XKB_BOOTSTRAP)
        self.assertIn('os.execv("/usr/bin/Xvfb",s["inner_command"])', client.PRIVATE_XKB_BOOTSTRAP)
        self.assertIn('uidmap==["0",str(s["host_uid"]),"1"]', client.PRIVATE_XKB_BOOTSTRAP)

    def test_added_readonly_cache_file_is_detected_after_launch(self):
        self.prepare()
        write(self.cache / "libraries/new-generated.cache", "unexpected write")
        with self.assertRaises(client.Refused): client.verify_inputs(self.output / "client-metadata.json")

    def test_client_mod_mutation_is_detected_after_launch(self):
        self.prepare()
        with (self.output / self.roles["cooking_main"]).open("ab") as stream: stream.write(b"mutation")
        with self.assertRaises(client.Refused): client.verify_inputs(self.output / "client-metadata.json")

    def test_home_and_injected_java_environment_are_isolated(self):
        value = self.prepare()
        self.assertEqual(value["planned_environment"]["HOME"], str(self.output / "home"))
        self.assertIn("-Duser.home=" + str(self.output / "home"), value["planned_command"])
        self.assertIn("JAVA_TOOL_OPTIONS", value["must_unset_environment"])
        self.assertTrue((self.output / "home").is_dir())

    def server_arguments(self, args):
        value = client.load(self.server_meta)
        value["jvm_arguments"] = args
        write(self.server_meta, value)
        self.m["server_metadata_sha256"] = client.digest(self.server_meta)

    def test_discovery_mode_true_false_matches_server_and_precedes_forge_main(self):
        for mode in [True, False]:
            with self.subTest(mode=mode):
                self.output = self.project / ("discovery" if mode else "production")
                self.m["discovery_only"] = mode
                flag = client.DISCOVERY_PROPERTY + ("=true" if mode else "=false")
                self.server_arguments(["-Xmx6G", flag])
                result = self.prepare(); command = result["planned_command"]
                self.assertIs(result["discovery_only"], mode)
                self.assertEqual(command.count(flag), 1)
                self.assertLess(command.index(flag), command.index(client.FORGE_MAIN))
                self.assertNotIn(flag, command[command.index(client.FORGE_MAIN) + 1:])
                client.verify_inputs(self.output / "client-metadata.json")

    def test_absent_mode_stays_false_never_silently_discovery(self):
        result = self.prepare()
        self.assertIs(result["discovery_only"], False)
        self.assertIs(result["fixture_namespace_classification"], False)
        self.assertIn(client.DISCOVERY_PROPERTY + "=false", result["planned_command"])
        self.assertNotIn(client.DISCOVERY_PROPERTY + "=true", result["planned_command"])

    def test_discovery_manifest_type_server_type_duplicate_and_mismatch_refused(self):
        for bad in [None, 0, 1, "true", "false", [], {}]:
            self.m["discovery_only"] = bad
            with self.subTest(value=bad), self.assertRaises(client.Refused): self.prepare()
        self.m["discovery_only"] = False
        prefix = client.DISCOVERY_PROPERTY
        for args in [None, "-Xmx6G", [True], [0], [prefix], [prefix + "=TRUE"],
                     [prefix + "=true "], [prefix + "=0"], [prefix + "=true"],
                     [prefix + "=false", prefix + "=false"], [prefix + "=false", prefix + "=true"]]:
            self.server_arguments(args)
            with self.subTest(args=args), self.assertRaises(client.Refused): self.prepare()
        self.server_arguments([]); self.m["discovery_only"] = True
        with self.assertRaises(client.Refused): self.prepare()
        self.assertFalse(self.output.exists())

    def test_prior_launcher_mode_matching_normalizes_once_conflict_duplicate_or_game_flag_refused(self):
        forge = client.load(self.forge_path); prefix = client.DISCOVERY_PROPERTY
        for args, game in [([prefix + "=true"], []), ([prefix + "=false", prefix + "=false"], []),
                           ([prefix + "=false", prefix + "=true"], []), ([prefix + "=False"], []),
                           ([], [prefix + "=false"])]:
            forge["arguments"]["jvm"] = args
            forge["arguments"]["game"] = ["--launchTarget", "forgeclient", *game]
            write(self.forge_path, forge); self.freeze()
            with self.subTest(args=args, game=game), self.assertRaises(client.Refused): self.prepare()
        forge["arguments"]["jvm"] = [prefix + "=false"]
        forge["arguments"]["game"] = ["--launchTarget", "forgeclient"]
        write(self.forge_path, forge); self.freeze()
        self.assertEqual(self.prepare()["planned_command"].count(prefix + "=false"), 1)

    def test_receipted_mode_or_property_position_mutation_refused_by_verifier(self):
        self.prepare(); path = self.output / "client-metadata.json"; original = client.load(path)
        for mutation in ["mode", "after_main", "duplicate"]:
            value = copy.deepcopy(original)
            if mutation == "mode": value["discovery_only"] = True
            else:
                flag = client.DISCOVERY_PROPERTY + "=false"
                if mutation == "after_main": value["planned_command"].remove(flag)
                value["planned_command"].append(flag)
            write(path, value)
            with self.subTest(mutation=mutation), self.assertRaises(client.Refused): client.verify_inputs(path)

    def test_discovery_and_classification_duplicate_json_keys_refused(self):
        for name in ["discovery_only", "fixture_namespace_classification"]:
            write(self.manifest, '{"' + name + '":false,"' + name + '":true}')
            with self.subTest(name=name), self.assertRaises(client.Refused): client.load(self.manifest)

    def test_unit_only_abstract_namespace_tree_exact_three_not_installable_approval(self):
        base = {"enforcement": {"unknown_loaded_namespaces": "blocking"}, "namespaces": {}, "unchanged": {"typed": 1}}
        expected = copy.deepcopy(base); expected["namespaces"].update(copy.deepcopy(client.FIXTURE_NAMESPACE_ROWS))
        client.fixture_namespace_tree(base, expected)  # UNIT MODEL ONLY: no approved hashes, not installable.
        for mutation in ["fourth", "other", "row", "guard", "type"]:
            bad = copy.deepcopy(expected)
            if mutation == "fourth": bad["namespaces"]["unknown_fourth"] = {"primary_role": "integration"}
            if mutation == "other": bad["unchanged"]["extra"] = True
            if mutation == "row": bad["namespaces"]["better_cooking"]["support_state"] = "qualified"
            if mutation == "guard": bad["enforcement"]["unknown_loaded_namespaces"] = "report"
            if mutation == "type": bad["unchanged"]["typed"] = True
            with self.subTest(mutation=mutation), self.assertRaises(client.Refused): client.fixture_namespace_tree(base, bad)
        synthetic = write(self.base / client.FIXTURE_POLICY_DEST, base); self.freeze()
        with self.assertRaises(client.Refused): client.fixture_namespace_input({client.FIXTURE_POLICY_DEST: (synthetic, client.digest(synthetic))}, client.load(self.server_meta))

    def test_classification_boolean_mode_and_generic_policy_overlay_refused(self):
        for flag in [True, "true", 1, None]:
            self.m["fixture_namespace_classification"] = flag
            with self.subTest(flag=flag), self.assertRaises(client.Refused): self.prepare()
        self.m["discovery_only"] = True; self.m["fixture_namespace_classification"] = True
        self.server_arguments([client.DISCOVERY_PROPERTY + "=true"])
        with self.assertRaises(client.Refused): self.prepare()  # No approved baseline bytes in this synthetic fixture.
        self.m["overlays"].append({"destination": client.FIXTURE_POLICY_DEST, "source": str(self.base / "config/render.toml"), "sha256": client.digest(self.base / "config/render.toml")})
        with self.assertRaises(client.Refused): self.prepare()  # No generic non-JAR path/source channel.
        self.assertFalse(self.output.exists())

    def test_classification_receipt_cannot_approve_a_self_hashed_synthetic_output(self):
        self.m["discovery_only"] = True
        self.server_arguments([client.DISCOVERY_PROPERTY + "=true"])
        result = self.prepare()  # Classification is false; no approved policy was copied.
        fake = write(self.output / client.FIXTURE_POLICY_DEST, {"synthetic": "NOT APPROVED FIXTURE POLICY"})
        result["fixture_namespace_classification"] = True
        result["immutable_outputs"][str(fake)] = client.digest(fake)
        write(self.output / "client-metadata.json", result)
        with self.assertRaisesRegex(client.Refused, "immutable classification mode"):
            client.verify_inputs(self.output / "client-metadata.json")

    def test_linux_and_feature_rules_are_explicit(self):
        self.assertFalse(client.allowed([{"action": "allow", "os": {"name": "windows"}}], {}))
        self.assertTrue(client.allowed([{"action": "allow", "os": {"name": "linux"}}], {}))
        self.assertFalse(client.allowed([{"action": "allow", "features": {"is_demo_user": True}}], {}))
        with self.assertRaises(client.Refused): client.allowed([{"action": "maybe"}], {})


if __name__ == "__main__":
    unittest.main()
