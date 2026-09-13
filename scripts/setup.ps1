$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (Get-Command java -ErrorAction SilentlyContinue) {
    & java (Join-Path $PSScriptRoot 'GenerateSecrets.java') $projectRoot
} else {
    & docker run --rm -v "${projectRoot}:/setup" -w /setup eclipse-temurin:21-jdk java scripts/GenerateSecrets.java /setup
}
if ($LASTEXITCODE -ne 0) { throw 'Secret generation failed.' }
