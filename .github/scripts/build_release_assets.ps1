[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$AllowBlockedInspection,
    [switch]$SkipInstaller
)

$ErrorActionPreference = 'Stop'

function Get-CanonicalUtf8Sha256([string]$Path) {
    $text = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $Path).Path)
    $text = $text.Replace("`r`n", "`n").Replace("`r", "`n")
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($text)
    return [BitConverter]::ToString(
        [Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
    ).Replace('-', '').ToLowerInvariant()
}

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
$lgplV21LicensePath = Join-Path $repoRoot 'assets\third_party\COPYING.LGPLv2.1'
$lgplV3LicensePath = Join-Path $repoRoot 'assets\third_party\COPYING.LGPLv3'
$sourceOfferPath = Join-Path $repoRoot 'assets\third_party\THIRD_PARTY_SOURCE_OFFER.md'
$nativeLockPath = Join-Path $repoRoot 'assets\third_party\WINDOWS_LIBMPV_BUILD.lock.json'
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
if (-not (Test-Path -LiteralPath $lgplV21LicensePath -PathType Leaf)) {
    throw "LGPLv2.1 license text is missing: $lgplV21LicensePath"
}
if (-not (Test-Path -LiteralPath $lgplV3LicensePath -PathType Leaf)) {
    throw "LGPLv3 license text is missing: $lgplV3LicensePath"
}
if (-not (Test-Path -LiteralPath $sourceOfferPath -PathType Leaf)) {
    throw "Corresponding-source offer is missing: $sourceOfferPath"
}
if (-not (Test-Path -LiteralPath $nativeLockPath -PathType Leaf)) {
    throw "Windows native provenance lock is missing: $nativeLockPath"
}
$nativeLock = Get-Content -Raw -LiteralPath $nativeLockPath | ConvertFrom-Json
if ($null -eq $nativeLock.licenses.androidFfmpeg -or $null -eq $nativeLock.licenses.windowsFfmpeg) {
    throw 'Native provenance lock must identify both Android LGPLv2.1 and Windows LGPLv3.'
}
$androidLicenseHash = Get-CanonicalUtf8Sha256 $lgplV21LicensePath
if ($androidLicenseHash -ne ([string]$nativeLock.licenses.androidFfmpeg.sha256).ToLowerInvariant()) {
    throw "Android LGPLv2.1 license SHA-256 $androidLicenseHash does not match the provenance lock."
}
$windowsLicenseHash = Get-CanonicalUtf8Sha256 $lgplV3LicensePath
if ($windowsLicenseHash -ne ([string]$nativeLock.licenses.windowsFfmpeg.sha256).ToLowerInvariant()) {
    throw "Windows LGPLv3 license SHA-256 $windowsLicenseHash does not match the provenance lock."
}
foreach ($licenseCheck in @(
        @{ Path = $lgplV21LicensePath; Header = 'Version 2.1, February 1999' },
        @{ Path = $lgplV3LicensePath; Header = 'Version 3, 29 June 2007' }
    )) {
    $licenseText = Get-Content -Raw -LiteralPath $licenseCheck.Path
    if (-not $licenseText.Contains('GNU LESSER GENERAL PUBLIC LICENSE') -or -not $licenseText.Contains($licenseCheck.Header)) {
        throw "Applicable LGPL license text is incomplete: $($licenseCheck.Path)"
    }
}

$nativeVerifierPath = Join-Path $repoRoot '.github\scripts\verify_windows_native_provenance.ps1'
if (-not (Test-Path -LiteralPath $nativeVerifierPath -PathType Leaf)) {
    throw "Windows native provenance verifier is missing: $nativeVerifierPath"
}
$nativeDllPath = Join-Path $windowsPath 'libmpv-2.dll'
& $nativeVerifierPath -DllPath $nativeDllPath -LockPath $nativeLockPath -LicensePath $lgplV3LicensePath

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
$apkLgplCheck = & tar -tf $apkAssetPath | Select-String -SimpleMatch 'assets/flutter_assets/assets/third_party/COPYING.LGPLv2.1'
if (-not $apkLgplCheck) {
    throw 'Android APK does not contain the bundled LGPLv2.1 license text.'
}
$apkLgplV3Check = & tar -tf $apkAssetPath | Select-String -SimpleMatch 'assets/flutter_assets/assets/third_party/COPYING.LGPLv3'
if (-not $apkLgplV3Check) {
    throw 'Android APK does not contain the bundled LGPLv3 license text required by the Windows native dependency.'
}
$apkSourceOfferCheck = & tar -tf $apkAssetPath | Select-String -SimpleMatch 'assets/flutter_assets/assets/third_party/THIRD_PARTY_SOURCE_OFFER.md'
if (-not $apkSourceOfferCheck) {
    throw 'Android APK does not contain the corresponding-source offer.'
}
$apkNativeLockCheck = & tar -tf $apkAssetPath | Select-String -SimpleMatch 'assets/flutter_assets/assets/third_party/WINDOWS_LIBMPV_BUILD.lock.json'
if (-not $apkNativeLockCheck) {
    throw 'Android APK does not contain the Windows native provenance lock.'
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
Copy-Item -LiteralPath $lgplV21LicensePath -Destination (Join-Path $stagePath 'COPYING.LGPLv2.1') -Force
Copy-Item -LiteralPath $lgplV3LicensePath -Destination (Join-Path $stagePath 'COPYING.LGPLv3') -Force
Copy-Item -LiteralPath $sourceOfferPath -Destination (Join-Path $stagePath 'THIRD_PARTY_SOURCE_OFFER.md') -Force
Copy-Item -LiteralPath $nativeLockPath -Destination (Join-Path $stagePath 'WINDOWS_LIBMPV_BUILD.lock.json') -Force
Copy-Item -LiteralPath (Join-Path $repoRoot 'docs\release-notes-1.0.1.md') -Destination (Join-Path $stagePath 'RELEASE_NOTES.md') -Force
Set-Content -LiteralPath (Join-Path $stagePath 'version.txt') -Value $version -NoNewline -Encoding ascii

if (-not (Test-Path -LiteralPath (Join-Path $stagePath 'THIRD_PARTY_NOTICES.md') -PathType Leaf)) {
    throw 'Windows bundle does not contain the bundled third-party notice.'
}
if (-not (Test-Path -LiteralPath (Join-Path $stagePath 'LICENSE') -PathType Leaf)) {
    throw 'Windows bundle does not contain the project license.'
}
if (-not (Test-Path -LiteralPath (Join-Path $stagePath 'COPYING.LGPLv2.1') -PathType Leaf)) {
    throw 'Windows bundle does not contain the LGPLv2.1 license text.'
}
if (-not (Test-Path -LiteralPath (Join-Path $stagePath 'COPYING.LGPLv3') -PathType Leaf)) {
    throw 'Windows bundle does not contain the LGPLv3 license text required by the Windows native dependency.'
}
if (-not (Test-Path -LiteralPath (Join-Path $stagePath 'THIRD_PARTY_SOURCE_OFFER.md') -PathType Leaf)) {
    throw 'Windows bundle does not contain the corresponding-source offer.'
}
if (-not (Test-Path -LiteralPath (Join-Path $stagePath 'WINDOWS_LIBMPV_BUILD.lock.json') -PathType Leaf)) {
    throw 'Windows bundle does not contain the native provenance lock.'
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
