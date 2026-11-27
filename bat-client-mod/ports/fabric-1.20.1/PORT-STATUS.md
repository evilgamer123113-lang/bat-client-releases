# fabric 1.20.1 - protected production native smoke PASS (r3 / a3)

- **Protected build (`r3`)**: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gradle-secondary-cache.ps1 -p ports/fabric-1.20.1 build --no-daemon --console=plain` -> `BUILD SUCCESSFUL`, `PROTECT_FABRIC_PORT_OK`, `SHA256=ABFA1042586997BB2D88876381AA5E61CBD53B59E07A2453F6B267C1094F1EB5`, size `22390568`, log `D:/BatClientBuildCache/mod-ports-20261108/fabric-1201-parity-r3-build.log`.
- **Audits**: `audit-port-mixins.py` -> `47 targets; 0 issues`; `audit-constructor-callbacks.py` -> `0 callbacks; 0 issues`.
- **Visual-parity smoke (`a3`)**: `run-fabric-production-smoke.py --minecraft 1.20.1 --runtime D:/BatClientBuildCache/mod-native/fabric-1.20.1-20261113-a3 ... --visual-parity` -> `BAT_VISUAL_PARITY_CAPTURE_OK`, 69 PNGs, log `D:/BatClientBuildCache/mod-ports-20261108/fabric-1201-parity-a3.log`.
- **Fixes**: `scripts/adapter_1201.py` (`WorldEntryFavoriteMixin` `LevelSummary;isUnavailable()Z`, `ServerEntryExtrasMixin` `FIELD ServerInfo;online:Z`).
- **Visual review**: All 6 screens (`main-menu.png`, `game-overlay.png`, `mod-manager.png`, `pause-menu.png`, `hud-editor.png`, `world-hud.png`) crisp. See `../../../../BAT-CLIENT-MOD-AGENT.md` and `../VISUAL-PARITY.md`.
