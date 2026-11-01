#!/usr/bin/env python3
"""Prepare a NEW hash-bound real Forge client; never launch Java, displays or downloads."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import secrets
import shutil
import stat
import uuid
import zipfile

from native_fixture import (Refused, checked_hash, check, digest, mod_ids,
                            runner_guard, strict, unique_object)

SCHEMA = "bc.cooking.native_client.v1"
SOURCE_ROOT = Path(__file__).absolute().parents[1]
BASE_CLIENT = Path("/home/dev/better-content-modpack/generated/test-evidence/20261027T230352Z-529700/smoke/fixture/client")
CACHE_ROOT = Path.home() / ".cache/bc/client-launcher"
VERSION = "forge-1.20.1-47.4.22"
# Explicit final cohort: never inherit an older/discovery-only server helper's eight-role set.
ROLE_IDS = {"cooking_main": "better_cooking", "native_fixture": "better_cooking_fixture",
            "physiology": "better_survival_physiology", "water": "better_drinking_water",
            "heat": "better_industrial_heat", "spice": "better_spicy_farmers",
            "kaleidoscope": "kaleidoscope_cookery", "commerce": "better_spirit_commerce",
            "bumblezone": "better_bumblezone_crops"}
BUMBLEZONE_JAR = "mods/better-bumblezone-crops-0.1.0.jar"
TREES = {"mods", "config", "defaultconfigs", "kubejs", "resources", "defaultresources",
         "resourcepacks", "shaderpacks", "global_packs", "globalresources", "datapacks",
         "moonlight-global-datapacks", "patchouli_books", "tacz", "thingpacks", "trees",
         "schematics", "fancymenu_data"}
CACHE_TREES = {"assets", "libraries", "versions"}
# Audited pack-global FancyMenu settings, NOT Minecraft's player options.txt.
# Exact case-sensitive client-content path only; never a basename/cache exception.
GLOBAL_OPTIONS_FILE = "config/fancymenu/options.txt"
# Root-reviewed public TConstruct material definitions, not saved player statistics.
PUBLIC_MATERIAL_STATS_DIR = "kubejs/data/kubejs/tinkering/materials/stats"
PUBLIC_WOOD_STATS_DIR = "kubejs/data/tconstruct/tinkering/materials/stats"
PUBLIC_WOOD_STATS_FILE = f"{PUBLIC_WOOD_STATS_DIR}/wood.json"
PUBLIC_MATERIAL_STATS_DIRS = frozenset({PUBLIC_MATERIAL_STATS_DIR, PUBLIC_WOOD_STATS_DIR})
PUBLIC_MATERIAL_STATS_FILES = frozenset(
    f"{PUBLIC_MATERIAL_STATS_DIR}/dragonsteel_{kind}.json" for kind in ("fire", "ice", "lightning")
) | {PUBLIC_WOOD_STATS_FILE}
FORBIDDEN = {"world", "worlds", "saves", "logs", "crash-reports", "playerdata", "stats",
             "options.txt", "optionsof.txt", "servers.dat", "servers.dat_old", "session.lock",
             "usercache.json", "usernamecache.json", "launcher_profiles.json", "ops.json",
             "whitelist.json", "banned-players.json", "banned-ips.json", "accounts.json"}
SENSITIVE = re.compile(r"(?i)(credential|access.?token|launcher.?account|uuid|usercache|usernamecache|\.pem$|\.key$)")


def regular(path: Path) -> Path:
    strict(path)
    info = path.stat()
    if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
        raise Refused(f"independent regular input required (no hardlinks): {path}")
    return path


def rel(value: str, trees: set[str]) -> str:
    if not isinstance(value, str) or not value or "\\" in value or ":" in value:
        raise Refused("invalid relative input")
    p = PurePosixPath(value)
    if p.is_absolute() or str(p) != value or any(x in {"", ".", ".."} for x in value.split("/")):
        raise Refused(f"escaping/noncanonical input: {value}")
    global_options = trees == TREES and value == GLOBAL_OPTIONS_FILE
    material_stats = trees == TREES and (value in PUBLIC_MATERIAL_STATS_DIRS or value in PUBLIC_MATERIAL_STATS_FILES)
    forbidden = any(x.lower() in FORBIDDEN and not (
        (global_options and index == len(p.parts) - 1 and x == "options.txt") or
        (material_stats and index == 5 and x == "stats")
    ) for index, x in enumerate(p.parts))
    if p.parts[0] not in trees or forbidden or any(SENSITIVE.search(x) for x in p.parts):
        raise Refused(f"outside content whitelist or identity/world path: {value}")
    return value


def inventory(root: Path, trees: set[str]) -> set[str]:
    strict(root, exists=False)
    if not root.is_dir():
        raise Refused(f"missing input directory: {root}")
    found = set()
    for tree in sorted(trees):
        start = root / tree
        if start.is_symlink():
            raise Refused(f"symlink tree: {start}")
        if not start.exists():
            continue
        if not start.is_dir():
            raise Refused(f"content root is not a directory: {start}")
        for directory, dirs, files in os.walk(start, followlinks=False):
            for name in dirs + files:
                path = Path(directory) / name
                rel(path.relative_to(root).as_posix(), trees)
                if path.is_symlink():
                    raise Refused(f"symlink descendant: {path}")
                relative = path.relative_to(root).as_posix()
                if (relative == GLOBAL_OPTIONS_FILE or relative in PUBLIC_MATERIAL_STATS_FILES) and path.is_dir():
                    raise Refused(f"approved content must be an independent regular file: {path}")
                if relative in PUBLIC_MATERIAL_STATS_DIRS and not path.is_dir():
                    raise Refused(f"approved material stats parent must be a directory: {path}")
            for name in files:
                path = regular(Path(directory) / name)
                found.add(rel(path.relative_to(root).as_posix(), trees))
    return found


def load(path: Path) -> dict:
    return json.loads(regular(path).read_text(), object_pairs_hook=unique_object)


def bound_files(root: Path, rows: list, trees: set[str]) -> dict[str, tuple[Path, str]]:
    result = {}
    for entry in rows:
        name = rel(entry["path"], trees)
        if name in result:
            raise Refused(f"duplicate input: {name}")
        path = regular(root / name)
        expected = checked_hash(entry["sha256"])
        check(path, expected)
        result[name] = (path, expected)
    if set(result) != inventory(root, trees):
        raise Refused(f"manifest must exactly bind every whitelisted input under {root}")
    return result


def allowed(rules: list | None, features: dict[str, bool]) -> bool:
    if rules is None:
        return True
    answer = False
    for rule in rules:
        if set(rule) - {"action", "os", "features"} or rule["action"] not in {"allow", "disallow"}:
            raise Refused("unsupported metadata rule")
        os_rule = rule.get("os", {})
        if set(os_rule) - {"name", "arch", "version"}:
            raise Refused("unsupported OS rule")
        matched = ("name" not in os_rule or os_rule["name"] == "linux")
        matched &= ("arch" not in os_rule or re.fullmatch(os_rule["arch"], "x86_64") is not None)
        if "version" in os_rule:
            matched &= re.search(os_rule["version"], os.uname().release) is not None
        matched &= all(features.get(k, False) == v for k, v in rule.get("features", {}).items())
        if matched:
            answer = rule["action"] == "allow"
    return answer


def arguments(rows: list, replacements: dict[str, str], features: dict[str, bool]) -> list[str]:
    result = []
    for row in rows:
        if isinstance(row, dict):
            if set(row) != {"rules", "value"}:
                raise Refused("unsupported conditional argument")
            if not allowed(row["rules"], features):
                continue
            row = row["value"]
        for arg in [row] if isinstance(row, str) else row:
            if not isinstance(arg, str) or "\x00" in arg or "\n" in arg or arg.startswith("@"):
                raise Refused("unsafe argument")
            def replace(match):
                key = match.group(1)
                if key not in replacements:
                    raise Refused(f"unknown argument placeholder: {key}")
                return replacements[key]
            result.append(re.sub(r"\$\{([^}]+)}", replace, arg))
    return result


DISCOVERY_PROPERTY = "-Dbetter_cooking.discoveryOnly"
FORGE_MAIN = "cpw.mods.bootstraplauncher.BootstrapLauncher"


def discovery_property(args: list) -> bool | None:
    """Only the exact JVM boolean spelling; absence is not an implicit discovery grant."""
    if type(args) is not list or any(type(arg) is not str or "\x00" in arg or "\n" in arg or "\r" in arg for arg in args):
        raise Refused("discovery binding requires a list of string JVM arguments")
    found = [arg for arg in args if arg.lstrip().startswith(DISCOVERY_PROPERTY)]
    # A different Java property whose name merely shares the prefix is not this switch.
    found = [arg for arg in found if arg.lstrip() == DISCOVERY_PROPERTY or arg.lstrip().startswith(DISCOVERY_PROPERTY + "=")]
    if len(found) > 1:
        raise Refused("duplicate discoveryOnly JVM property")
    if not found:
        return None
    if found[0] not in {DISCOVERY_PROPERTY + "=true", DISCOVERY_PROPERTY + "=false"}:
        raise Refused("discoveryOnly JVM property must be exact lowercase true or false")
    return found[0].endswith("=true")


def matched_discovery_mode(value: bool, server: dict) -> bool:
    if type(value) is not bool:
        raise Refused("manifest discovery_only must be a JSON boolean")
    observed = discovery_property(server.get("jvm_arguments", []))
    if value != (False if observed is None else observed):
        raise Refused("client discovery_only differs from bound server JVM mode")
    return value


def verify_discovery_command(command: list, mode: bool) -> None:
    observed = discovery_property(command)
    if type(mode) is not bool or observed is None or observed != mode or command.count(FORGE_MAIN) != 1:
        raise Refused("client command must declare the receipted discovery mode exactly once")
    exact = DISCOVERY_PROPERTY + ("=true" if mode else "=false")
    if command.index(exact) >= command.index(FORGE_MAIN):
        raise Refused("client discoveryOnly property must precede Forge main")


def launcher(cache: Path, cache_files: dict, output: Path, port: int, identity: dict, java: Path,
             discovery_only: bool = False) -> tuple[list[str], list[Path]]:
    """Resolve the pinned Linux x86_64 metadata without invoking PortableMC/installers."""
    forge_path = cache / f"versions/{VERSION}/{VERSION}.json"
    vanilla_path = cache / "versions/1.20.1/1.20.1.json"
    forge, vanilla = load(forge_path), load(vanilla_path)
    if (forge.get("id") != VERSION or forge.get("inheritsFrom") != "1.20.1" or
        forge.get("mainClass") != "cpw.mods.bootstraplauncher.BootstrapLauncher" or
        vanilla.get("id") != "1.20.1" or vanilla.get("javaVersion", {}).get("majorVersion") != 17):
        raise Refused("only pinned Minecraft1.20.1 Forge47.4.22 Java17 is supported")
    features = {"has_custom_resolution": True, "is_quick_play_multiplayer": True}
    classpath, natives, seen = [], [], set()
    for metadata in [forge, vanilla]:
        for lib in metadata["libraries"]:
            if not allowed(lib.get("rules"), features):
                continue
            if "natives" in lib:
                raise Refused("legacy native metadata unsupported for this pinned target")
            coordinate = lib["name"].split(":")
            if len(coordinate) not in {3, 4}:
                raise Refused("unsupported library coordinate")
            key = tuple(coordinate[:2] + coordinate[3:])
            if key in seen:
                continue
            seen.add(key)
            artifact = lib["downloads"]["artifact"]
            path = cache / rel("libraries/" + artifact["path"], CACHE_TREES)
            if path.relative_to(cache).as_posix() not in cache_files:
                raise Refused(f"unbound launcher library: {path}")
            if hashlib.sha1(regular(path).read_bytes()).hexdigest() != artifact["sha1"]:
                raise Refused(f"Mojang/Forge library SHA1 mismatch: {path}")
            classpath.append(str(path))
            if len(coordinate) == 4 and coordinate[3] == "natives-linux":
                natives.append(path)
    client = cache / f"versions/{VERSION}/{VERSION}.jar"
    if client.relative_to(cache).as_posix() not in cache_files or hashlib.sha1(regular(client).read_bytes()).hexdigest() != vanilla["downloads"]["client"]["sha1"]:
        raise Refused("unbound/incorrect original Mojang client JAR")
    classpath.append(str(client))
    asset_id = vanilla["assetIndex"]["id"]
    index_path = cache / rel(f"assets/indexes/{asset_id}.json", CACHE_TREES)
    if hashlib.sha1(regular(index_path).read_bytes()).hexdigest() != vanilla["assetIndex"]["sha1"]:
        raise Refused("Mojang asset index SHA1 mismatch")
    for item in load(index_path)["objects"].values():
        sha1 = item["hash"]
        if not re.fullmatch(r"[0-9a-f]{40}", sha1):
            raise Refused("invalid Mojang object hash")
        name = f"assets/objects/{sha1[:2]}/{sha1}"
        if name not in cache_files or hashlib.sha1((cache / name).read_bytes()).hexdigest() != sha1:
            raise Refused(f"missing/incorrect Mojang asset: {name}")
    # Forge's early launch handler resolves these runtime artifacts via libraryDirectory.
    for name in ["net/minecraftforge/forge/1.20.1-47.4.22/forge-1.20.1-47.4.22-client.jar",
                 "net/minecraftforge/forge/1.20.1-47.4.22/forge-1.20.1-47.4.22-universal.jar",
                 "net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-extra.jar",
                 "net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-srg.jar",
                 *[f"net/minecraftforge/{name}/1.20.1-47.4.22/{name}-1.20.1-47.4.22.jar"
                   for name in ["fmlcore", "javafmllanguage", "lowcodelanguage", "mclanguage"]]]:
        if "libraries/" + name not in cache_files:
            raise Refused(f"missing preinstalled Forge runtime: {name}")
    replacements = {"auth_player_name": identity["username"], "auth_uuid": identity["uuid"],
                    "auth_access_token": "0", "clientid": "", "auth_xuid": "", "user_type": "",
                    "version_name": VERSION, "version_type": "release", "game_directory": str(output),
                    "library_directory": str(cache / "libraries"), "assets_root": str(cache / "assets"),
                    "assets_index_name": asset_id, "natives_directory": str(output / "natives"),
                    "launcher_name": "better-cooking-source-fixture", "launcher_version": "1",
                    "classpath_separator": ":", "classpath": ":".join(classpath),
                    "resolution_width": "1280", "resolution_height": "720",
                    "quickPlayMultiplayer": f"127.0.0.1:{port}"}
    jvm = arguments(vanilla["arguments"]["jvm"] + forge["arguments"]["jvm"], replacements, features)
    game = arguments(vanilla["arguments"]["game"] + forge["arguments"]["game"], replacements, features)
    for flag, value in {"--launchTarget": "forgeclient", "--quickPlayMultiplayer": f"127.0.0.1:{port}"}.items():
        if game.count(flag) != 1 or game[game.index(flag) + 1] != value:
            raise Refused(f"incorrect native client target/join flag: {flag}")
    if "--server" in game or "--quickPlaySingleplayer" in game or "--demo" in game:
        raise Refused("unexpected fallback/demo/singleplayer arguments")
    log = vanilla.get("logging", {}).get("client")
    if log:
        path = cache / rel("assets/log_configs/" + log["file"]["id"], CACHE_TREES)
        if path.relative_to(cache).as_posix() not in cache_files or hashlib.sha1(regular(path).read_bytes()).hexdigest() != log["file"]["sha1"]:
            raise Refused("unbound logging configuration")
        jvm.append(log["argument"].replace("${path}", str(path)))
    if type(discovery_only) is not bool:
        raise Refused("client launcher discovery_only must be boolean")
    prior = discovery_property(jvm)
    if prior is not None and prior != discovery_only:
        raise Refused("conflicting launcher metadata discoveryOnly property")
    if discovery_property(game) is not None:
        raise Refused("discoveryOnly property cannot be a post-main game argument")
    # Normalize one matching metadata declaration to exactly one explicit final JVM switch.
    jvm = [arg for arg in jvm if not arg.startswith(DISCOVERY_PROPERTY + "=")]
    mode_argument = DISCOVERY_PROPERTY + ("=true" if discovery_only else "=false")
    # Final preferIPv6 setting intentionally overrides Forge's earlier '=system', as Smoke does.
    command = [str(java), "-Xms1G", "-Xmx4G", "-XX:ActiveProcessorCount=2", "-XX:+UseG1GC",
               "-Dfile.encoding=UTF-8", "-Djava.io.tmpdir=" + str(output / "tmp"),
               "-Duser.home=" + str(output / "home"), *jvm,
               "-Djava.net.preferIPv6Addresses=false", mode_argument, forge["mainClass"], *game]
    verify_discovery_command(command, discovery_only)
    return command, natives


FIXTURE_POLICY_DEST = "kubejs/config/crafting_policy.json"
FIXTURE_POLICY_BASE_SHA = "909aba4bc18a0d0144d3c3f05f05ce74c68e96c955410c879deb91d619a8418e"
FIXTURE_POLICY_DERIVED_SHA = "0b75adb10a47059f9ce6f4ffd7c4045ea3400a9c5521aa96c063f5e7aeba10b6"
FIXTURE_NAMESPACE_ROWS = {
    "better_cooking": {"primary_role": "integration", "support_state": "fixture_unqualified"},
    "better_cooking_fixture": {"primary_role": "infrastructure", "support_state": "not_applicable"},
    "kaleidoscope_cookery": {"primary_role": "content", "support_state": "fixture_unqualified"},
}


def fixture_namespace_tree(base: dict, derived: dict) -> None:
    """Pure whole-JSON check; a model passing this is NOT approved bytes or installable input."""
    if (type(base) is not dict or type(derived) is not dict or type(base.get("namespaces")) is not dict
            or type(base.get("enforcement")) is not dict
            or base.get("enforcement", {}).get("unknown_loaded_namespaces") != "blocking"
            or any(name in base["namespaces"] for name in FIXTURE_NAMESPACE_ROWS)):
        raise Refused("fixture namespace classification requires unchanged blocking baseline")
    expected = json.loads(json.dumps(base))
    expected["namespaces"].update(FIXTURE_NAMESPACE_ROWS)
    if json.dumps(expected, sort_keys=True, separators=(",", ":")) != json.dumps(derived, sort_keys=True, separators=(",", ":")):
        raise Refused("fixture classification may add ONLY the exact three reviewed namespace rows")


def fixture_namespace_input(base_files: dict, server: dict) -> tuple[Path, str]:
    if FIXTURE_POLICY_DEST not in base_files or base_files[FIXTURE_POLICY_DEST][1] != FIXTURE_POLICY_BASE_SHA:
        raise Refused("fixture classification requires the exact approved baseline policy bytes")
    source = regular(Path(server["planned_cwd"]) / FIXTURE_POLICY_DEST)
    if server["files"].get(FIXTURE_POLICY_DEST) != FIXTURE_POLICY_DERIVED_SHA:
        raise Refused("fixture classification must be bound by server metadata files hash")
    check(base_files[FIXTURE_POLICY_DEST][0], FIXTURE_POLICY_BASE_SHA)
    check(source, FIXTURE_POLICY_DERIVED_SHA)
    fixture_namespace_tree(load(base_files[FIXTURE_POLICY_DEST][0]), load(source))
    return source, FIXTURE_POLICY_DERIVED_SHA


def fresh_identity() -> dict:
    username = "Cooking_" + secrets.token_hex(4)
    value = bytearray(hashlib.md5(("OfflinePlayer:" + username).encode()).digest())
    value[6] = (value[6] & 15) | 48
    value[8] = (value[8] & 63) | 128
    return {"username": username, "uuid": uuid.UUID(bytes=bytes(value)).hex,
            "accessToken": "0", "authentication": "fresh ephemeral offline loopback only"}


# Fixed, reviewed namespace bootstrap; data is JSON, never caller-supplied code or shell.
PRIVATE_XKB_BOOTSTRAP = r'''import hashlib,json,os,stat,subprocess,sys
from pathlib import Path
s=json.loads(sys.argv[1])
def require(ok):
    if not ok: raise RuntimeError("private_xkb_storage binding refused")
def plain(p):
    require(p.is_absolute() and str(p)==str(p.resolve()))
    for q in (p,*p.parents): require(not q.is_symlink())
source=Path(s["source"]); target=Path("/var/lib/xkb")
require(s["schema"]=="bc.cooking.private_xkb_storage.v1" and s["target"]==str(target))
command=s["inner_command"]; number=int(command[1][1:]); authority=source.parent/"Xauthority"
require(1<=number<=59535 and command==["/usr/bin/Xvfb",":"+str(number),"-screen","0","1280x720x24",
    "-nolisten","unix","-listen","tcp","-displayfd","1","-auth",str(authority)])
plain(source); plain(target); plain(authority)
require(authority.is_file() and authority.stat().st_uid==os.getuid() and not authority.stat().st_mode & 0o077)
require(source.name=="runtime-xkb" and source.is_dir() and target.is_dir())
a=source.stat(); b=target.stat()
require(stat.S_IMODE(a.st_mode)==0o700 and a.st_uid==os.getuid()==0)
require([a.st_dev,a.st_ino]==s["source_identity"]["device_inode"])
require([b.st_dev,b.st_ino,b.st_mtime_ns,b.st_ctime_ns]==s["host_target_identity"])
ns=str(Path("/proc/self/ns/mnt").readlink())
require(ns!=s["host_mount_namespace"])
uidmap=Path("/proc/self/uid_map").read_text().split()
gidmap=Path("/proc/self/gid_map").read_text().split()
require(uidmap==["0",str(s["host_uid"]),"1"] and gidmap==["0",str(s["host_gid"]),"1"])
require(set(s["programs"])=={"/usr/bin/unshare","/usr/bin/mount","/usr/bin/python3","/usr/bin/Xvfb"})
for name,row in s["programs"].items():
    p=Path(name); require(str(p.resolve())==row["physical_path"])
    require(hashlib.sha256(p.read_bytes()).hexdigest()==row["sha256"])
subprocess.run(["/usr/bin/mount","--no-mtab","--bind",str(source),str(target)],check=True,timeout=5)
a=source.stat(); b=target.stat()
require((a.st_dev,a.st_ino)==(b.st_dev,b.st_ino) and [a.st_dev,a.st_ino]==s["source_identity"]["device_inode"]
    and stat.S_IMODE(a.st_mode)==0o700 and a.st_uid==os.getuid() and os.access(target,os.W_OK|os.X_OK))
print(json.dumps({"private_xkb_storage":{"source":str(source),"target":str(target),
    "device":a.st_dev,"inode":a.st_ino,"mount_namespace":ns,
    "uid_map":uidmap,"gid_map":gidmap,"physical_HOME_only":True}}),flush=True)
os.execv("/usr/bin/Xvfb",s["inner_command"])
'''


def display_storage_decision() -> dict:
    path = regular(Path("/home/dev/.tmp/cooking-xkb-physical-backing-decision.json"))
    expected = "38abc85ccee7bff15deddc63634ce6226030d4caebe1cf35bf61f5cb529e5bea"
    check(path, expected)
    return {"path": str(path), "sha256": expected}


def display_programs() -> dict:
    """Read/hash only; system Python's explicit symlink resolution is bound, not copied."""
    rows = {}
    for name in ("/usr/bin/unshare", "/usr/bin/mount", "/usr/bin/python3", "/usr/bin/Xvfb"):
        physical = regular(Path(name).resolve())
        if not os.access(physical, os.X_OK) or physical.stat().st_mode & 0o022:
            raise Refused("display program must be executable and not group/world writable")
        rows[name] = {"physical_path": str(physical), "sha256": digest(physical)}
    return rows


