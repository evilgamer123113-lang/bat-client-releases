# Explicit opt-in build entrypoint. Does not move workspaces or change global settings.
$ErrorActionPreference='Stop'
$batCacheRoot='D:\BatClientBuildCache\gradle-home'
$batProjectCacheRoot='D:\BatClientBuildCache\port-project-cache'
$batTempRoot='D:\BatClientBuildCache\temp'
$batModRoot=Split-Path -Parent $PSScriptRoot
if (!(Test-Path -LiteralPath $batCacheRoot -PathType Container)) {
    throw 'The verified secondary build cache has not been prepared.'
}
if ((Resolve-Path -LiteralPath $batCacheRoot).Path -ne $batCacheRoot) {
    throw 'Unexpected secondary cache location.'
}
New-Item -ItemType Directory -Force -Path $batProjectCacheRoot, $batTempRoot | Out-Null
$env:TEMP=$batTempRoot
$env:TMP=$batTempRoot
$projectCacheName='root'
for ($i=0; $i -lt $args.Count; $i++) {
    if ($args[$i] -eq '-p' -and ($i + 1) -lt $args.Count) {
        $projectCacheName=Split-Path -Leaf $args[$i + 1]
    }
}
$extraArgs=@()
if ($args -notcontains '--project-cache-dir') {
    $targetCache=Join-Path $batProjectCacheRoot $projectCacheName
    New-Item -ItemType Directory -Force -Path $targetCache | Out-Null
    $extraArgs=@('--project-cache-dir', $targetCache)
}
$batWrapper=Join-Path $batModRoot 'gradlew.bat'
if (!(Test-Path -LiteralPath $batWrapper -PathType Leaf)) { throw 'Mod Gradle wrapper missing.' }
Push-Location -LiteralPath $batModRoot
try {
    & $batWrapper --gradle-user-home $batCacheRoot @extraArgs @args
    $batBuildExit=$LASTEXITCODE
} finally {
    Pop-Location
}
exit $batBuildExit
