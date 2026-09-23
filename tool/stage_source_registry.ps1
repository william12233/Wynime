[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string]$DeviceId,

  [string]$ApplicationId = 'io.github.william12233.wynime',

  [string]$LocalRegistryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

$adbCommand = Get-Command adb -ErrorAction Stop
$indexFile = Join-Path $LocalRegistryRoot 'sources/index.json'
if (-not (Test-Path -LiteralPath $indexFile -PathType Leaf)) {
  throw "Registry index was not found: $indexFile"
}

$index = Get-Content -Raw -LiteralPath $indexFile | ConvertFrom-Json
if ($index.schemaVersion -ne 1 -or [string]::IsNullOrWhiteSpace($index.sourceRoot)) {
  throw 'The staged registry index must use schema version 1 and a sourceRoot.'
}

function Assert-SafeRelativePath([string]$Value) {
  $segments = $Value.Split('/')
  $unsafeSegments = @($segments | Where-Object {
      $_ -eq '' -or $_ -eq '.' -or $_ -eq '..'
    })
  if ([string]::IsNullOrWhiteSpace($Value) -or
      $Value.StartsWith('/') -or
      $Value.Contains('\') -or
      $unsafeSegments.Count -gt 0) {
    throw "Unsafe staged registry relative path: $Value"
  }
}

Assert-SafeRelativePath $index.sourceRoot
$files = @([pscustomobject]@{ Local = $indexFile; Relative = 'sources/index.json' })
foreach ($entry in @($index.packages)) {
  Assert-SafeRelativePath $entry.path
  $relative = "$($index.sourceRoot)/$($entry.path)"
  $local = Join-Path $LocalRegistryRoot ($relative -replace '/', '\')
  if (-not (Test-Path -LiteralPath $local -PathType Leaf)) {
    throw "Indexed package artifact was not found: $local"
  }
  $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $local).Hash.ToLowerInvariant()
  if ($actualHash -ne ([string]$entry.sha256).ToLowerInvariant()) {
    throw "SHA-256 mismatch for indexed artifact: $relative"
  }
  $files += [pscustomobject]@{ Local = $local; Relative = $relative }
}

function Invoke-AdbChecked([string[]]$Arguments) {
  & $adbCommand.Source @('-s', $DeviceId) @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "adb failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
  }
}

Invoke-AdbChecked @('shell', 'run-as', $ApplicationId, 'mkdir', '-p', 'files/source-registry/sources')
foreach ($file in $files) {
  $relative = $file.Relative.Replace('/', '/')
  $targetDirectory = Split-Path -Parent $relative
  if ([string]::IsNullOrWhiteSpace($targetDirectory)) {
    $targetDirectory = '.'
  }
  Invoke-AdbChecked @(
    'shell', 'run-as', $ApplicationId, 'mkdir', '-p',
    "files/source-registry/$targetDirectory"
  )

  $temporaryRemote = "/data/local/tmp/wynime-registry-$([Guid]::NewGuid().ToString('N')).json"
  try {
    Invoke-AdbChecked @('push', $file.Local, $temporaryRemote)
    Invoke-AdbChecked @(
      'shell', 'run-as', $ApplicationId, 'cp', $temporaryRemote,
      "files/source-registry/$relative"
    )
  } finally {
    & $adbCommand.Source @('-s', $DeviceId, 'shell', 'rm', '-f', $temporaryRemote) | Out-Null
  }
}

Write-Output "Staged $($files.Count) verified registry files under app-support/source-registry."
