# Fabric 1.20.6 — protected production visual-parity smoke PASS (r2 / a1)

- **Protected build (`r2`)**: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gradle-secondary-cache.ps1 -p ports/fabric-1.20.6 build --no-daemon --console=plain` -> `BUILD SUCCESSFUL`, `PROTECTED_FABRIC_PORT_OK`, `SHA256=80F270FA8A5BBA09BD5F929E97118566AA0EC577DAF8055A885B01300E5F3C95`, size `22382658`, log `D:/BatClientBuildCache/mod-ports-20261108/fabric-1206-parity-r2-build.log`.
- **Audits**: `audit-port-mixins.py` -> `47 targets; 0 issues`; `audit-constructor-callbacks.py` -> `0 callbacks; 0 issues`.
- **Visual-parity smoke (`a1`)**: `run-fabric-production-smoke.py --minecraft 1.20.6 --runtime D:/BatClientBuildCache/mod-native/fabric-1.20.6-20261113-a1 ... --visual-parity` -> `BAT_VISUAL_PARITY_CAPTURE_OK`, 69 PNGs, log `D:/BatClientBuildCache/mod-ports-20261108/fabric-1206-parity-a1.log`.
- **Fixes**: `scripts/adapt-1.20.6.py` (`ServerChunkLoadingManager` -> `ThreadedAnvilChunkStorage` for `LocalChunkBatchMixin`).
- **Visual review**: All 6 screens (`main-menu.png`, `game-overlay.png`, `mod-manager.png`, `pause-menu.png`, `hud-editor.png`, `world-hud.png`) crisp. See `../../../../BAT-CLIENT-MOD-AGENT.md` and `../VISUAL-PARITY.md`.