def xkb_source_identity(output: Path) -> dict:
    source = output / "runtime-xkb"
    strict(source, exists=False)
    info = source.stat()
    if (not source.is_dir() or stat.S_IMODE(info.st_mode) != 0o700 or info.st_uid != os.getuid()
            or not source.is_relative_to(Path.home()) or not os.access(source, os.W_OK | os.X_OK)):
        raise Refused("private XKB requires the exact new owned mode0700 HOME runtime-xkb directory")
    return {"device_inode": [info.st_dev, info.st_ino], "uid": info.st_uid, "mode": 0o700}


def xkb_host_identity() -> list[int]:
    target = Path("/var/lib/xkb")
    strict(target, exists=False)
    if not target.is_dir():
        raise Refused("existing exact /var/lib/xkb directory required; never create a host target")
    info = target.stat()
    return [info.st_dev, info.st_ino, info.st_mtime_ns, info.st_ctime_ns]


def display_plan(output: Path, number: int | None, *, cookie: str | None = None) -> dict:
    if number is None:
        return {"started": False, "requires_coordinator_display": True}
    if type(number) is not int or not 1 <= number <= 59535:
        raise Refused("display number must provide a valid separate TCP port")
    cookie = secrets.token_hex(16) if cookie is None else cookie
    if type(cookie) is not str or re.fullmatch(r"[0-9a-f]{32}", cookie) is None:
        raise Refused("fresh authority cookie must be exact lowercase128bit hex")
    authority = str(output / "Xauthority")
    inner = ["/usr/bin/Xvfb", f":{number}", "-screen", "0", "1280x720x24",
             "-nolisten", "unix", "-listen", "tcp", "-displayfd", "1", "-auth", authority]
    storage = {"schema": "bc.cooking.private_xkb_storage.v1", "source": str(output / "runtime-xkb"),
               "target": "/var/lib/xkb", "source_identity": xkb_source_identity(output),
               "host_target_identity": xkb_host_identity(), "host_uid": os.getuid(), "host_gid": os.getgid(),
               "host_mount_namespace": str(Path("/proc/self/ns/mnt").readlink()),
               "programs": display_programs(), "inner_command": inner,
               "physical_storage_decision": display_storage_decision()}
    command = ["/usr/bin/unshare", "--user", "--map-root-user", "--mount", "--propagation", "private", "--",
               "/usr/bin/python3", "-I", "-S", "-c", PRIVATE_XKB_BOOTSTRAP,
               json.dumps(storage, sort_keys=True, separators=(",", ":"))]
    return {"started": False, "requires_external_run_coordination": True, "number": number,
            "private_xkb_storage": storage, "inner_command": inner,
            "must_unset_environment": ["LD_PRELOAD", "LD_LIBRARY_PATH", "PYTHONPATH", "PYTHONHOME"],
            "prepare_authority_command": ["xauth", "-f", authority, "add", f"127.0.0.1:{number}",
                                          "MIT-MAGIC-COOKIE-1", cookie],
            "planned_command": command,
            "environment": {"DISPLAY": f"127.0.0.1:{number}", "XAUTHORITY": authority},
            "warning": "PLAN ONLY: coordinator must verify actual private namespace, mapping, displayfd, ownership and cleanup; never acceptance"}


