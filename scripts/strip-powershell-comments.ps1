param([switch]$Check)
$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$selectedFiles = & git -C $repositoryRoot -c core.quotePath=false ls-files --cached --others --exclude-standard
$fileCount = 0
$commentCount = 0
foreach ($relative in $selectedFiles | Sort-Object -Unique) {
    if ($relative -notmatch '\.ps1$' -or $relative -match '(^|/)(\.tmp[^/]*|\.agents|\.claude|licenses|build|node_modules)/') { continue }
    $target = Join-Path $repositoryRoot $relative
    if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { continue }
    $original = [IO.File]::ReadAllText($target)
    $tokens = $null
    $parseErrors = $null
    [System.Management.Automation.Language.Parser]::ParseInput($original, [ref]$tokens, [ref]$parseErrors) > $null
    if ($parseErrors.Count) { throw "Parse errors: $relative" }
    $comments = @($tokens | Where-Object Kind -eq Comment)
    $result = $original
    foreach ($token in $comments | Sort-Object { $_.Extent.StartOffset } -Descending) {
        if ($token.Extent.StartOffset -eq 0 -and $token.Text.StartsWith('#!')) { continue }
        $start = $token.Extent.StartOffset
        $end = $token.Extent.EndOffset
        $result = $result.Substring(0, $start) + ($token.Text -replace '[^\r\n]', ' ') + $result.Substring($end)
        $commentCount++
    }
    $afterTokens = $null
    [System.Management.Automation.Language.Parser]::ParseInput($result, [ref]$afterTokens, [ref]$parseErrors) > $null
    if ($parseErrors.Count) { throw "Parse errors after cleanup: $relative" }
    $beforeSignature = @($tokens | Where-Object { $_.Kind -notin @('Comment', 'NewLine') } | ForEach-Object { "$($_.Kind):$($_.Text)" })
    $afterSignature = @($afterTokens | Where-Object { $_.Kind -notin @('Comment', 'NewLine') } | ForEach-Object { "$($_.Kind):$($_.Text)" })
    if (Compare-Object $beforeSignature $afterSignature -SyncWindow 0) { throw "Token change: $relative" }
    $notices = @($comments | Where-Object Text -match 'Copyright|SPDX-License-Identifier' | ForEach-Object Text)
    if (-not $Check -and $notices.Count) {
        $notice = Join-Path $repositoryRoot "licenses/source-notices/$relative.license"
        New-Item -ItemType Directory -Force -Path (Split-Path $notice) > $null
        if (-not (Test-Path -LiteralPath $notice)) { [IO.File]::WriteAllText($notice, ($notices -join "`n`n") + "`n") }
    }
    if (-not $Check -and $result -ne $original) { [IO.File]::WriteAllText($target, $result) }
    $fileCount++
}
Write-Output "PowerShell: files=$fileCount comments=$commentCount non-comment token equality=PASS"
if ($Check -and $commentCount) { exit 1 }
