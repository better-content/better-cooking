#!/usr/bin/env python3
"""Prepare an explicit frozen private server fixture. Never launches Minecraft or Gradle."""
from __future__ import annotations
import argparse
import contextlib
import fcntl
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import secrets
import shutil
import socket
import tomllib
import zipfile

SCHEMA = "bc.cooking.native_fixture.v1"
SOURCE_ROOT = Path(__file__).absolute().parents[1]
# These public bootstrap trees affect registries before datapack/network synchronization.
# Omitting them produced R11d's Excavated Variants handshake failure.
TREES = {"libraries", "resources", "defaultresources", "globalresources",
         "config", "defaultconfigs", "kubejs", "mods"}
LAUNCHERS = {"run.sh", "run-forge.sh", "better-world-management-server.sh"}
ROLE_IDS = {"cooking_main": "better_cooking", "native_fixture": "better_cooking_fixture",
            "physiology": "better_survival_physiology", "water": "better_drinking_water",
            "heat": "better_industrial_heat", "spice": "better_spicy_farmers",
            "kaleidoscope": "kaleidoscope_cookery", "commerce": "better_spirit_commerce",
            "bumblezone": "better_bumblezone_crops"}
ROLES = set(ROLE_IDS)
FORBIDDEN = {"world", "worlds", "saves", "logs", "crash-reports", "playerdata", "advancements", "stats",
             "usercache.json", "usernamecache.json", "ops.json", "whitelist.json", "banned-players.json",
             "banned-ips.json", "run.json", "launcher_accounts.json", "launcher_profiles.json",
             "accounts.json", "credentials.json", "access_tokens.json", "session.lock"}
# Advancement datapacks under kubejs are legitimate; only player progress is forbidden there.

class Refused(ValueError):
    pass

def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise Refused(f"duplicate JSON key: {key}")
        result[key] = value
    return result

def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def strict(path: Path, *, exists: bool = True) -> Path:
    if not path.is_absolute() or ".." in path.parts:
        raise Refused(f"absolute, nonescaping path required: {path}")
    for part in (path, *path.parents):
        if part.is_symlink():
            raise Refused(f"symlink input/output forbidden: {part}")
    if exists and not path.is_file():
        raise Refused(f"missing regular input: {path}")
    return path

def relative(value: str) -> str:
    if not isinstance(value, str) or "\\" in value:
        raise Refused("invalid relative path")
    p = PurePosixPath(value)
    if p.is_absolute() or not p.parts or any(x in {"..", "."} for x in value.split("/")):
        raise Refused(f"escaping/noncanonical destination: {value}")
    if str(p) != value or not (p.parts[0] in TREES or value in LAUNCHERS):
        raise Refused(f"outside runtime whitelist: {value}")
    forbidden = FORBIDDEN - {"advancements", "stats"} if p.parts[0] in {"kubejs", "resources"} else FORBIDDEN
    if any(x.lower() in forbidden or re.search(r"(?i)(credential|access.?token|launcher.?account|\.pem$|\.key$)", x) for x in p.parts):
        raise Refused(f"world/evidence/credential path forbidden: {value}")
    return value

def checked_hash(value: str) -> str:
    if not isinstance(value, str) or not re.fullmatch(r"[0-9a-f]{64}", value):
        raise Refused("every input needs an exact lowercase SHA-256")
    return value

def check(path: Path, expected: str) -> None:
    strict(path)
    if digest(path) != checked_hash(expected):
        raise Refused(f"input hash changed: {path}")

