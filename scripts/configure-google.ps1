param([string]$ClientId)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) { throw 'Run scripts/setup.ps1 first.' }
Write-Host 'Step 1 of 2: Copy the Google Client ID ending in .apps.googleusercontent.com.'
Write-Host 'Both prompts hide pasted text. Press Enter after each value.'
if (-not $ClientId) {
    $secureId = Read-Host 'Google Client ID (hidden)' -AsSecureString
    $idPtr = [IntPtr]::Zero
    try {
        $idPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureId)
        $ClientId = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($idPtr)
    } finally {
        if ($idPtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($idPtr) }
        $secureId.Dispose()
    }
}
$ClientId = $ClientId.Trim()
if ($ClientId -notmatch '^[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$') {
    throw 'Enter the Web application client ID ending in .apps.googleusercontent.com.'
}
Write-Host 'Step 2 of 2: Copy the Client secret for that same Web application client.'
$secureSecret = Read-Host 'Google client secret (hidden)' -AsSecureString
$secretPtr = [IntPtr]::Zero
try {
    $secretPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSecret)
    $clientSecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPtr)
    if ($clientSecret -notmatch '^[A-Za-z0-9_-]{10,256}$') { throw 'The client secret has an unexpected format.' }
    $lines = @(Get-Content -LiteralPath $envPath | Where-Object { $_ -notmatch '^\s*GOOGLE_CLIENT_(ID|SECRET)\s*=' })
    $lines += "GOOGLE_CLIENT_ID=$ClientId"
    $lines += "GOOGLE_CLIENT_SECRET=$clientSecret"
    [IO.File]::WriteAllLines($envPath, $lines, [Text.UTF8Encoding]::new($false))
    $originLine = $lines | Where-Object { $_ -match '^APP_ORIGIN=' } | Select-Object -First 1
    $appOrigin = if ($originLine) { $originLine.Substring('APP_ORIGIN='.Length).Trim().Trim('"', "'") } else { 'https://localhost:8443' }
    Write-Host "Saved the Google client in the private .env. Authorized redirect URI: $appOrigin/api/auth/oauth2/callback/google"
    Write-Host 'Apply with: docker compose up -d --no-deps --wait user-service'
} finally {
    if ($secretPtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPtr) }
    $clientSecret = $null
    $secureSecret.Dispose()
}
