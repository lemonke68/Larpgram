#!/usr/bin/env python3
"""Build the pinned Larpgram SDK without resetting existing source checkouts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import struct
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
LOCK = json.loads((HERE / "source-lock.json").read_text())
ABI_TARGETS = {
    "aarch64-linux-android": ("arm64-v8a", 2, 183),
    "armv7-linux-androideabi": ("armeabi-v7a", 1, 40),
    "x86_64-linux-android": ("x86_64", 2, 62),
    "i686-linux-android": ("x86", 1, 3),
}


def verify_aar(path, targets, profile):
    """Reject stale provenance, partial SDKs and native libraries under the wrong ABI."""
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise SystemExit("SDK AAR contains duplicate entries")
        metadata = json.loads(archive.read("assets/larpgram-sdk-build.json"))
        for key, expected in LOCK.items():
            if metadata.get(key) != expected:
                raise SystemExit(f"SDK AAR provenance mismatch: {key}")
        expected_targets = "all" if len(targets) == 4 else targets
        if metadata.get("profile") != profile or metadata.get("targets") != expected_targets:
            raise SystemExit("SDK AAR profile/targets do not match the requested build")
        expected_abis = {ABI_TARGETS[target][0] for target in targets}
        present = {name.split("/")[1] for name in names
                   if name.startswith("jni/") and name.endswith("/libmatrix_sdk_ffi.so")}
        if present != expected_abis:
            raise SystemExit(f"SDK AAR ABI mismatch: expected {expected_abis}, got {present}")
        for target in targets:
            abi, elf_class, machine = ABI_TARGETS[target]
            with archive.open(f"jni/{abi}/libmatrix_sdk_ffi.so") as native:
                header = native.read(20)
            if (header[:4] != b"\x7fELF" or header[4:6] != bytes([elf_class, 1])
                    or len(header) < 20 or struct.unpack_from("<H", header, 18)[0] != machine):
                raise SystemExit(f"SDK AAR contains an invalid ELF for {abi}")
        if "classes.jar" not in names:
            raise SystemExit("SDK AAR has no Kotlin/JVM bindings")
    print(f"SDK AAR verified: {profile}, {', '.join(sorted(expected_abis))}")


def run(*args, cwd=None, env=None):
    subprocess.run(list(map(str, args)), cwd=cwd, env=env, check=True)


def output(*args, cwd=None):
    return subprocess.check_output(list(map(str, args)), cwd=cwd, text=True).strip()


def checkout(path, repository, revision):
    if not path.exists():
        run("git", "init", path)
        run("git", "remote", "add", "origin", repository, cwd=path)
        run("git", "fetch", "--depth", "1", "origin", revision, cwd=path)
        run("git", "checkout", "--detach", "FETCH_HEAD", cwd=path)
    if output("git", "rev-parse", "HEAD", cwd=path) != revision:
        raise SystemExit(f"Wrong revision in {path}; existing checkout was left untouched")


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument("--sdk", type=Path, default=ROOT.parent / "matrix-rust-sdk")
    parser.add_argument("--components", type=Path, default=ROOT.parent / "matrix-rust-components-kotlin")
    parser.add_argument("--target", choices=ABI_TARGETS, default="aarch64-linux-android")
    parser.add_argument("--all-abis", action="store_true")
    parser.add_argument("--release", action="store_true")
    parser.add_argument("--verify-only", action="store_true")
    parser.add_argument("--verify-aar", type=Path, help="Verify an existing AAR without fetching/building sources")
    args = parser.parse_args()
    targets = list(ABI_TARGETS) if args.all_abis else [args.target]
    profile = "small-release-stripped" if args.release else "reldev"
    if args.verify_aar:
        verify_aar(args.verify_aar, targets, profile)
        return
    args.sdk = args.sdk.resolve()
    args.components = args.components.resolve()
    patch = HERE / "media-cache.patch"
    if hashlib.sha256(patch.read_bytes()).hexdigest() != LOCK["patch_sha256"]:
        raise SystemExit("SDK patch hash does not match source-lock.json")
    checkout(args.sdk, "https://github.com/matrix-org/matrix-rust-sdk.git", LOCK["sdk_revision"])
    checkout(args.components, "https://github.com/matrix-org/matrix-rust-components-kotlin.git", LOCK["components_revision"])
    expected_bindings = json.loads((HERE / "generated-bindings.json").read_text())
    for name in output("git", "diff", "--name-only", "HEAD", cwd=args.components).splitlines():
        expected = expected_bindings.get(name)
        if expected is None or hashlib.sha256((args.components / name).read_bytes()).hexdigest() != expected:
            raise SystemExit(f"Unexpected Kotlin component edits in {name}; nothing was overwritten")
    for name in output("git", "ls-files", "--others", "--exclude-standard", "sdk/", cwd=args.components).splitlines():
        if "/src/" in name and "/jniLibs/" not in name:
            raise SystemExit(f"Unexpected component source file: {name}; nothing was overwritten")
    dirty = output("git", "status", "--porcelain", cwd=args.sdk)
    if not dirty:
        run("git", "apply", "--check", patch, cwd=args.sdk)
        run("git", "apply", patch, cwd=args.sdk)
    changed = set(output("git", "diff", "--name-only", "HEAD", cwd=args.sdk).splitlines())
    changed.update(output("git", "ls-files", "--others", "--exclude-standard", cwd=args.sdk).splitlines())
    if changed != set(LOCK["patched_files"]):
        raise SystemExit("SDK contains unexpected source changes; nothing was reset")
    for name, expected in LOCK["patched_files"].items():
        if hashlib.sha256((args.sdk / name).read_bytes()).hexdigest() != expected:
            raise SystemExit(f"Unexpected contents in {name}; nothing was reset")
    if args.verify_only:
        print("Pinned sources and patch verified")
        return
    if output("rustc", "--version").split()[1] != LOCK["rust_version"]:
        raise SystemExit(f"Build needs Rust {LOCK['rust_version']}")
    if output("cargo", "ndk", "--version").split()[-1] != LOCK["cargo_ndk_version"]:
        raise SystemExit(f"Build needs cargo-ndk {LOCK['cargo_ndk_version']}")
    sdk_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk_home:
        raise SystemExit("Set ANDROID_HOME to the installed Android SDK")
    env = dict(os.environ, ANDROID_NDK_HOME=str(Path(sdk_home) / "ndk" / LOCK["ndk_version"]),
               NDK_VERSION=LOCK["ndk_version"], CARGO_BUILD_JOBS="3")
    if not Path(env["ANDROID_NDK_HOME"]).is_dir():
        raise SystemExit(f"Required NDK is missing: {env['ANDROID_NDK_HOME']}")
    command = ["cargo", "xtask", "kotlin", "build-android-library", "--profile", profile,
               "--src-dir", str(args.components / "sdk/sdk-android/src/main"), "--package", "full-sdk"]
    if not args.all_abis:
        command += ["--only-target", args.target]
    run(*command, cwd=args.sdk, env=env)
    for name, expected in expected_bindings.items():
        if hashlib.sha256((args.components / name).read_bytes()).hexdigest() != expected:
            raise SystemExit(f"Generated bindings changed unexpectedly: {name}; AAR was not replaced")
    # One Gradle invocation at a time; the app build is a separate subsequent step.
    variant = "Release" if args.release else "Debug"
    run(args.components / "gradlew", f":sdk:sdk-android:assemble{variant}", "--max-workers=3",
        "--no-daemon", cwd=args.components, env=env)
    built = args.components / f"sdk/sdk-android/build/outputs/aar/sdk-android-{variant.lower()}.aar"
    destination = ROOT / "libraries/rustsdk/matrix-rust-sdk.aar"
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Use a staging file, so a failed build cannot replace a working AAR.
    staging = destination.with_suffix(".aar.tmp")
    expected_abis = {ABI_TARGETS[target][0] for target in targets}
    with zipfile.ZipFile(built) as source, zipfile.ZipFile(staging, "w") as target:
        for entry in source.infolist():
            if entry.filename.startswith("jni/") and entry.filename.split("/")[1] not in expected_abis:
                continue
            target.writestr(entry, source.read(entry))
        present = {name.split("/")[1] for name in target.namelist()
                   if name.startswith("jni/") and name.endswith("/libmatrix_sdk_ffi.so")}
        if present != expected_abis:
            raise SystemExit(f"AAR ABI mismatch: expected {expected_abis}, got {present}")
    metadata = dict(LOCK, profile=profile, targets="all" if args.all_abis else [args.target])
    with zipfile.ZipFile(staging, "a") as archive:
        archive.writestr("assets/larpgram-sdk-build.json", json.dumps(metadata, indent=2))
    verify_aar(staging, targets, profile)
    staging.replace(destination)
    print(f"SDK ready: {destination}")


if __name__ == "__main__":
    main()
