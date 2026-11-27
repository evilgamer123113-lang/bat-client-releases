# forge 1.20.1 - protected production native smoke PASS (r2 / a1)

- **Protected build (`r2`)**: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gradle-secondary-cache.ps1 -p ports/forge-1.20.1 build --no-daemon --console=plain` -> `BUILD SUCCESSFUL`, `FORGE_47_MIXIN_BYTECODE_OK`, `PROTECTED_FORGE_ARTIFACT_OK`, `SHA256=DD44776050B7CDC788F4E8445B1417D7499315C0B5CED0C53BE4499C343A30E8`, size `22406143`, log `D:/BatClientBuildCache/mod-ports-20261108/forge-1201-parity-r2-build.log`.
- **Audits**: `audit-port-mixins.py` -> `47 targets; 0 issues`; `audit-constructor-callbacks.py` -> `0 callbacks; 0 issues`.
- **Visual-parity smoke (`a1`)**: `prepare-forge-production-smoke.py` (`47.4.28`) + `run-forge-production-smoke.py --runtime D:/BatClientBuildCache/mod-native/forge-1.20.1-20261113-a1 --version 1.20.1-forge-47.4.28 ... --visual-parity` -> `BAT_VISUAL_PARITY_CAPTURE_OK`, 69 PNGs, log `D:/BatClientBuildCache/mod-ports-20261108/forge-1201-parity-a1.log`.
- **Visual review**: All 6 screens (`main-menu.png`, `game-overlay.png`, `mod-manager.png`, `pause-menu.png`, `hud-editor.png`, `world-hud.png`) crisp. See `../../../../BAT-CLIENT-MOD-AGENT.md` and `../VISUAL-PARITY.md`.
