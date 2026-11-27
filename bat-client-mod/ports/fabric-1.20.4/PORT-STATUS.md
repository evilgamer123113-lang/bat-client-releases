# Fabric 1.20.4 — protected production visual-parity smoke PASS (r4 / a3)

- **Protected build (`r4`)**: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gradle-secondary-cache.ps1 -p ports/fabric-1.20.4 build --no-daemon --console=plain` -> `BUILD SUCCESSFUL`, `PROTECTED_FABRIC_PORT_OK`, `SHA256=5BBE46D4E52AD75EEF976DA6B1574D249A4FA3BF81F0A8D43DF22B5FDD7A7116`, size `22386542`, log `D:/BatClientBuildCache/mod-ports-20261108/fabric-1204-parity-r4-build.log`.
- **Audits**: `audit-port-mixins.py` -> `47 targets; 0 issues`; `audit-constructor-callbacks.py` -> `0 callbacks; 0 issues`.
- **Visual-parity smoke (`a3`)**: `run-fabric-production-smoke.py --minecraft 1.20.4 --runtime D:/BatClientBuildCache/mod-native/fabric-1.20.4-20261113-a3 ... --visual-parity` -> `BAT_VISUAL_PARITY_CAPTURE_OK`, 69 PNGs, log `D:/BatClientBuildCache/mod-ports-20261108/fabric-1204-parity-a3.log`.
- **Fixes**: `scripts/adapt-1.20.4.py` (`ServerEntryExtrasMixin` `ServerInfo;getStatus()` -> `FIELD ServerInfo;online:Z` outside `if args.loader == 'forge'`).
- **Visual review**: All 6 screens (`main-menu.png`, `game-overlay.png`, `mod-manager.png`, `pause-menu.png`, `hud-editor.png`, `world-hud.png`) crisp. See `../../../../BAT-CLIENT-MOD-AGENT.md` and `../VISUAL-PARITY.md`.
