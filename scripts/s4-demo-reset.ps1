$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$composeFile = Join-Path $repoRoot 'docker-compose.yml'
$projectName = 'foodlab-s4-demo'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI is required to reset the S4 demo environment.'
}
docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Engine is not available. Start Docker Desktop, then rerun this script.'
}

# Bind only to the local machine. Callers may choose other numeric ports, but
# cannot accidentally expose the development-identity API or demo database.
function Resolve-LocalPort([string]$name, [string]$defaultValue) {
    $configured = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrWhiteSpace($configured)) { $configured = $defaultValue }
    if ($configured -notmatch '^\d{1,5}$' -or [int]$configured -lt 1 -or [int]$configured -gt 65535) {
        throw "$name must be a numeric TCP port between 1 and 65535."
    }
    return [int]$configured
}
$mysqlPort = Resolve-LocalPort 'MYSQL_HOST_PORT' '13309'
$backendPort = Resolve-LocalPort 'BACKEND_HOST_PORT' '18082'
$frontendPort = Resolve-LocalPort 'FRONTEND_HOST_PORT' '15175'
$env:MYSQL_HOST_PORT = "127.0.0.1:$mysqlPort"
$env:BACKEND_HOST_PORT = "127.0.0.1:$backendPort"
$env:FRONTEND_HOST_PORT = "127.0.0.1:$frontendPort"

Push-Location $repoRoot
try {
    docker compose --project-name $projectName --project-directory $repoRoot --file $composeFile down --volumes --remove-orphans
    if ($LASTEXITCODE -ne 0) { throw 'Could not remove the isolated S4 demo project.' }

    docker compose --project-name $projectName --project-directory $repoRoot --file $composeFile up --detach --build --wait
    if ($LASTEXITCODE -ne 0) { throw 'The S4 demo project did not become healthy.' }

    Write-Host "S4 demo is ready at http://127.0.0.1:$frontendPort."
    Write-Host "Backend health: http://127.0.0.1:$backendPort/api/health"
    Write-Host "The reset used only Compose project '$projectName', loopback ports and its project-scoped volumes."
} finally {
    Pop-Location
}
