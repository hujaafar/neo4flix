$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) { throw 'Run scripts/setup.ps1 first.' }
Write-Host 'Copy the API Read Access Token from https://www.themoviedb.org/settings/api.'
Write-Host 'Use the long read access token, not the shorter API key. Pasted text is hidden.'
$secureToken = Read-Host 'TMDB API Read Access Token (hidden)' -AsSecureString
$tokenPtr = [IntPtr]::Zero
try {
    $tokenPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureToken)
    $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($tokenPtr).Trim()
    if ($token -notmatch '^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$' -or $token.Length -gt 4096) {
        throw 'Enter the long TMDB API Read Access Token. It has three sections separated by dots.'
    }
    $lines = @(Get-Content -LiteralPath $envPath | Where-Object { $_ -notmatch '^\s*TMDB_READ_ACCESS_TOKEN\s*=' })
    $lines += "TMDB_READ_ACCESS_TOKEN=$token"
    [IO.File]::WriteAllLines($envPath, $lines, [Text.UTF8Encoding]::new($false))
    Write-Host 'Saved the TMDB token in the private .env. No films have been removed.'
    Write-Host 'After building the new version: docker compose up -d --no-deps --wait movie-service'
    Write-Host 'Then sign in as administrator and open Manage films to preview the TMDB catalogue.'
} finally {
    if ($tokenPtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($tokenPtr) }
    $token = $null
    $secureToken.Dispose()
}
