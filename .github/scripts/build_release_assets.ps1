[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$AllowBlockedInspection,
    [switch]$SkipInstaller
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
$thirdPartyNoticePath = Join-Path $repoRoot 'assets\third_party\THIRD_PARTY_NOTICES.md'
$licensePath = Join-Path $repoRoot 'LICENSE'
if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
    throw "Android release APK is missing: $apkPath"
}
if (-not (Test-Path -LiteralPath (Join-Path $windowsPath 'wynime.exe') -PathType Leaf)) {
    throw "Windows release executable is missing: $windowsPath"
}
if (-not (Test-Path -LiteralPath $thirdPartyNoticePath -PathType Leaf)) {
    throw "Third-party notice is missing: $thirdPartyNoticePath"
}
if (-not (Test-Path -LiteralPath $licensePath -PathType Leaf)) {
    throw "Project license is missing: $licensePath"
}

$outputPath = Join-Path $repoRoot 'build\release'
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null

$apkAssetName = "wynime-$version.apk"
$apkAssetPath = Join-Path $outputPath $apkAssetName
Copy-Item -LiteralPath $apkPath -Destination $apkAssetPath -Force
$apkNoticeCheck = & tar -tf $apkAssetPath | Select-String -SimpleMatch 'assets/flutter_assets/assets/third_party/THIRD_PARTY_NOTICES.md'
if (-not $apkNoticeCheck) {
    throw 'Android APK does not contain the bundled third-party notice.'
}
$apkLicenseCheck = & tar -tf $apkAssetPath | Select-String -SimpleMatch 'assets/flutter_assets/LICENSE'
if (-not $apkLicenseCheck) {
    throw 'Android APK does not contain the bundled Wynime LICENSE.'
}
$apkHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $apkAssetPath).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$apkAssetPath.sha256" -Value "$apkHash  $apkAssetName" -NoNewline -Encoding ascii

$stagePath = Join-Path $outputPath "wynime-$version-windows-x64-stage"
if (Test-Path -LiteralPath $stagePath) {
    Remove-Item -LiteralPath $stagePath -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $stagePath | Out-Null
Copy-Item -Path (Join-Path $windowsPath '*') -Destination $stagePath -Recurse -Force
Copy-Item -LiteralPath (Join-Path $repoRoot 'README.md') -Destination (Join-Path $stagePath 'README.md') -Force
Copy-Item -LiteralPath $licensePath -Destination (Join-Path $stagePath 'LICENSE') -Force
Copy-Item -LiteralPath $thirdPartyNoticePath -Destination (Join-Path $stagePath 'THIRD_PARTY_NOTICES.md') -Force
Copy-Item -LiteralPath (Join-Path $repoRoot 'docs\release-notes-1.0.1.md') -Destination (Join-Path $stagePath 'RELEASE_NOTES.md') -Force
Set-Content -LiteralPath (Join-Path $stagePath 'version.txt') -Value $version -NoNewline -Encoding ascii

if (-not (Test-Path -LiteralPath (Join-Path $stagePath 'THIRD_PARTY_NOTICES.md') -PathType Leaf)) {
    throw 'Windows bundle does not contain the bundled third-party notice.'
}
if (-not (Test-Path -LiteralPath (Join-Path $stagePath 'LICENSE') -PathType Leaf)) {
    throw 'Windows bundle does not contain the project license.'
}

$zipAssetName = "wynime-$version.zip"
$zipAssetPath = Join-Path $outputPath $zipAssetName
if (Test-Path -LiteralPath $zipAssetPath) {
    Remove-Item -LiteralPath $zipAssetPath -Force
}
Compress-Archive -Path (Join-Path $stagePath '*') -DestinationPath $zipAssetPath -CompressionLevel Optimal
$zipHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $zipAssetPath).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$zipAssetPath.sha256" -Value "$zipHash  $zipAssetName" -NoNewline -Encoding ascii

Remove-Item -LiteralPath $stagePath -Recurse -Force

$installerAssetName = "wynime-$version-windows-x64-setup.exe"
$installerAssetPath = Join-Path $outputPath $installerAssetName
if (-not $SkipInstaller) {
    $isccCommand = Get-Command ISCC.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    $isccPath = if ($isccCommand) { $isccCommand.Source } else {
        @(
            'C:\Program Files (x86)\Inno Setup 6\ISCC.exe',
            'C:\Program Files\Inno Setup 6\ISCC.exe'
        ) | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
    }
    if ([string]::IsNullOrWhiteSpace($isccPath)) {
        throw 'Inno Setup compiler ISCC.exe is required to build the Windows installer. Use -SkipInstaller only for portable-only local inspection.'
    }

    $issPath = Join-Path $repoRoot 'installer\windows\wynime.iss'
    if (-not (Test-Path -LiteralPath $issPath -PathType Leaf)) {
        throw "Inno Setup script is missing: $issPath"
    }
    Remove-Item -LiteralPath $installerAssetPath -Force -ErrorAction SilentlyContinue
    & $isccPath $issPath "/DMyAppVersion=$version" "/O$outputPath"
    if ($LASTEXITCODE -ne 0) {
        throw "Inno Setup failed with exit code $LASTEXITCODE."
    }
    if (-not (Test-Path -LiteralPath $installerAssetPath -PathType Leaf)) {
        throw "Windows installer is missing: $installerAssetPath"
    }
    $installerHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $installerAssetPath).Hash.ToLowerInvariant()
    Set-Content -LiteralPath "$installerAssetPath.sha256" -Value "$installerHash  $installerAssetName" -NoNewline -Encoding ascii
}

Write-Output "version=$version"
Write-Output "versionCode=$versionCode"
Write-Output "android=$apkAssetPath|$apkHash"
Write-Output "windows=$zipAssetPath|$zipHash"
if (-not $SkipInstaller) {
    Write-Output "installer=$installerAssetPath|$installerHash"
}
