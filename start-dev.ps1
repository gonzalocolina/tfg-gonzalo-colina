# ============================================================
# start-dev.ps1 - Lanza el entorno de desarrollo local
#   - Backend: Spring Boot en http://localhost:8080
#   - Frontend unificado: Portal + Organizer + Player en http://localhost:5175
#   - Base de datos: H2 embebida (se inicia con el backend)
#                    Consola H2: http://localhost:8080/h2-console
# ============================================================

$root = $PSScriptRoot
$envFile = Join-Path $root ".env"

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        Write-Host "No se encontró '$Path'. Copia .env.example a .env antes de arrancar." -ForegroundColor Red
        exit 1
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
        Set-Item -Path "Env:$name" -Value $value
    }
}

Import-DotEnv -Path $envFile

$seedOrganizerPassword = $env:APP_SEED_ORGANIZER_PASSWORD
if ([string]::IsNullOrWhiteSpace($seedOrganizerPassword)) {
    Write-Host "APP_SEED_ORGANIZER_PASSWORD no está definido en .env." -ForegroundColor Red
    exit 1
}

if ([string]::IsNullOrWhiteSpace($env:APP_JWT_SECRET)) {
    Write-Host "APP_JWT_SECRET no está definido en .env." -ForegroundColor Red
    exit 1
}

# Limpia compilados previos del backend
$backendTarget = Join-Path $root "backend\target"
if (Test-Path $backendTarget) {
    Write-Host "Limpiando carpeta backend/target..." -ForegroundColor Yellow
    Remove-Item $backendTarget -Recurse -Force -ErrorAction SilentlyContinue
}

# --- Backend ---
Write-Host "Iniciando backend..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command",
    "Set-Location '$root\backend'; `$env:APP_SEED_ORGANIZER_PASSWORD='$seedOrganizerPassword'; `$env:APP_JWT_SECRET='$($env:APP_JWT_SECRET)'; .\mvnw.cmd clean spring-boot:run" `
    -WindowStyle Normal

# Espera a que el backend esté listo antes de lanzar el frontend
Write-Host "Esperando a que el backend arranque (puerto 8080)..." -ForegroundColor Yellow
$maxWait = 90
$elapsed = 0
do {
    Start-Sleep -Seconds 2
    $elapsed += 2
    $up = $null
    try { $up = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -ErrorAction SilentlyContinue } catch {}
} while ($up.status -ne "UP" -and $elapsed -lt $maxWait)

if ($up.status -eq "UP") {
    Write-Host "Backend listo." -ForegroundColor Green
} else {
    Write-Host "El backend tardó más de ${maxWait}s. Lanzando el frontend de todas formas..." -ForegroundColor Yellow
}

# --- Frontend unificado ---
Write-Host "Iniciando frontend unificado..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command",
    "npm --prefix '$root\frontend' run dev" `
    -WindowStyle Normal

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host " Entorno de desarrollo iniciado" -ForegroundColor Green
Write-Host "  Backend:   http://localhost:8080" -ForegroundColor White
Write-Host "  Frontend unificado: http://localhost:5175" -ForegroundColor White
Write-Host "  Portal: http://localhost:5175" -ForegroundColor White
Write-Host "  Organizer: http://localhost:5175/organizer/login" -ForegroundColor White
Write-Host "  Player: http://localhost:5175/player/join" -ForegroundColor White
Write-Host "  H2 console: http://localhost:8080/h2-console" -ForegroundColor White
Write-Host "  Usuario:   organizer / organizer" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Green
