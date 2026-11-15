$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'check-native.ps1')
$env:RUSTUP_HOME = 'D:/BatClientBuildCache/rustup'
$env:CARGO_HOME = 'D:/BatClientBuildCache/cargo'
$env:CARGO_TARGET_DIR = 'D:/BatClientBuildCache/rust-launcher-target'
$env:TEMP = 'D:/BatClientBuildCache/rust-temp'
$env:TMP = $env:TEMP
$env:PATH = "$env:CARGO_HOME/bin;$env:PATH"
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $projectRoot
try { & cargo run --locked --features custom-protocol --manifest-path src-tauri/Cargo.toml; if($LASTEXITCODE -ne 0){throw "Rust launcher failed: $LASTEXITCODE"} }
finally { Pop-Location }
