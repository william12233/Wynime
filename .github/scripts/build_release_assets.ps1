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
$gplV3LicensePath = Join-Path $repoRoot 'assets\third_party\COPYING.GPLv3'
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
if (-not (Test-Path -LiteralPath $lgplV3LicensePath -PathType Leaf)) {
    throw "LGPLv3 license text is missing: $lgplV3LicensePath"
}
if (-not (Test-Path -LiteralPath $gplV3LicensePath -PathType Leaf)) {
    throw "GPLv3 license text is missing: $gplV3LicensePath"
}
if (-not (Test-Path -LiteralPath $sourceOfferPath -PathType Leaf)) {
    throw "Corresponding-source offer is missing: $sourceOfferPath"
}
if (-not (Test-Path -LiteralPath $nativeLockPath -PathType Leaf)) {
    throw "Windows native provenance lock is missing: $nativeLockPath"
}
$nativeLock = Get-Content -Raw -LiteralPath $nativeLockPath | ConvertFrom-Json
if ($null -eq $nativeLock.licenses.androidFfmpeg -or $null -eq $nativeLock.licenses.windowsFfmpeg) {
    throw 'Native provenance lock must identify the Android and Windows LGPLv3 mappings.'
}
if ($null -eq $nativeLock.licenses.combinedWorkGpl -or
    $nativeLock.licenses.combinedWorkGpl.spdx -ne 'GPL-3.0-or-later' -or
    $nativeLock.licenses.combinedWorkGpl.file -ne 'assets/third_party/COPYING.GPLv3' -or
    $nativeLock.licenses.combinedWorkGpl.sha256 -ne '8ceb4b9ee5adedde47b31e975c1d90c73ad27b6b165a1dcd80c7c545eb65b903' -or
    $nativeLock.licenses.combinedWorkGpl.sourceBlob -ne '94a9ed024d3859793618152ea559a168bbcbb5e2') {
    throw 'Native provenance lock must identify the exact GPLv3 Combined Work license material.'
}
if ($nativeLock.androidBuild.releaseTag -ne 'v1.1.7' -or
    $nativeLock.androidBuild.releaseTagCommit -ne 'fe8c3ac1a91c09aa6fb1deccbc833f1bafa54768' -or
    $nativeLock.androidBuild.flavor -ne 'default' -or
    $nativeLock.androidBuild.mpvCommit -ne '78d43740f52db817d98bcf24fb30a76ab6fa13ff' -or
    $nativeLock.androidBuild.ffmpeg.version -ne '6.0' -or
    $nativeLock.androidBuild.ffmpeg.sourceTag -ne 'n6.0' -or
    $nativeLock.androidBuild.ffmpeg.sourceTagCommit -ne '3949db4d261748a9f34358a388ee255ad1a7f0c0' -or
    $nativeLock.androidBuild.ffmpeg.sourceCommit -ne 'ea3d24bbe3c58b171e55fe2151fc7ffaca3ab3d2' -or
    $nativeLock.androidBuild.ffmpeg.configurePolicy.script -ne 'buildscripts/flavors/default.sh' -or
    $nativeLock.androidBuild.ffmpeg.configurePolicy.scriptBlob -ne '5968d5d2dc84dd4726540b846acbd26caa1984c3' -or
    $nativeLock.androidBuild.ffmpeg.configurePolicy.scriptSha256 -ne 'd5b84c3398fc673c6210f6b0559a163d5c1e1c146b1dc52eab211c3ad0ba09ce' -or
    $nativeLock.androidBuild.scripts.dependencyRecord.blob -ne '481757452663bdac8162dea49e1699176411c5c7' -or
    $nativeLock.androidBuild.scripts.dependencyRecord.sha256 -ne '3ac50b68e1669694f3e0b77d45a66bdae27a7bb23600389f6cfb686b924483b3') {
    throw 'Android native provenance lock does not match the pinned v1.1.7 default build record.'
}
$requiredAndroidFfmpegFlags = @(
    '--disable-gpl',
    '--disable-nonfree',
    '--enable-version3',
    '--enable-static',
    '--disable-shared',
    '--enable-mbedtls'
)
$actualRequiredAndroidFfmpegFlags = @($nativeLock.androidBuild.ffmpeg.configurePolicy.required) -join '|'
$expectedRequiredAndroidFfmpegFlags = $requiredAndroidFfmpegFlags -join '|'
$actualForbiddenAndroidFfmpegFlags = @($nativeLock.androidBuild.ffmpeg.configurePolicy.forbidden) -join '|'
if ($actualRequiredAndroidFfmpegFlags -cne $expectedRequiredAndroidFfmpegFlags -or
    $actualForbiddenAndroidFfmpegFlags -cne '--enable-gpl|--enable-nonfree') {
    throw 'Android native provenance lock does not match the pinned FFmpeg configure policy.'
}
if ($nativeLock.licenses.androidFfmpeg.spdx -ne 'LGPL-3.0-or-later' -or
    $nativeLock.licenses.androidFfmpeg.file -ne 'assets/third_party/COPYING.LGPLv3' -or
    $nativeLock.licenses.windowsFfmpeg.spdx -ne 'LGPL-3.0-or-later' -or
    $nativeLock.licenses.windowsFfmpeg.file -ne 'assets/third_party/COPYING.LGPLv3') {
    throw 'Native provenance lock must map both native FFmpeg components to LGPLv3.'
}
$windowsLicenseHash = Get-CanonicalUtf8Sha256 $lgplV3LicensePath
if ($windowsLicenseHash -ne ([string]$nativeLock.licenses.androidFfmpeg.sha256).ToLowerInvariant() -or
    $windowsLicenseHash -ne ([string]$nativeLock.licenses.windowsFfmpeg.sha256).ToLowerInvariant()) {
    throw "LGPLv3 license SHA-256 $windowsLicenseHash does not match both Android and Windows provenance mappings."
}
$gplV3LicenseHash = Get-CanonicalUtf8Sha256 $gplV3LicensePath
if ($gplV3LicenseHash -ne ([string]$nativeLock.licenses.combinedWorkGpl.sha256).ToLowerInvariant()) {
    throw "GPLv3 license SHA-256 $gplV3LicenseHash does not match the Combined Work provenance mapping."
}
$gplV3Text = Get-Content -Raw -LiteralPath $gplV3LicensePath
if (-not $gplV3Text.Contains('GNU GENERAL PUBLIC LICENSE') -or
    -not $gplV3Text.Contains('Version 3, 29 June 2007')) {
    throw 'Applicable GPLv3 license text is incomplete.'
}
foreach ($licenseCheck in @(
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
$apkLgplV3Check = & tar -tf $apkAssetPath | Select-String -SimpleMatch 'assets/flutter_assets/assets/third_party/COPYING.LGPLv3'
if (-not $apkLgplV3Check) {
    throw 'Android APK does not contain the bundled LGPLv3 license text required by the Android native dependency.'
}
$apkGplV3Check = & tar -tf $apkAssetPath | Select-String -SimpleMatch 'assets/flutter_assets/assets/third_party/COPYING.GPLv3'
if (-not $apkGplV3Check) {
    throw 'Android APK does not contain the bundled GPLv3 license text required by the LGPLv3 Combined Work.'
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
Copy-Item -LiteralPath $gplV3LicensePath -Destination (Join-Path $stagePath 'COPYING.GPLv3') -Force
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
if (-not (Test-Path -LiteralPath (Join-Path $stagePath 'COPYING.LGPLv3') -PathType Leaf)) {
    throw 'Windows bundle does not contain the LGPLv3 license text required by the Windows native dependency.'
}
if (-not (Test-Path -LiteralPath (Join-Path $stagePath 'COPYING.GPLv3') -PathType Leaf)) {
    throw 'Windows bundle does not contain the GPLv3 license text required by the LGPLv3 Combined Work.'
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
