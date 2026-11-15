$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'check-native.ps1')
$env:RUSTUP_HOME = 'D:/BatClientBuildCache/rustup'
$env:CARGO_HOME = 'D:/BatClientBuildCache/cargo'
$env:CARGO_TARGET_DIR = 'D:/BatClientBuildCache/rust-launcher-target'
$env:TEMP = 'D:/BatClientBuildCache/rust-temp'
$env:TMP = $env:TEMP
$env:PATH = "$env:CARGO_HOME/bin;$env:PATH"
$manifest = Join-Path $PSScriptRoot '../src-tauri/Cargo.toml'
& cargo build --locked --release --features custom-protocol --manifest-path $manifest
if ($LASTEXITCODE -ne 0) { throw "Rust launcher build failed: $LASTEXITCODE" }
Write-Host "Native executable: $env:CARGO_TARGET_DIR/release/bat-client-next.exe"
