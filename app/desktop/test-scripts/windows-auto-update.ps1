                           
$env:WYNIME_DESKTOP_TEST_TASK = "download-update-and-install"
$env:WYNIME_DESKTOP_TEST_ARGC = "1"
$env:WYNIME_DESKTOP_TEST_ARGV_0 = "https://github.com/william12233/Wynime/releases/download/0.1/wynime-0.1-windows-x86_64.zip"

                    

Write-Host "Running Wynime.exe..."
$process = Start-Process -FilePath ".\Wynime.exe" -WindowStyle Hidden -Wait -PassThru

if ($process.ExitCode -ne 0)
{
    Write-Error "wynime.exe exited with code $( $process.ExitCode )."
    exit $process.ExitCode
}

Write-Host "Success: Wynime.exe exited with code 0."