def verify_display(metadata: dict) -> None:
    plan = metadata["display"]
    manifest = regular(Path(metadata["preparation_manifest"]))
    check(manifest, metadata["inputs"][str(manifest)])
    requested = load(manifest).get("display_number")
    if plan == {"started": False, "requires_coordinator_display": True}:
        if requested is not None:
            raise Refused("cannot remove a manifest-requested private display plan")
        return
    if type(requested) is not int or requested != plan.get("number"):
        raise Refused("display number must match original hash-bound preparation manifest")
    try:
        if plan.get("number") is None:
            raise Refused("explicit private display number required")
        expected = display_plan(Path(metadata["planned_cwd"]), plan["number"],
                                cookie=plan["prepare_authority_command"][-1])
        if plan != expected:
            raise Refused("private display command/program/source/target/metadata binding changed")
        for row in plan["private_xkb_storage"]["programs"].values():
            if metadata["inputs"].get(row["physical_path"]) != row["sha256"]:
                raise Refused("display program missing original input hash binding")
    except (KeyError, TypeError, IndexError) as error:
        raise Refused("invalid private display metadata") from error


def extract_natives(jars: list[Path], output: Path) -> None:
    output.mkdir()
    seen = set()
    for path in jars:
        with zipfile.ZipFile(path) as archive:
            for entry in archive.infolist():
                p = PurePosixPath(entry.filename)
                if p.is_absolute() or ".." in p.parts or (entry.external_attr >> 16) & 0o170000 == 0o120000:
                    raise Refused("unsafe native archive entry")
                if not entry.filename.endswith(".so"):
                    continue
                if p.name in seen or entry.file_size > 64 * 1024 * 1024:
                    raise Refused("duplicate/oversized native library")
                seen.add(p.name)
                with (output / p.name).open("xb") as stream:
                    stream.write(archive.read(entry))