@contextlib.contextmanager
def runner_guard(lock: Path):
    strict(lock, exists=False)
    lock.parent.mkdir(parents=True, exist_ok=True)
    # FileChannel.tryLock uses POSIX record locks, not BSD flock locks.
    with lock.open("a+b") as stream:
        try:
            fcntl.lockf(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as failure:
            raise Refused("active pack runner lock; preparation refused") from failure
        try:
            yield
        finally:
            fcntl.lockf(stream, fcntl.LOCK_UN)

def base_inventory(base: Path) -> set[str]:
    found = set()
    for name in sorted(TREES | LAUNCHERS):
        root = base / name
        if root.is_symlink():
            raise Refused(f"symlink base tree: {root}")
        if root.is_file():
            found.add(relative(name))
        elif root.is_dir():
            for directory, dirs, files in os.walk(root, followlinks=False):
                for child in dirs + files:
                    p = Path(directory) / child
                    if p.is_symlink():
                        raise Refused(f"symlink base descendant: {p}")
                for filename in files:
                    found.add(relative((Path(directory) / filename).relative_to(base).as_posix()))
    return found

def mod_ids(path: Path) -> set[str]:
    try:
        with zipfile.ZipFile(path) as jar:
            ids = []
            if "META-INF/mods.toml" in jar.namelist():
                data = tomllib.loads(jar.read("META-INF/mods.toml").decode())
                ids.extend(mod["modId"] for mod in data.get("mods", []))
            elif "fabric.mod.json" in jar.namelist():
                ids.append(json.loads(jar.read("fabric.mod.json"))["id"])
            if len(ids) != len(set(ids)) or any(not re.fullmatch(r"[a-z][a-z0-9_]*", i) for i in ids):
                raise Refused(f"duplicate/invalid IDs within mod: {path}")
            return set(ids)
    except (zipfile.BadZipFile, KeyError, UnicodeError, tomllib.TOMLDecodeError) as failure:
        raise Refused(f"cannot inspect mod metadata: {path}") from failure

def plan_inputs(base: Path, manifest: dict) -> dict[str, tuple[Path, str]]:
    if manifest.get("schema") != SCHEMA or manifest.get("base_server") != str(base):
        raise Refused("schema/base_server mismatch")
    result = {}
    for entry in manifest["base_files"]:
        dest = relative(entry["path"])
        if dest in result:
            raise Refused(f"duplicate base destination: {dest}")
        result[dest] = (base / dest, checked_hash(entry["sha256"]))
    if set(result) != base_inventory(base):
        raise Refused("base_files must exactly inventory every whitelisted base file")
    overlays = set()
    for entry in manifest["overlays"]:
        dest = relative(entry["destination"])
        if dest in overlays:
            raise Refused(f"duplicate overlay destination: {dest}")
        overlays.add(dest)
        if dest in result and entry.get("replaces_sha256") != result[dest][1]:
            raise Refused(f"replacement lacks exact original hash: {dest}")
        source = strict(Path(entry["source"]))
        result[dest] = (source, checked_hash(entry["sha256"]))
    roles = manifest["roles"]
    if set(roles) != ROLES or len(set(roles.values())) != len(ROLES):
        raise Refused("nine distinct explicit runtime roles required")
    for destination in roles.values():
        if destination not in result or not destination.startswith("mods/") or not destination.endswith(".jar"):
            raise Refused(f"missing role JAR: {destination}")
    args = relative(manifest["forge_args"])
    if not args.startswith("libraries/") or not args.endswith("/unix_args.txt") or args not in result:
        raise Refused("explicit local Forge unix_args.txt required")
    ids, names = {}, set()
    for dest, (source, expected) in result.items():
        check(source, expected)
        if dest.startswith("mods/") and dest.endswith(".jar"):
            name = Path(dest).name.lower()
            if name in names:
                raise Refused(f"duplicate mod name: {name}")
            names.add(name)
            for mod in mod_ids(source):
                if mod in ids:
                    raise Refused(f"duplicate mod ID {mod}: {ids[mod]} and {dest}")
                ids[mod] = dest
    for role, expected_id in ROLE_IDS.items():
        if ids.get(expected_id) != roles[role]:
            raise Refused(f"role {role} must contain actual mod ID {expected_id}")
    return result

def fixture_jvm_args(output: Path, extra: tuple[str, ...]) -> list[str]:
    """Explicit observation/GameTest properties, before the JVM main class; never shell fragments."""
    allowed = {"better_cooking.discoveryOnly", "better_cooking.nativeCatalogueOutput", "fml.disableVersionCheck",
               "forge.enableGameTest", "forge.gameTestServer", "forge.enabledGameTestNamespaces",
               "java.io.tmpdir", "user.home"}
    paths = {"better_cooking.nativeCatalogueOutput", "java.io.tmpdir", "user.home"}
    if any(c.isspace() or c in "\x00\"'\\#" for c in str(output)):
        raise Refused("native fixture output cannot require JVM argfile quoting")
    properties = {"java.io.tmpdir": str(output / "runtime-tmp")}
    seen = set()
    if len(extra) > 32:
        raise Refused("too many native JVM properties")
    for arg in extra:
        if not isinstance(arg, str) or len(arg) > 4096 or any(c.isspace() or c in "\x00\"'\\#" for c in arg) or not arg.startswith("-D") or "=" not in arg:
            raise Refused("native JVM properties must be bounded single -Dkey=value arguments")
        key, value = arg[2:].split("=", 1)
        if key not in allowed or key in seen or not value:
            raise Refused("unsupported/duplicate native JVM property")
        seen.add(key)
        if key in paths:
            path = Path(value)
            if not path.is_absolute() or ".." in path.parts or not path.is_relative_to(output.parent) or path.exists() or path.is_symlink():
                raise Refused("native JVM paths must be NEW paths in this fresh source-owned fixture")
        elif key == "forge.enabledGameTestNamespaces":
            if not re.fullmatch(r"[a-z0-9_.-]+(?:,[a-z0-9_.-]+)*", value):
                raise Refused("invalid finite GameTest namespace list")
        elif value not in {"true", "false"}:
            raise Refused("native JVM switch must be boolean")
        properties[key] = value
    return ["-Xms1G", "-Xmx6G", "-XX:ActiveProcessorCount=2"] + ["-D" + key + "=" + value for key, value in properties.items()]


def prepare(base: Path, output: Path, manifest_path: Path, target: str = "forgeserver",
            *, source_root: Path = SOURCE_ROOT, lock_path: Path | None = None, java: str = "java",
            jvm_args: tuple[str, ...] = ()) -> dict:
    strict(base, exists=False); strict(output, exists=False); strict(manifest_path)
    if not base.is_dir() or output.exists() or not output.is_relative_to(source_root) or output == source_root:
        raise Refused("output must be a NEW child of the source-owned project, base must exist")
    if output.is_relative_to(base) or base.is_relative_to(output):
        raise Refused("input/output overlap")
    if target not in {"forgeserver", "forgegametestserver"}:
        raise Refused("unsupported launch target")
    java_arguments = fixture_jvm_args(output, jvm_args)
    lock_path = lock_path or Path(os.environ.get("BC_PACK_TEST_STATE_ROOT", str(Path.home() / ".local/share/worklane/pack-tests"))) / "runner.lock"
    with runner_guard(lock_path):
        manifest_hash = digest(manifest_path)
        manifest_bytes = manifest_path.read_bytes()
        manifest = json.loads(manifest_bytes, object_pairs_hook=unique_object)
        # Validate even replaced originals; baseline mutation must never be hidden by overlays.
        for entry in manifest.get("base_files", []):
            check(base / relative(entry["path"]), entry["sha256"])
        inputs = plan_inputs(base, manifest)
        if not LAUNCHERS.issubset(inputs):
            raise Refused("The full production run.sh/lifecycle-supervisor/run-forge.sh chain is required")
        original_args = inputs[manifest["forge_args"]][0].read_text()
        if len(re.findall(r"--launchTarget\s+forgeserver\b", original_args)) != 1:
            raise Refused("expected exactly one native forgeserver target in frozen Forge arguments")
        if re.search(r"(?:^|[=\s:])/|\.\./|@[\w/]", original_args):
            raise Refused("Forge args contain external/escaping/indirect runtime paths")
        for reference in re.findall(r"libraries/[A-Za-z0-9_./+\-]+", original_args):
            if reference not in inputs:
                raise Refused(f"Forge bootstrap dependency missing from frozen inventory: {reference}")
        output.mkdir(parents=True, exist_ok=False)
        try:
            for dest, (source, expected) in sorted(inputs.items()):
                check(source, expected)
                destination = output / dest
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, destination)  # independent bytes; never hardlinks
                if dest in LAUNCHERS:
                    destination.chmod(0o755)  # The production chain execs its child launchers.
                if digest(destination) != expected:
                    raise Refused(f"copy digest mismatch: {dest}")
                check(source, expected)
            generated_args = output / "fixture-forge-args.txt"
            generated_args.write_text(re.sub(r"--launchTarget\s+forgeserver\b", "--launchTarget " + target, original_args))
            # Preserve run.sh -> lifecycle supervisor -> run-forge.sh unchanged. The wrapper
            # reads the standard Forge argfile, not our diagnostic alias. Only an explicitly
            # requested GameTest target derives that copied argfile; original bytes stay pinned.
            if target != "forgeserver":
                (output / manifest["forge_args"]).write_bytes(generated_args.read_bytes())
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", 0)); port = probe.getsockname()[1]
            seed = secrets.randbits(63)
            (output / "server.properties").write_text(f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nlevel-name=world\nlevel-seed={seed}\nview-distance=4\nsimulation-distance=4\nenable-rcon=false\nenable-query=false\n")
            (output / "eula.txt").write_text("eula=true\n")
            (output / "user_jvm_args.txt").write_text("\n".join(java_arguments) + "\n")
            for source, expected in inputs.values():
                check(source, expected)
            for entry in manifest["base_files"]:
                check(base / entry["path"], entry["sha256"])
            check(manifest_path, manifest_hash)
            if base_inventory(base) != {e["path"] for e in manifest["base_files"]}:
                raise Refused("base inventory changed during copy")
            (output / "fixture-input-manifest.json").write_bytes(manifest_bytes)
            files = {p.relative_to(output).as_posix(): digest(p) for p in sorted(output.rglob("*")) if p.is_file()}
            metadata = {"schema": SCHEMA, "status": "PREPARED_NOT_RUN", "base_server": str(base),
                        "input_manifest": str(manifest_path), "input_manifest_sha256": manifest_hash,
                        "inputs": {dest: {"source": str(source), "sha256": expected, "runtime_sha256": files[dest]} for dest, (source, expected) in sorted(inputs.items())},
                        "files": files, "seed": seed, "port": port, "launch_target": target,
                        "java_executable": java, "jvm_arguments": java_arguments,
                        "planned_command": ["./run.sh", "nogui"],
                        "planned_environment": {"BC_JAVA": java},
                        "launch_contract": "production_run_sh_lifecycle_supervisor",
                        "lineage_state_copied_or_manually_initialized": False,
                        "launcher_modes": {name: oct((output / name).stat().st_mode & 0o777) for name in sorted(LAUNCHERS)},
                        "forge_argument_derivation": {"path": manifest["forge_args"], "source_sha256": inputs[manifest["forge_args"]][1], "runtime_sha256": files[manifest["forge_args"]], "target": target},
                        "planned_cwd": str(output), "requires_external_run_coordination": True,
                        "acceptance": False, "worlds_copied": False, "credentials_copied": False}
            (output / "fixture-metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
            return metadata
        except BaseException as failure:
            (output / "PREPARATION_FAILED.json").write_text(json.dumps({"status": "PREPARATION_FAILED", "error": str(failure)}) + "\n")
            raise

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-server", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--input-manifest", type=Path, required=True)
    parser.add_argument("--launch-target", choices=["forgeserver", "forgegametestserver"], default="forgeserver")
    parser.add_argument("--java", default="java", help="BC_JAVA binding for the production supervisor; never executed during preparation")
    parser.add_argument("--jvm-arg", action="append", default=[], help="bounded allowed -Dkey=value, use --jvm-arg=-Dkey=value")
    args = parser.parse_args()
    try:
        result = prepare(args.base_server, args.output, args.input_manifest, args.launch_target, java=args.java, jvm_args=tuple(args.jvm_arg))
    except (Refused, KeyError, OSError, json.JSONDecodeError) as failure:
        parser.exit(1, f"fixture preparation refused: {failure}\n")
    print(json.dumps({"status": result["status"], "output": str(args.output)}))

if __name__ == "__main__":
    main()
