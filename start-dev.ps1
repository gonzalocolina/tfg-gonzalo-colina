# ============================================================
# start-dev.ps1 - Lanza el entorno de desarrollo local
#   - Backend: Spring Boot en http://localhost:8080
#   - Frontend Organizer: Vite dev server en http://localhost:5173
#   - Frontend Player: Vite dev server en http://localhost:5174
#   - Base de datos: H2 embebida (se inicia con el backend)
#                    Consola H2: http://localhost:8080/h2-console
# ============================================================

$root = $PSScriptRoot

# Limpia compilados previos del backend
$backendTarget = Join-Path $root "backend\target"
if (Test-Path $backendTarget) {
    Write-Host "Limpiando carpeta backend/target..." -ForegroundColor Yellow
    Remove-Item $backendTarget -Recurse -Force -ErrorAction SilentlyContinue
}

# --- Backend ---
Write-Host "Iniciando backend..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command",
    "Set-Location '$root\backend'; .\mvnw.cmd clean spring-boot:run" `
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

# --- Frontend Organizer ---
Write-Host "Iniciando frontend organizer..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command",
    "Set-Location '$root\frontend-organizer'; npm run dev" `
    -WindowStyle Normal

# --- Frontend Player ---
Write-Host "Iniciando frontend player..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command",
    "Set-Location '$root\frontend-player'; npm run dev" `
    -WindowStyle Normal

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host " Entorno de desarrollo iniciado" -ForegroundColor Green
Write-Host "  Backend:   http://localhost:8080" -ForegroundColor White
Write-Host "  Frontend organizer:  http://localhost:5173" -ForegroundColor White
Write-Host "  Frontend player: http://localhost:5174" -ForegroundColor White
Write-Host "  H2 console: http://localhost:8080/h2-console" -ForegroundColor White
Write-Host "  Usuario:   organizer / organizer" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Green