def prepare(manifest_path: Path, output: Path, *, source_root: Path = SOURCE_ROOT,
            lock_path: Path | None = None, baseline: Path = BASE_CLIENT,
            cache_root: Path = CACHE_ROOT) -> dict:
    regular(manifest_path); strict(output, exists=False); strict(source_root, exists=False)
    if not output.is_relative_to(source_root) or output == source_root or output.exists():
        raise Refused("output must be a NEW source-owned project child")
    lock_path = lock_path or Path(os.environ.get("BC_PACK_TEST_STATE_ROOT", str(Path.home() / ".local/share/worklane/pack-tests"))) / "runner.lock"
    with runner_guard(lock_path):
        manifest_sha = digest(manifest_path)
        m = load(manifest_path)
        if m["schema"] != SCHEMA:
            raise Refused("unsupported client manifest schema")
        base, cache = Path(m["base_client"]), Path(m["launcher_cache"])
        if base != baseline or cache != cache_root:
            raise Refused("only the explicitly approved Build10 baseline and canonical launcher cache are supported")
        for path in [base, cache, manifest_path]:
            strict(path, exists=False)
            if output.is_relative_to(path) or path.is_relative_to(output):
                raise Refused("input/output overlap")
        port = m["server_port"]
        if type(port) is not int or not 1 <= port <= 65535:
            raise Refused("explicit loopback server port required")
        java = regular(Path(m["java"]))
        check(java, m["java_sha256"])
        release = regular(java.parent.parent / "release")
        check(release, m["java_release_sha256"])
        if not re.search(r'(?m)^JAVA_VERSION="17\.', release.read_text()):
            raise Refused("explicit Java17 JDK release required")
        base_files = bound_files(base, m["base_files"], TREES)
        cache_files = bound_files(cache, m["cache_files"], CACHE_TREES)
        inputs = base_files.copy()
        overlaid = set()
        for row in m["overlays"]:
            dest = rel(row["destination"], TREES)
            if not dest.startswith("mods/") or dest.count("/") != 1 or not dest.endswith(".jar") or dest in overlaid:
                raise Refused("only distinct explicit top-level mod JAR overlays allowed")
            overlaid.add(dest)
            if dest in inputs and row.get("replaces_sha256") != inputs[dest][1]:
                raise Refused("overlay must bind exact replaced client original")
            source = regular(Path(row["source"])); expected = checked_hash(row["sha256"])
            check(source, expected); inputs[dest] = source, expected
        roles = m["roles"]
        if set(roles) != set(ROLE_IDS) or len(set(roles.values())) != 9 or overlaid != set(roles.values()):
            raise Refused("exactly nine distinct server-matched explicit role overlays required (Bumblezone mandatory)")
        if roles["bumblezone"] != BUMBLEZONE_JAR:
            raise Refused("Bumblezone role requires the explicit better-bumblezone-crops-0.1.0.jar destination")
        server_path = regular(Path(m["server_metadata"]))
        check(server_path, m["server_metadata_sha256"])
        server = load(server_path)
        if server.get("schema") != "bc.cooking.native_fixture.v1" or server.get("status") != "PREPARED_NOT_RUN" or server.get("launch_target") != "forgeserver" or server.get("port") != port:
            raise Refused("prepared native forgeserver metadata/port required")
        discovery_only = matched_discovery_mode(m.get("discovery_only", False), server)
        fixture_classification = m.get("fixture_namespace_classification", False)
        if type(fixture_classification) is not bool or (fixture_classification and not discovery_only):
            raise Refused("fixture_namespace_classification must be boolean and requires discovery_only=true")
        frozen_manifest = regular(Path(server["planned_cwd"]) / "fixture-input-manifest.json")
        check(frozen_manifest, server["input_manifest_sha256"])
        if load(frozen_manifest)["roles"] != roles:
            raise Refused("client/server runtime role destinations differ")
        ids = {}
        for dest, (path, expected) in inputs.items():
            if dest.startswith("mods/") and dest.endswith(".jar"):
                for mod in mod_ids(path):
                    if mod in ids:
                        raise Refused(f"duplicate client mod ID: {mod}")
                    ids[mod] = dest
        for role, mod_id in ROLE_IDS.items():
            dest = roles[role]
            if dest not in inputs or ids.get(mod_id) != dest or server["inputs"][dest]["sha256"] != inputs[dest][1] or server["files"][dest] != inputs[dest][1]:
                raise Refused(f"client/server role hash/ID mismatch: {role}")
            check(regular(Path(server["planned_cwd"]) / dest), inputs[dest][1])
        if fixture_classification:
            inputs[FIXTURE_POLICY_DEST] = fixture_namespace_input(base_files, server)
        identity = fresh_identity()
        command, native_jars = launcher(cache, cache_files, output, port, identity, java, discovery_only)
        display_number = m.get("display_number")
        if display_number is not None and (type(display_number) is not int or not 1 <= display_number <= 59535):
            raise Refused("invalid planned display number")
        bound = {**{str(p): h for p, h in base_files.values()}, **{str(p): h for p, h in cache_files.values()},
                 **{str(p): h for p, h in inputs.values()}, str(server_path): m["server_metadata_sha256"],
                 str(frozen_manifest): server["input_manifest_sha256"], str(java): m["java_sha256"],
                 str(release): m["java_release_sha256"], str(manifest_path): manifest_sha}
        for role in roles.values():
            bound[str(Path(server["planned_cwd"]) / role)] = inputs[role][1]
        output.mkdir(parents=True, exist_ok=False)
        try:
            for dest, (path, expected) in sorted(inputs.items()):
                check(regular(path), expected)
                target = output / dest
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(path, target)
                check(target, expected)
                if target.stat().st_ino == path.stat().st_ino and target.stat().st_dev == path.stat().st_dev:
                    raise Refused("copy unexpectedly shares source inode")
            extract_natives(native_jars, output / "natives")
            (output / "tmp").mkdir()
            (output / "home").mkdir()
            if display_number is not None:
                (output / "runtime-xkb").mkdir(mode=0o700)
            display = display_plan(output, display_number)
            if display_number is not None:
                for row in display["private_xkb_storage"]["programs"].values():
                    bound[row["physical_path"]] = row["sha256"]
                decision = display["private_xkb_storage"]["physical_storage_decision"]
                bound[decision["path"]] = decision["sha256"]
            (output / "options.txt").write_text("renderDistance:4\nsimulationDistance:5\nmaxFps:30\ngraphicsMode:0\nao:false\nentityShadows:false\n")
            for path, expected in bound.items():
                check(regular(Path(path)), expected)
            if inventory(base, TREES) != set(base_files) or inventory(cache, CACHE_TREES) != set(cache_files):
                raise Refused("input inventory changed during preparation")
            metadata = {"schema": SCHEMA, "status": "PREPARED_NOT_RUN", "acceptance": False,
                        "requires_external_run_coordination": True, "identity": identity,
                        "server_metadata": str(server_path), "server_metadata_sha256": digest(server_path),
                        "roles": roles, "inputs": bound, "preparation_manifest": str(manifest_path),
                        "planned_command": command,
                        "planned_cwd": str(output), "server_port": port, "launch_target": "forgeclient",
                        "discovery_only": discovery_only,
                        "fixture_namespace_classification": fixture_classification,
                        "planned_environment": {"LIBGL_ALWAYS_SOFTWARE": "1", "MESA_GL_VERSION_OVERRIDE": "4.6",
                            "MESA_GLSL_VERSION_OVERRIDE": "460", "ALSOFT_DRIVERS": "null",
                            "HOME": str(output / "home"), "XDG_CACHE_HOME": str(output / "home/.cache"),
                            "XDG_CONFIG_HOME": str(output / "home/.config"), "XDG_DATA_HOME": str(output / "home/.local/share")},
                        "must_unset_environment": ["JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH", "LD_PRELOAD"],
                        "display": display, "input_hashes_verified_before_and_after": True,
                        "input_inventories": [{"root": str(base), "trees": sorted(TREES), "files": sorted(base_files)},
                                              {"root": str(cache), "trees": sorted(CACHE_TREES), "files": sorted(cache_files)}],
                        "immutable_outputs": {str(output / dest): expected for dest, (_, expected) in inputs.items() if dest.startswith("mods/") or (fixture_classification and dest == FIXTURE_POLICY_DEST)},
                        "worlds_copied": False, "old_options_copied": False, "credentials_copied": False,
                        "cache_mode": "existing explicitly hashed read-only cache, no links/copies/install/download",
                        "files": {p.relative_to(output).as_posix(): digest(p) for p in sorted(output.rglob("*")) if p.is_file()}}
            (output / "client-metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
            return metadata
        except BaseException as failure:
            (output / "PREPARATION_FAILED.json").write_text(json.dumps({"status": "PREPARATION_FAILED", "error": str(failure)}) + "\n")
            raise


def verify_inputs(metadata_path: Path) -> None:
    """Coordinator calls this before AND after actual launch, including failure cleanup."""
    metadata = load(metadata_path)
    if metadata.get("schema") != SCHEMA:
        raise Refused("not client metadata")
    for path, expected in {**metadata["inputs"], **metadata["immutable_outputs"]}.items():
        check(regular(Path(path)), expected)
    for entry in metadata["input_inventories"]:
        if inventory(Path(entry["root"]), set(entry["trees"])) != set(entry["files"]):
            raise Refused("read-only input inventory changed after preparation")
    server_path = regular(Path(metadata["server_metadata"]))
    if metadata["inputs"].get(str(server_path)) != metadata["server_metadata_sha256"]:
        raise Refused("receipted discovery mode requires its original hash-bound server metadata")
    check(server_path, metadata["server_metadata_sha256"])
    mode = matched_discovery_mode(metadata.get("discovery_only", False), load(server_path))
    verify_discovery_command(metadata["planned_command"], mode)
    classification = metadata.get("fixture_namespace_classification", False)
    if type(classification) is not bool or (classification and not mode):
        raise Refused("invalid receipted fixture namespace classification mode")
    dest = str(Path(metadata["planned_cwd"]) / FIXTURE_POLICY_DEST)
    if classification != (metadata["immutable_outputs"].get(dest) == FIXTURE_POLICY_DERIVED_SHA):
        raise Refused("fixture policy output must match its receipted immutable classification mode")
    if Path(metadata["planned_cwd"]) != metadata_path.parent:
        raise Refused("client metadata must remain beside its exact prepared output")
    verify_display(metadata)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-manifest", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--verify-inputs", type=Path)
    args = parser.parse_args()
    if args.verify_inputs:
        if args.input_manifest or args.output:
            parser.error("verification is separate from preparation")
        verify_inputs(args.verify_inputs)
    else:
        if not args.input_manifest or not args.output:
            parser.error("--input-manifest and --output required")
        metadata = prepare(args.input_manifest, args.output)
        print(json.dumps({"status": metadata["status"], "metadata": str(args.output / "client-metadata.json")}, indent=2))


if __name__ == "__main__":
    main()
