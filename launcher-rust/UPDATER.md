# Rust updater and the shared Bat Client manifest

The native launcher reads `https://batclient.xyz/update.json` in Rust. It **only** consumes the `rust` object. Existing top-level `version`, `website`, `download`, `sha256`, `required`, `changelog` and legacy `platforms` entries belong to Electron and the download website. They must not become Rust update packages.

```json
{
  "rust": {
    "schema": 1,
    "product": "bat-client-rust",
    "enabled": false,
    "version": "0.1.0",
    "notes": "Release notes",
    "platforms": {}
  }
}
```

Each published platform entry contains `url`, `signature` (the entire base64 `.sig` file), `sha256` and `size` in bytes. Keys are `windows-x86_64`, `darwin-x86_64`, `darwin-aarch64`, `linux-x86_64`. Native selection also recognizes Windows/Linux aarch64, but those packages are not in the current build matrix. Missing targets never fall back to another platform or to Electron.

## User flow

Settings → Launcher → Launcher updates has Check for updates, progress, and Install update & restart. Startup respects the saved `autoCheckUpdates` setting. When enabled, the branded loading screen checks the shared manifest, automatically downloads and verifies an available Rust update, installs it and restarts the app without a confirmation prompt. The loading screen stays visible through download/verification/installation; it does not expose Home while the native installer is restarting. A manual Check in Settings still checks only; Install update & restart remains a retry action. All installation is blocked while Minecraft or the legacy Electron launcher is running; the game lock remains held throughout download/install. No worlds, mods, accounts or settings are replaced by our updater code.

The bounded HTTPS manifest check does not follow redirects. Package URLs must belong to the existing Bat Client GitHub releases repository. Downloads permit only GitHub's HTTPS asset redirect hosts, enforce exact advertised size (maximum512MiB), verify SHA256 and then use Tauri's signature verification and platform-native installer. `restore_update` enforces a signature bound to the announced version, preventing old signed bytes being relabeled as a new release. No downgrade comparator is used in production. A separate explicit smoke harness overrides comparison only to verify the current local package, without installing it.

Windows uses signed NSIS packages and the installer's restart path. macOS uses signed `.app.tar.gz` updater artifacts; Linux uses signed AppImage artifacts. DMG files are for first installation, not AppImage/macOS updater payloads. macOS Apple code-signing/notarization is separate from Tauri update signing and remains release work.

## Signing and packages

- Public key: `src-tauri/updater.pub`, also configured in `tauri.conf.json`. `requireSignedVersion` and `createUpdaterArtifacts` are enabled.
- Persistent private key is outside the repository at `D:/BatClientPrivate/rust-updater/bat-client-next.key`, in an ACL-restricted directory. **Preserve this key; do not regenerate or include it in a package, site, commit, log or screenshot.** Back it up securely before publishing the first release.
- Local signed builds set `TAURI_SIGNING_PRIVATE_KEY` to that key file path and `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` to the matching password. The generated local key has no password and relies on its directory ACL; do not expose its contents.
- The artifact-only, manually dispatched `.github/workflows/rust-packages.yml` needs repository secrets `BAT_RUST_UPDATER_PRIVATE_KEY` and optionally `BAT_RUST_UPDATER_PASSWORD`. They have **not** been configured remotely by this task. The workflow does not publish releases or deploy the site.
- Private Actions run 42069842859 built the macOS Intel and Apple Silicon DMGs and app archives, plus the Linux x64 AppImage and Debian package. The Windows x64 NSIS package was built locally. The artifact-only workflow keeps the production signing key off CI. Sign downloaded updater artifacts locally with Tauri CLI 2.12.4 or a compatible CLI using signer sign --app-version <version> and the existing key path. Mac DMGs are ad hoc signed. Apple Developer ID signing and notarization remain separate release steps.

## Preparing update.json

`scripts/prepare-update-manifest.py` takes the existing shared manifest, version, and explicit `target=local-package` pairs. Every package requires an adjacent `.sig`. It calculates actual SHA256/size, creates GitHub asset URLs under `rust-vVERSION`, preserves **every** legacy field and writes a separate reviewable output. It always leaves the Rust channel disabled. Do not populate missing platforms with imaginary packages.

Example from the Raven-Client root:

```powershell
python launcher-rust/scripts/prepare-update-manifest.py --source C:/Users/Noor-ul-Islam/Desktop/BatClient_SEO_Ready/update.json --output launcher-rust/output/update.rust-draft.json --version 0.1.0 --artifact 'windows-x86_64=D:/BatClientBuildCache/rust-launcher-target/release/bundle/nsis/Bat Client Next_0.1.0_x64-setup.exe'
```

Only after matching signed artifacts are published, downloaded back, verified and tested should a release activate `rust.enabled` and deploy the reviewed shared manifest. Current production publishing/deployment is not authorized by this implementation task. Keep the existing Electron `download` unchanged. The current development binary0.1.0 is not a complete clean-PC release candidate.

## Verification

- Rust unit tests cover channel separation, platform selection, disabled/missing channels, missing checksums/signatures, untrusted URLs and no legacy fallback.
- `live_shared_manifest` is an explicit read-only live endpoint check.
- `startup-updater-smoke.cjs` covers automatic install with visible splash/byte progress, current/unpublished versions, the disabled preference, offline failure, invalid signature failure and retaining the splash during native installation. These are mocked IPC tests, not real package replacement.
- `updater-ui-smoke.cjs` mocks the native IPC and verifies availability, explicit install action and progress; it does not install or publish.
- `--updater-verify-smoke` with `BAT_UPDATER_SMOKE_PACKAGE` pointing to a signed local package verifies authentic bytes, rejects tampering and rejects version substitution. It never downloads or installs.
- Actual replacement/restart across two installed releases and native installation/gameplay on macOS and Linux still need release validation. Do not claim those passed based on unit tests.
