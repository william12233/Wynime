[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$DllPath,
    [Parameter(Mandatory = $true)]
    [string]$LockPath,
    [string]$ArchivePath
)

$ErrorActionPreference = 'Stop'

function Fail-Provenance([string]$Message) {
    throw "WINDOWS_NATIVE_PROVENANCE_FAIL: $Message"
}

if (-not (Test-Path -LiteralPath $DllPath -PathType Leaf)) {
    Fail-Provenance "DLL is missing: $DllPath"
}
if (-not (Test-Path -LiteralPath $LockPath -PathType Leaf)) {
    Fail-Provenance "provenance lock is missing: $LockPath"
}

$lock = Get-Content -Raw -LiteralPath $LockPath | ConvertFrom-Json
$dllItem = Get-Item -LiteralPath $DllPath
$dllHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $DllPath).Hash.ToLowerInvariant()
if ($dllItem.Length -ne [int64]$lock.runtime.size) {
    Fail-Provenance "DLL size $($dllItem.Length) does not match locked size $($lock.runtime.size)."
}
if ($dllHash -ne $lock.runtime.sha256.ToLowerInvariant()) {
    Fail-Provenance "DLL SHA-256 $dllHash does not match locked hash $($lock.runtime.sha256)."
}

if (-not [string]::IsNullOrWhiteSpace($ArchivePath)) {
    if (-not (Test-Path -LiteralPath $ArchivePath -PathType Leaf)) {
        Fail-Provenance "native archive is missing: $ArchivePath"
    }
    $archiveItem = Get-Item -LiteralPath $ArchivePath
    $archiveHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $ArchivePath).Hash.ToLowerInvariant()
    if ($archiveItem.Length -ne [int64]$lock.nativeArchive.size) {
        Fail-Provenance "archive size $($archiveItem.Length) does not match locked size $($lock.nativeArchive.size)."
    }
    if ($archiveHash -ne $lock.nativeArchive.sha256.ToLowerInvariant()) {
        Fail-Provenance "archive SHA-256 $archiveHash does not match locked hash $($lock.nativeArchive.sha256)."
    }
}

$ascii = [Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes($DllPath))
$embeddedConfiguration = [regex]::Match($ascii, 'Configuration: (?<value>[^\x00]+)')
if (-not $embeddedConfiguration.Success) {
    Fail-Provenance 'embedded mpv Configuration string is missing.'
}
$configuration = $embeddedConfiguration.Groups['value'].Value.Trim()
$configurationHash = [BitConverter]::ToString(
    [Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($configuration))
).Replace('-', '').ToLowerInvariant()
if ($configurationHash -ne $lock.runtime.configurationSha256.ToLowerInvariant()) {
    Fail-Provenance "mpv configuration SHA-256 $configurationHash does not match locked hash $($lock.runtime.configurationSha256)."
}
foreach ($required in $lock.runtime.configurationRequired) {
    if (-not $configuration.Contains([string]$required)) {
        Fail-Provenance "mpv configuration is missing required flag: $required"
    }
}
foreach ($forbidden in $lock.runtime.configurationForbidden) {
    if ($configuration.Contains([string]$forbidden)) {
        Fail-Provenance "mpv configuration contains forbidden flag: $forbidden"
    }
}

$sourceText = Get-Content -Raw -LiteralPath $LockPath
foreach ($required in @(
        $lock.package.commit,
        $lock.nativeArchive.releaseTagCommit,
        $lock.build.mpvCommit,
        $lock.build.ffmpegCommit,
        $lock.build.sourceBlobs.'packages/ffmpeg.cmake',
        $lock.build.sourceBlobs.'packages/mpv.cmake'
    )) {
    if (-not $sourceText.Contains([string]$required)) {
        Fail-Provenance "provenance lock does not contain required immutable identity: $required"
    }
}

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class WynimeWindowsNativeProvenanceProbe {
    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    public static extern IntPtr SetDllDirectory(string path);
    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    public static extern IntPtr LoadLibrary(string path);
    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern IntPtr GetProcAddress(IntPtr module, string name);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)]
    public delegate IntPtr Create();
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)]
    public delegate int Initialize(IntPtr context);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)]
    public delegate IntPtr GetPropertyString(IntPtr context, [MarshalAs(UnmanagedType.LPStr)] string name);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)]
    public delegate void TerminateDestroy(IntPtr context);
}
'@

