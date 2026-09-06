[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$AllowBlockedInspection
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location -LiteralPath $repoRoot

$pubspec = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'pubspec.yaml')
$versionMatch = [regex]::Match(
    $pubspec,
    '(?m)^version:\s*(?<version>\d+\.\d+\.\d+)(?:\+(?<code>\d+))?\s*$'
)
if (-not $versionMatch.Success) {
    throw 'Unable to read a SemVer version from pubspec.yaml.'
}

$version = $versionMatch.Groups['version'].Value
$versionCode = $versionMatch.Groups['code'].Value
if ([string]::IsNullOrWhiteSpace($versionCode) -or [int]$versionCode -le 0) {
    throw "pubspec.yaml must contain a positive Android build number: $versionCode"
}

$statusPath = Join-Path $repoRoot 'docs/PHASE12_STATUS.md'
$status = Get-Content -Raw -LiteralPath $statusPath
$statusCodeMatch = [regex]::Match($status, '(?m)^\s*`(?<code>[A-Z][A-Z0-9_]*)`\s*$')
$statusCode = if ($statusCodeMatch.Success) { $statusCodeMatch.Groups['code'].Value } else { '' }
if (-not $AllowBlockedInspection -and $statusCode -ne 'RELEASE_READY') {
    throw "Release status is $statusCode, not RELEASE_READY. Use -AllowBlockedInspection only for local inspection artifacts."
}

if (-not $SkipBuild) {
    & flutter build apk --release --no-pub --suppress-analytics
    if ($LASTEXITCODE -ne 0) { throw 'Flutter Android release build failed.' }

    & flutter build windows --release --no-pub --suppress-analytics
    if ($LASTEXITCODE -ne 0) { throw 'Flutter Windows release build failed.' }
}

$apkPath = Join-Path $repoRoot 'build\app\outputs\flutter-apk\app-release.apk'
$windowsPath = Join-Path $repoRoot 'build\windows\x64\runner\Release'
if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
    throw "Android release APK is missing: $apkPath"
}
if (-not (Test-Path -LiteralPath (Join-Path $windowsPath 'wynime.exe') -PathType Leaf)) {
    throw "Windows release executable is missing: $windowsPath"
}

$outputPath = Join-Path $repoRoot 'build\release'
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null

$apkAssetName = "wynime-$version.apk"
$apkAssetPath = Join-Path $outputPath $apkAssetName
Copy-Item -LiteralPath $apkPath -Destination $apkAssetPath -Force
$apkHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $apkAssetPath).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$apkAssetPath.sha256" -Value "$apkHash  $apkAssetName" -NoNewline -Encoding ascii

$stagePath = Join-Path $outputPath "wynime-$version-windows-x64-stage"
if (Test-Path -LiteralPath $stagePath) {
    Remove-Item -LiteralPath $stagePath -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $stagePath | Out-Null
Copy-Item -Path (Join-Path $windowsPath '*') -Destination $stagePath -Recurse -Force
Copy-Item -LiteralPath (Join-Path $repoRoot 'README.md') -Destination (Join-Path $stagePath 'README.md') -Force
Set-Content -LiteralPath (Join-Path $stagePath 'version.txt') -Value $version -NoNewline -Encoding ascii

$zipAssetName = "wynime-$version.zip"
$zipAssetPath = Join-Path $outputPath $zipAssetName
if (Test-Path -LiteralPath $zipAssetPath) {
    Remove-Item -LiteralPath $zipAssetPath -Force
}
Compress-Archive -Path (Join-Path $stagePath '*') -DestinationPath $zipAssetPath -CompressionLevel Optimal
$zipHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $zipAssetPath).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$zipAssetPath.sha256" -Value "$zipHash  $zipAssetName" -NoNewline -Encoding ascii

Remove-Item -LiteralPath $stagePath -Recurse -Force

Write-Output "version=$version"
Write-Output "versionCode=$versionCode"
Write-Output "android=$apkAssetPath|$apkHash"
Write-Output "windows=$zipAssetPath|$zipHash"
