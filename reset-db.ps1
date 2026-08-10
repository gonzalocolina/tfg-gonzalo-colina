# ============================================================
# reset-db.ps1 - Borra la base de datos local de desarrollo
#   - Elimina los ficheros H2 de APP_DATA_DIR (por defecto %LOCALAPPDATA%\mc-tfg)
#   - Conserva otros recursos del directorio, como inter-pod-cards
# ============================================================

param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$root = $PSScriptRoot

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return
    }

    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith('#')) {
            return
        }

        $separatorIndex = $line.IndexOf('=')
        if ($separatorIndex -lt 1) {
            return
        }

        $name = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1).Trim()
        if ($value.StartsWith('"') -and $value.EndsWith('"')) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

function Resolve-AppDataDir {
    if (-not [string]::IsNullOrWhiteSpace($env:APP_DATA_DIR)) {
        return $env:APP_DATA_DIR
    }
    return Join-Path $env:LOCALAPPDATA "mc-tfg"
}

$envFile = Join-Path $root ".env"
Import-DotEnv -Path $envFile

$appDataDir = Resolve-AppDataDir
$dbBasePath = Join-Path $appDataDir "mc_database"
$dbFiles = @(
    "$dbBasePath.mv.db",
    "$dbBasePath.trace.db",
    "$dbBasePath.lock.db"
)

$backendRunning = $false
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 2 -ErrorAction Stop
    $backendRunning = $health.status -eq "UP"
} catch {
    $backendRunning = $false
}

if ($backendRunning) {
    Write-Host "El backend parece estar en ejecución en http://localhost:8080." -ForegroundColor Yellow
    Write-Host "Detén el backend antes de borrar la base de datos para evitar archivos bloqueados." -ForegroundColor Red
    exit 1
}

$existingDbFiles = @($dbFiles | Where-Object { Test-Path $_ })

if ($existingDbFiles.Count -eq 0) {
    Write-Host "No se han encontrado archivos de base de datos en '$dbBasePath*'." -ForegroundColor Yellow
    exit 0
}

if (-not $Force) {
    Write-Host "Se van a eliminar estos archivos de la base de datos:" -ForegroundColor Yellow
    $existingDbFiles | ForEach-Object { Write-Host " - $_" -ForegroundColor Yellow }

    $confirmation = Read-Host "Escribe BORRAR para continuar"
    if ($confirmation -cne "BORRAR") {
        Write-Host "Operación cancelada. La base de datos sigue intacta." -ForegroundColor Cyan
        exit 0
    }
}

foreach ($dbFile in $existingDbFiles) {
    Remove-Item $dbFile -Force
    Write-Host "Eliminado: $dbFile" -ForegroundColor Green
}

Write-Host "" 
Write-Host "Base de datos eliminada correctamente." -ForegroundColor Green
Write-Host "La próxima vez que arranques el backend, H2 la recreará desde cero." -ForegroundColor Cyan