$resolvedDllPath = (Resolve-Path -LiteralPath $DllPath).Path
$dllDirectory = Split-Path -Parent $resolvedDllPath
[void][WynimeWindowsNativeProvenanceProbe]::SetDllDirectory($dllDirectory)
$module = [WynimeWindowsNativeProvenanceProbe]::LoadLibrary($resolvedDllPath)
if ($module -eq [IntPtr]::Zero) {
    Fail-Provenance "LoadLibrary failed with Win32 error $([Runtime.InteropServices.Marshal]::GetLastWin32Error())."
}

function Get-NativeProc([string]$Name, [Type]$DelegateType) {
    $address = [WynimeWindowsNativeProvenanceProbe]::GetProcAddress($module, $Name)
    if ($address -eq [IntPtr]::Zero) {
        Fail-Provenance "native export is missing: $Name"
    }
    return [Runtime.InteropServices.Marshal]::GetDelegateForFunctionPointer($address, $DelegateType)
}

$create = Get-NativeProc 'mpv_create' ([WynimeWindowsNativeProvenanceProbe+Create])
$initialize = Get-NativeProc 'mpv_initialize' ([WynimeWindowsNativeProvenanceProbe+Initialize])
$getPropertyString = Get-NativeProc 'mpv_get_property_string' ([WynimeWindowsNativeProvenanceProbe+GetPropertyString])
$terminateDestroy = Get-NativeProc 'mpv_terminate_destroy' ([WynimeWindowsNativeProvenanceProbe+TerminateDestroy])
$context = $create.Invoke()
if ($context -eq [IntPtr]::Zero) {
    Fail-Provenance 'mpv_create returned a null context.'
}
try {
    $initializeResult = $initialize.Invoke($context)
    if ($initializeResult -ne 0) {
        Fail-Provenance "mpv_initialize returned $initializeResult."
    }
    $runtime = @{}
    foreach ($property in @('mpv-version', 'ffmpeg-version', 'mpv-configuration')) {
        $pointer = $getPropertyString.Invoke($context, $property)
        if ($pointer -eq [IntPtr]::Zero) {
            Fail-Provenance "runtime property is missing: $property"
        }
        $runtime[$property] = [Runtime.InteropServices.Marshal]::PtrToStringAnsi($pointer)
    }
}
finally {
    $terminateDestroy.Invoke($context)
}

if ($runtime['mpv-version'] -ne $lock.runtime.mpvVersion) {
    Fail-Provenance "runtime mpv version $($runtime['mpv-version']) does not match locked version $($lock.runtime.mpvVersion)."
}
if ($runtime['ffmpeg-version'] -ne $lock.runtime.ffmpegVersion) {
    Fail-Provenance "runtime FFmpeg version $($runtime['ffmpeg-version']) does not match locked version $($lock.runtime.ffmpegVersion)."
}
if ($runtime['mpv-configuration'] -ne $configuration) {
    Fail-Provenance 'embedded and runtime mpv configuration strings differ.'
}

Write-Output 'WINDOWS_NATIVE_PROVENANCE=PASS'
Write-Output "dll=$resolvedDllPath"
Write-Output "dllSha256=$dllHash"
Write-Output "mpvVersion=$($runtime['mpv-version'])"
Write-Output "ffmpegVersion=$($runtime['ffmpeg-version'])"
Write-Output "configurationSha256=$configurationHash"
