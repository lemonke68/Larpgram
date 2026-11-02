# Larpgram media-cache SDK

The published `sdk-android:26.10.23` was built from Rust SDK
`8de215a0aa0026afa5754ae5f05a6d168d1a1d60`, with Kotlin components
`917946f48c904750a6ab4b2a9d66f94513db0c99` (`sdk-v26.10.23`).
Provenance: https://github.com/matrix-org/matrix-rust-components-kotlin/releases/tag/sdk-v26.10.23

`source-lock.json` pins these revisions, Rust 1.99.0, cargo-ndk 4.1.2, NDK
28.2.13676358, the patch and every patched source file. `build.py` never resets
an existing checkout. Unexpected revisions or edits cause an error.

Set `JAVA_HOME` to JDK 21, `ANDROID_HOME` to the installed Android SDK, and put
`cargo`, `rustc` and `cargo-ndk` on PATH. Install the Rust Android targets for the
ABIs you build. Run one Gradle build at a time on the 16 GB Mac.

For a new machine, install the pinned versions explicitly, for example:

```sh
rustup toolchain install 1.99.0 --profile minimal
rustup target add --toolchain 1.99.0 aarch64-linux-android
cargo +1.99.0 install cargo-ndk --version 4.1.2 --locked
export RUSTUP_TOOLCHAIN=1.99.0
```

Install NDK `28.2.13676358` with Android SDK Manager. For `--all-abis`, also
install Rust targets `armv7-linux-androideabi`, `i686-linux-android` and
`x86_64-linux-android` for the same toolchain.

```sh
# Verify the pinned source and patch without building.
python3 tools/larpgram/rust-sdk/build.py --verify-only

# Optimized development SDK for the connected arm64 Honor.
python3 tools/larpgram/rust-sdk/build.py

# Future release: build all supported ABIs, without publishing anything.
python3 tools/larpgram/rust-sdk/build.py --release --all-abis

# Verify the actual AAR's provenance metadata, build profile and native ELF ABIs.
python3 tools/larpgram/rust-sdk/build.py --verify-aar \
  libraries/rustsdk/matrix-rust-sdk.aar --release --all-abis
```

The result is `libraries/rustsdk/matrix-rust-sdk.aar`, shared by debug and
release. Its build metadata is embedded at `assets/larpgram-sdk-build.json`.
The app creates APK splits only for SDK ABIs actually present in that AAR.
This pins source inputs; byte-for-byte reproducibility across machines is not
claimed. The build does not sign, install, push or publish an application.

## Local/CI release preparation

The release target is all four ABIs: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.
On a fresh runner, provision JDK 21, Python 3, Git, Node.js 24, pnpm 11, the
Android SDK from this repository's Gradle versions, NDK 28.2.13676358 and the
pinned Rust/cargo-ndk versions and four Rust targets above. Include `cargo`,
`rustc`, `cargo-ndk`, `node` and `pnpm` on PATH. Then run:

```sh
bash tools/larpgram/prepare-release.sh
```

This entry point builds the custom SDK before configuring Android, verifies all
four SDK ELF architectures and release provenance metadata, then runs release
lint and assembly sequentially with three workers and a 6 GB Gradle heap. The
4 GB attempt on the M1/16 GB host exhausted heap during R8; the release
entry point uses a fresh Gradle process with 6 GB. The embedded Element Call
source build is a Gradle dependency and also needs network
access on a fresh runner. No official SDK fallback is permitted. The AAR check
validates build declarations and ELF headers; it is not a cryptographic proof of
the compiler's source inputs.

For a source-clean check, use a new checkout and new SDK/components directories;
do not reset existing developer work. Build/download caches may be reused, but
record whether Gradle reports `FROM-CACHE`. A successful cached build is not a
claim of a cold toolchain build. There is no hosted CI run until an owner wires
this entry point to their runner.

Assembly can use the existing debug-signature fallback when release credentials
are absent. Such an APK is a local validation artifact. Before distribution,
provide the existing Larpgram signing configuration and verify the final APK's
signing certificate with `apksigner`; assembly alone is not a release approval.
Never put signing credentials or generated SDK/APK binaries in Git.

## Behavior and compatibility

- `Client.media_cache_usage()` reports the stored byte counts for images,
  videos, audio, other/unknown files, thumbnails and protected uploads.
  Thumbnails are separate. Database allocation is reported separately;
  indexes, bookkeeping and free pages are not presented as media content.
- Classification happens before store encryption for recognized formats;
  ambiguous MP4/WebM/Ogg containers stay unknown until event MIME is available
  through `get_media_file` or the upload queue. Upload thumbnails retain their
  own category when a local File key becomes a remote key. Old cache rows remain unknown until metadata is
  known. No URI/file-name guessing or invented breakdown.
- The schema change only adds `larpgram_kind` with an unknown default.
  Existing encrypted values and keys stay intact. Older clients can read
  media and insert records with the original columns; their records default
  to unknown. Tests cover upgrade, the old write format and reopen.
- `Client.clear_media_cache()` holds the SDK media-store cross-process lock
  and deletes only rows with `ignore_policy = false`, in a transaction.
  The send queue stores pending uploads with `ignore_policy = true` and holds
  the same lock while replacing their local keys after upload.
- Clearing does not change retention policy, destroy a client, clear state or
  crypto stores, delete app directories, or touch saved files. Repeated clear
  is safe. Re-fetching media uses the same live client and store.
- SQLite retains freed pages for reuse. Clearing reduces media entries;
  database allocation need not immediately fall. Production VACUUM is not used.

## Validation

```sh
cargo test --locked -p matrix-sdk-sqlite --features bundled --lib media_store
```

Run that from the pinned SDK checkout. Android acceptance additionally requires
an upgrade install on Honor, category and clear/reclear checks, re-download,
account/history/key/draft/send-queue preservation and SD/HD regression checks.
Keep the item open until those device checks pass.

For rollback, restore the prior APK with its compatible SDK; do not remove app
data or crypto storage. The additive cache column is harmless to the pinned
upstream reader/writer. Future SDK upgrades must reconcile the local media-store
schema migration number against upstream before changing the pinned revision.
