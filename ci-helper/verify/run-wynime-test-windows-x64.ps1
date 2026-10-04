<#
.SYNOPSIS
  Locate and run Wynime.exe either from a directory or by first unzipping a .zip file.

.DESCRIPTION
  1. If the path is a directory, search that directory for Wynime.exe.
  2. If the path is a .zip file, unzip to extracted_zip, then search for Wynime.exe.
  3. Set ANIMEKO_DESKTOP_TEST_TASK to the second argument and run Wynime.exe.
  4. If Wynime.exe exits non-zero, exit with that code. Otherwise, exit with 0.

.PARAMETER InputPath
  A directory or a .zip file where Wynime.exe is located.

.PARAMETER TestString
  The test identifier to assign to ANIMEKO_DESKTOP_TEST_TASK.

.EXAMPLE
  PS> .\run-wynime-test.ps1 "C:\my\dir" "TEST_ABC"

.EXAMPLE
  PS> .\run-wynime-test.ps1 "C:\downloads\ani.zip" "TEST_XYZ"
#>

param (
    [Parameter(Mandatory = $true)]
    [string]$InputPath,

    [Parameter(Mandatory = $true)]
    [string]$TestString
)

Write-Host "=== Wynime Test Runner ==="

# --- Step 1: Validate input path ---
if (!(Test-Path -Path $InputPath)) {
    Write-Error "Error: The path '$InputPath' does not exist."
    exit 1
}

# --- Step 2: If directory, search there; if .zip, unzip, then search ---
$aniSearchRoot = ""

if (Test-Path -Path $InputPath -PathType Container) {
    # It's a directory
    Write-Host "Detected a directory. Will search directly in '$InputPath'."
    $aniSearchRoot = $InputPath
}
else {
    # It's a file - check extension
    $extension = [System.IO.Path]::GetExtension($InputPath).ToLower()

    if ($extension -eq ".zip") {
        Write-Host "Detected a .zip file. Will unzip and then search for Wynime.exe."

        # Cleanup old extracted folder
        if (Test-Path "extracted_zip") {
            Write-Host "Removing old 'extracted_zip' folder..."
            Remove-Item "extracted_zip" -Recurse -Force
        }

        Write-Host "Unzipping '$InputPath' to 'extracted_zip'..."
        Expand-Archive -LiteralPath $InputPath -DestinationPath "extracted_zip" -Force
        # If older PowerShell, use 7z (uncomment below and comment out Expand-Archive):
        # & 7z x $InputPath -oextracted_zip

        $aniSearchRoot = "extracted_zip"

    }
    else {
        Write-Error "Error: '$InputPath' is not a directory or a .zip file."
        exit 1
    }
}

# --- Step 3: Search for Wynime.exe ---
Write-Host "Searching for 'Wynime.exe' under '$aniSearchRoot'..."
$aniExe = Get-ChildItem -Path $aniSearchRoot -Filter "Wynime.exe" -Recurse -Force | Select-Object -First 1

if (-not $aniExe) {
    Write-Error "Error: Wynime.exe not found in '$aniSearchRoot'."
    exit 1
}

Write-Host "Found Wynime.exe at: $($aniExe.FullName)"

# --- Step 4: Set environment variable & run Wynime.exe ---
Write-Host "Setting ANIMEKO_DESKTOP_TEST_TASK to '$TestString'..."
$env:ANIMEKO_DESKTOP_TEST_TASK = $TestString
$env:ANI_DISALLOW_PROJECT_DIRECTORIES_FALLBACK = "true"

Write-Host "Running Wynime.exe..."
$process = Start-Process -FilePath $aniExe.FullName -Wait -PassThru

if ($process.ExitCode -ne 0) {
    Write-Error "Wynime.exe exited with code $($process.ExitCode)."
    exit $process.ExitCode
}

Write-Host "Success: Wynime.exe exited with code 0."
exit 0
