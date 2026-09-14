param([switch]$TrustLocalCertificate, [string]$BuildCaFile = $env:BUILD_CA_FILE)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $composeFiles = @('-f', 'compose.yaml')
    if (-not $BuildCaFile -and (Test-Path -LiteralPath './secrets/build-ca.pem')) {
        $BuildCaFile = (Resolve-Path -LiteralPath './secrets/build-ca.pem').Path
    }
    if ($BuildCaFile) {
        $env:BUILD_CA_FILE = (Resolve-Path -LiteralPath $BuildCaFile).Path
        $composeFiles += @('-f', 'compose.build-ca.yaml')
    }
    & "$PSScriptRoot\setup.ps1"
    if ($LASTEXITCODE -ne 0) { throw 'Setup failed.' }
    $localSettings = @{}
    foreach ($line in Get-Content -LiteralPath .env) {
        if ($line -match '^(APP_ORIGIN|HTTPS_PORT)=(.*)$') { $localSettings[$matches[1]] = $matches[2].Trim() }
    }
    $httpsPort = if ($localSettings['HTTPS_PORT']) { $localSettings['HTTPS_PORT'] } else { '8443' }
    $appUrl = "https://localhost:$httpsPort"
    if ($localSettings['APP_ORIGIN'] -and $localSettings['APP_ORIGIN'] -ne $appUrl) {
        throw "For local startup, APP_ORIGIN must equal $appUrl. Public deployment uses compose.production.yaml."
    }
    & docker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop in Linux-container mode, then retry.' }
    & docker compose @composeFiles up --build --wait --wait-timeout 300
    if ($LASTEXITCODE -ne 0) { throw 'Stack did not become healthy. Check docker compose logs.' }
    $certificateReady = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        & docker compose @composeFiles exec -T gateway test -f /data/caddy/pki/authorities/local/root.crt
        if ($LASTEXITCODE -eq 0) { $certificateReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $certificateReady) { throw 'The gateway has not issued its local certificate yet.' }
    & docker compose @composeFiles cp gateway:/data/caddy/pki/authorities/local/root.crt ./secrets/local-ca.crt
    if ($LASTEXITCODE -ne 0) { throw 'Could not export the local gateway certificate.' }
    if ($TrustLocalCertificate) {
        $certificate = [Security.Cryptography.X509Certificates.X509Certificate2]::new((Join-Path $projectRoot 'secrets\local-ca.crt'))
        if ($certificate.NotAfter -le (Get-Date) -or $certificate.NotBefore -gt (Get-Date)) { throw 'The exported CA is outside its validity dates.' }
        $caConstraint = $certificate.Extensions | Where-Object { $_.Oid.Value -eq '2.5.29.19' }
        if (-not $caConstraint.CertificateAuthority) { throw 'The exported certificate is not a CA.' }
        $null = Import-Certificate -FilePath ./secrets/local-ca.crt -CertStoreLocation Cert:\CurrentUser\Root
        Write-Host "Trusted the local gateway CA for your Windows account ($($certificate.Thumbprint))."
        $response = Invoke-WebRequest -Uri "$appUrl/login" -UseBasicParsing -TimeoutSec 20
        if ($response.StatusCode -ne 200) { throw 'HTTPS verification failed.' }
    }
    Write-Host "Neo4flix is ready at $appUrl"
    if (-not $TrustLocalCertificate) {
        Write-Host 'If your browser does not trust local HTTPS yet, rerun with -TrustLocalCertificate.'
    }
} finally { Pop-Location }
