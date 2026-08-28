# Push variables from deploy/railway-dryrun.env to the linked Railway service.
# Requires: railway CLI logged in and project linked (railway link).
# Usage: .\deploy\set-railway-vars.ps1

$ErrorActionPreference = "Stop"
$envFile = Join-Path $PSScriptRoot "railway-dryrun.env"

if (-not (Test-Path $envFile)) {
    Write-Error "Missing $envFile — copy deploy/railway-dryrun.env.example and fill from application.properties"
}

railway --version | Out-Null

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }

    $eq = $line.IndexOf("=")
    if ($eq -lt 1) { return }

    $name = $line.Substring(0, $eq).Trim()
    $value = $line.Substring($eq + 1)

    Write-Host "Setting $name"
    railway variables set "${name}=${value}"
}

Write-Host "Done. Redeploy if the service is already running: railway up"
