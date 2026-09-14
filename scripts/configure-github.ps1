param([string]$ClientId)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) { throw 'Run scripts/setup.ps1 first.' }
Write-Host 'Step 1 of 2: Copy the value under Client ID on GitHub, above Client secrets.'
Write-Host 'Both prompts hide pasted text. Press Enter after each value.'
if (-not $ClientId) {
    $secureId = Read-Host 'GitHub Client ID (hidden)' -AsSecureString
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
if ($ClientId -match '^[A-Fa-f0-9]{40,64}$') {
    throw 'That looks like a client secret, not a Client ID. Nothing was saved. Run this script again and copy the value under Client ID, above Client secrets on GitHub.'
}
if ($ClientId -notmatch '^[A-Za-z0-9._-]{10,100}$') {
    throw 'Enter the client ID from your GitHub OAuth App settings.'
}
Write-Host 'Step 2 of 2: Copy the newly generated value under Client secrets.'
$secureSecret = Read-Host 'GitHub client secret (hidden)' -AsSecureString
$secretPtr = [IntPtr]::Zero
try {
    $secretPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSecret)
    $clientSecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPtr)
    if ($clientSecret -notmatch '^[A-Za-z0-9_-]{10,256}$') { throw 'The client secret has an unexpected format.' }
    if ($clientSecret -ceq $ClientId) { throw 'The Client ID and client secret must be different. Nothing was saved.' }
    $lines = @(Get-Content -LiteralPath $envPath | Where-Object { $_ -notmatch '^\s*GITHUB_CLIENT_(ID|SECRET)\s*=' })
    $lines += "GITHUB_CLIENT_ID=$ClientId"
    $lines += "GITHUB_CLIENT_SECRET=$clientSecret"
    [IO.File]::WriteAllLines($envPath, $lines, [Text.UTF8Encoding]::new($false))
    $originLine = $lines | Where-Object { $_ -match '^APP_ORIGIN=' } | Select-Object -First 1
    $appOrigin = if ($originLine) { $originLine.Substring('APP_ORIGIN='.Length).Trim().Trim('"', "'") } else { 'https://localhost:8443' }
    Write-Host "Saved the GitHub client in the private .env. Authorized redirect URI: $appOrigin/api/auth/oauth2/callback/github"
    Write-Host 'Apply with: docker compose up -d --no-deps --wait user-service'
} finally {
    if ($secretPtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPtr) }
    $clientSecret = $null
    $secureSecret.Dispose()
}
