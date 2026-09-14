param([string]$ClientId)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) { throw 'Run scripts/setup.ps1 first.' }
if (-not $ClientId) { $ClientId = Read-Host 'GitHub OAuth App client ID' }
$ClientId = $ClientId.Trim()
if ($ClientId -notmatch '^[A-Za-z0-9._-]{10,100}$') {
    throw 'Enter the client ID from your GitHub OAuth App settings.'
}
$secureSecret = Read-Host 'GitHub OAuth client secret (hidden)' -AsSecureString
$secretPtr = [IntPtr]::Zero
try {
    $secretPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSecret)
    $clientSecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPtr)
    if ($clientSecret -notmatch '^[A-Za-z0-9_-]{10,256}$') { throw 'The client secret has an unexpected format.' }
    $lines = @(Get-Content -LiteralPath $envPath | Where-Object { $_ -notmatch '^\s*GITHUB_CLIENT_(ID|SECRET)\s*=' })
    $lines += "GITHUB_CLIENT_ID=$ClientId"
    $lines += "GITHUB_CLIENT_SECRET=$clientSecret"
    [IO.File]::WriteAllLines($envPath, $lines, [Text.UTF8Encoding]::new($false))
    $originLine = $lines | Where-Object { $_ -match '^APP_ORIGIN=' } | Select-Object -First 1
    $appOrigin = if ($originLine) { $originLine.Substring('APP_ORIGIN='.Length).Trim().Trim('"', "'") } else { 'https://localhost:8443' }
    Write-Host "Saved the GitHub client in the private .env. Authorized redirect URI: $appOrigin/api/auth/oauth2/callback/github"
    Write-Host 'Apply with: docker compose up -d --no-deps user-service'
} finally {
    if ($secretPtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPtr) }
    $clientSecret = $null
    $secureSecret.Dispose()
}
