  
         
                                                                                      

            
                                                                      
                                                                                    
                                                                             
                                                                               

                    
                                                         

                     
                                                             

        
                                                  

        
                                                             
  

param (
    [Parameter(Mandatory = $true)]
    [string]$InputPath,

    [Parameter(Mandatory = $true)]
    [string]$TestString
)

Write-Host "=== Wynime Test Runner ==="

                                     
if (!(Test-Path -Path $InputPath)) {
    Write-Error "Error: The path '$InputPath' does not exist."
    exit 1
}

                                                                         
$aniSearchRoot = ""

if (Test-Path -Path $InputPath -PathType Container) {
                      
    Write-Host "Detected a directory. Will search directly in '$InputPath'."
    $aniSearchRoot = $InputPath
}
else {
                                   
    $extension = [System.IO.Path]::GetExtension($InputPath).ToLower()

    if ($extension -eq ".zip") {
        Write-Host "Detected a .zip file. Will unzip and then search for Wynime.exe."

                                      
        if (Test-Path "extracted_zip") {
            Write-Host "Removing old 'extracted_zip' folder..."
            Remove-Item "extracted_zip" -Recurse -Force
        }

        Write-Host "Unzipping '$InputPath' to 'extracted_zip'..."
        Expand-Archive -LiteralPath $InputPath -DestinationPath "extracted_zip" -Force
                                                                                       
                                           

        $aniSearchRoot = "extracted_zip"

    }
    else {
        Write-Error "Error: '$InputPath' is not a directory or a .zip file."
        exit 1
    }
}

                                       
Write-Host "Searching for 'Wynime.exe' under '$aniSearchRoot'..."
$aniExe = Get-ChildItem -Path $aniSearchRoot -Filter "wynime.exe" -Recurse -Force | Select-Object -First 1

if (-not $aniExe) {
    Write-Error "Error: Wynime.exe not found in '$aniSearchRoot'."
    exit 1
}

Write-Host "Found Wynime.exe at: $($aniExe.FullName)"

                                                           
Write-Host "Setting WYNIME_DESKTOP_TEST_TASK to '$TestString'..."
$env:WYNIME_DESKTOP_TEST_TASK = $TestString
$env:ANI_DISALLOW_PROJECT_DIRECTORIES_FALLBACK = "true"

Write-Host "Running Wynime.exe..."
$process = Start-Process -FilePath $aniExe.FullName -Wait -PassThru

if ($process.ExitCode -ne 0) {
    Write-Error "wynime.exe exited with code $($process.ExitCode)."
    exit $process.ExitCode
}

Write-Host "Success: Wynime.exe exited with code 0."
exit 0
