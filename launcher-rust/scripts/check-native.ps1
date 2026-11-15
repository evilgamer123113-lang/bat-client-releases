$ErrorActionPreference = 'Stop'
# Read-only prerequisite inspection. Does not install software or change system settings.
$issues = [Collections.Generic.List[string]]::new()
$cargo = 'D:/BatClientBuildCache/cargo/bin/cargo.exe'
if (-not (Test-Path -LiteralPath $cargo)) { $issues.Add('Rust/Cargo is missing.') }
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/vswhere.exe'
$installation = $null
if (Test-Path -LiteralPath $vswhere) {
    $installation = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
}
if (-not $installation) { $issues.Add('Install Microsoft C++ Build Tools: Desktop development with C++, MSVC x64/x86 tools, and Windows SDK.') }
# Inspect registered product names across machine and user installations.
$webviewFound = $false
foreach ($registryRoot in @('HKLM:/SOFTWARE/WOW6432Node/Microsoft/EdgeUpdate/Clients','HKLM:/SOFTWARE/Microsoft/EdgeUpdate/Clients','HKCU:/SOFTWARE/Microsoft/EdgeUpdate/Clients')) {
    if (Test-Path $registryRoot) {
        foreach ($key in Get-ChildItem $registryRoot) {
            $product = Get-ItemProperty $key.PSPath -ErrorAction SilentlyContinue
            if ($product.name -like '*WebView2*' -and $product.pv -and $product.pv -ne '0.0.0.0') { $webviewFound = $true }
        }
    }
}
if (-not $webviewFound) { Write-Warning 'WebView2 was not detected in the registry. Install Microsoft Edge WebView2 Evergreen Runtime if startup requests it.' }
if ($issues.Count) {
    foreach ($issue in $issues) { Write-Host $issue -ForegroundColor Yellow }
    Write-Host 'Official signed installer: D:/BatClientBuildCache/rust-bootstrap/vs_BuildTools.exe'
    throw 'Native Rust build prerequisites are incomplete.'
}
Write-Host "C++ tools found: $installation"
Write-Host 'Native build prerequisites detected.'
