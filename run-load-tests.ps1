[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$Scenario = "smoke",
    [switch]$NoReset,
    [switch]$NoInstall,
    [switch]$KeepBackendWhenDone,
    [switch]$StopBackendWhenDone,
    [int]$BackendWaitSeconds = 120,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ScenarioArgs
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = $PSScriptRoot
$loadTestsDir = Join-Path $root "load-tests"
$backendDir = Join-Path $root "backend"
$resultsDir = Join-Path $loadTestsDir "results"
$tmpDir = Join-Path $loadTestsDir "tmp"
$backendStateFile = Join-Path $tmpDir "managed-backend.json"
$seedOrganizerPassword = "organizer"

$script:CurrentStep = 0
$script:TotalSteps = 0
$script:BackendStartedByScript = $false
$script:BackendProcess = $null
$script:BackendLogFile = $null
$script:LastNativeExitCode = 0

$ScenarioArgs = @($ScenarioArgs)

if ($KeepBackendWhenDone -and $StopBackendWhenDone) {
    throw "Los parámetros -KeepBackendWhenDone y -StopBackendWhenDone son incompatibles entre sí."
}

$cleanupBackendWhenDone = -not $KeepBackendWhenDone

function Format-DisplayArgument {
    param([AllowNull()][string]$Argument)

    if ($null -eq $Argument) {
        return '""'
    }

    if ($Argument -match '[\s"]') {
        return '"' + ($Argument -replace '"', '\\"') + '"'
    }

    return $Argument
}

function Format-DisplayCommand {
    param(
        [string]$Executable,
        [string[]]$Arguments
    )

    return ((@($Executable) + $Arguments | ForEach-Object { Format-DisplayArgument $_ }) -join ' ')
}

function Write-Banner {
    Write-Host ""
    Write-Host "===============================================" -ForegroundColor Cyan
    Write-Host " God of Lies · Load tests runner" -ForegroundColor Cyan
    Write-Host "===============================================" -ForegroundColor Cyan
}

function Write-StepHeader {
    param(
        [string]$Title,
        [string]$CommandText
    )

    $script:CurrentStep += 1
    Write-Host ""
    Write-Host ("[{0}/{1}] ▶ {2}" -f $script:CurrentStep, $script:TotalSteps, $Title) -ForegroundColor Cyan
    if ($CommandText) {
        Write-Host ("      {0}" -f $CommandText) -ForegroundColor DarkGray
    }
}

function Write-StepSuccess {
    param([string]$Message)

    Write-Host ("      ✔ {0}" -f $Message) -ForegroundColor Green
}

function Write-StepInfo {
    param([string]$Message)

    Write-Host ("      ℹ {0}" -f $Message) -ForegroundColor DarkGray
}

function Write-StepWarning {
    param([string]$Message)

    Write-Host ("      ⚠ {0}" -f $Message) -ForegroundColor Yellow
}

function Get-PreferredShellPath {
    try {
        $currentProcess = Get-Process -Id $PID -ErrorAction Stop
        if ($currentProcess.Path) {
            return $currentProcess.Path
        }
    } catch {
        # Fallback below.
    }

    foreach ($candidate in @("pwsh", "powershell")) {
        $command = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($command) {
            return $command.Source
        }
    }

    throw "No se ha encontrado pwsh ni powershell para lanzar scripts auxiliares."
}

function Test-BackendUp {
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 2 -ErrorAction Stop
        return $null -ne $health -and $health.status -eq "UP"
    } catch {
        return $false
    }
}

function Get-BackendListenerInfo {
    $connection = $null

    try {
        $connection = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction Stop |
            Select-Object -First 1
    } catch {
        return $null
    }

    if ($null -eq $connection) {
        return $null
    }

    $processId = [int]$connection.OwningProcess
    $processRecord = Get-CimInstance Win32_Process -Filter ("ProcessId = {0}" -f $processId) -ErrorAction SilentlyContinue
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue

    $name = if ($processRecord -and $processRecord.Name) { $processRecord.Name } elseif ($process) { $process.ProcessName } else { $null }
    $path = if ($processRecord) { $processRecord.ExecutablePath } elseif ($process) { $process.Path } else { $null }
    $commandLine = if ($processRecord) { $processRecord.CommandLine } else { $null }
    $parentProcessId = if ($processRecord) { [int]$processRecord.ParentProcessId } else { 0 }

    return [pscustomobject]@{
        ProcessId = $processId
        Name = $name
        Path = $path
        CommandLine = $commandLine
        ParentProcessId = $parentProcessId
    }
}

function Get-ManagedBackendState {
    if (-not (Test-Path $backendStateFile)) {
        return $null
    }

    try {
        return Get-Content -Path $backendStateFile -Raw | ConvertFrom-Json
    } catch {
        Remove-Item $backendStateFile -Force -ErrorAction SilentlyContinue
        return $null
    }
}

function Save-ManagedBackendState {
    param(
        [int]$RootProcessId,
        [string]$LogFile
    )

    if (-not (Test-Path $tmpDir)) {
        New-Item -Path $tmpDir -ItemType Directory | Out-Null
    }

    $state = [pscustomobject]@{
        RootProcessId = $RootProcessId
        BackendDir = $backendDir
        LogFile = $LogFile
        SavedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    }

    $state | ConvertTo-Json | Set-Content -Path $backendStateFile -Encoding UTF8
}

function Clear-ManagedBackendState {
    Remove-Item $backendStateFile -Force -ErrorAction SilentlyContinue
}

function Get-ProcessTreeIds {
    param([int]$RootProcessId)

    $allProcesses = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue)
    if ($allProcesses.Count -eq 0) {
        return @($RootProcessId)
    }

    $pending = [System.Collections.Generic.Queue[int]]::new()
    $result = [System.Collections.Generic.List[int]]::new()
    $visited = [System.Collections.Generic.HashSet[int]]::new()
    $pending.Enqueue($RootProcessId)

    while ($pending.Count -gt 0) {
        $currentId = $pending.Dequeue()
        if (-not $visited.Add($currentId)) {
            continue
        }

        $result.Add($currentId)

        foreach ($child in ($allProcesses | Where-Object { $_.ParentProcessId -eq $currentId })) {
            $pending.Enqueue([int]$child.ProcessId)
        }
    }

    return @($result)
}

function Stop-ProcessTree {
    param([int]$RootProcessId)

    $processIds = @(Get-ProcessTreeIds -RootProcessId $RootProcessId | Sort-Object -Descending -Unique)
    $stoppedAny = $false

    foreach ($processId in $processIds) {
        try {
            Stop-Process -Id $processId -Force -ErrorAction Stop
            $stoppedAny = $true
        } catch {
            # Puede que el proceso ya no exista; seguimos con el resto del árbol.
        }
    }

    return $stoppedAny
}

function Wait-BackendDown {
    param([int]$MaxWaitSeconds = 15)

    $elapsed = 0
    while ($elapsed -lt $MaxWaitSeconds) {
        if (-not (Test-BackendUp)) {
            return $true
        }

        Start-Sleep -Seconds 1
        $elapsed += 1
    }

    return (-not (Test-BackendUp))
}

function Format-BackendListenerSummary {
    param($ListenerInfo)

    if ($null -eq $ListenerInfo) {
        return "Puerto 8080 ocupado"
    }

    $parts = New-Object System.Collections.Generic.List[string]
    if ($ListenerInfo.Name) {
        $parts.Add($ListenerInfo.Name)
    }
    if ($ListenerInfo.ProcessId) {
        $parts.Add(("PID {0}" -f $ListenerInfo.ProcessId))
    }
    if ($ListenerInfo.Path) {
        $parts.Add($ListenerInfo.Path)
    }
    if ($ListenerInfo.CommandLine) {
        $commandLine = $ListenerInfo.CommandLine.Trim()
        if ($commandLine.Length -gt 180) {
            $commandLine = $commandLine.Substring(0, 180) + "…"
        }
        $parts.Add($commandLine)
    }

    return ($parts -join " · ")
}

function Get-StaleManagedBackendInfo {
    $state = Get-ManagedBackendState
    if ($null -eq $state) {
        return $null
    }

    if ($state.BackendDir -ne $backendDir) {
        Clear-ManagedBackendState
        return $null
    }

    $listenerInfo = Get-BackendListenerInfo
    if ($null -eq $listenerInfo) {
        Clear-ManagedBackendState
        return $null
    }

    $processTreeIds = @(Get-ProcessTreeIds -RootProcessId ([int]$state.RootProcessId))
    if ($processTreeIds -contains [int]$listenerInfo.ProcessId) {
        return [pscustomobject]@{
            State = $state
            ListenerInfo = $listenerInfo
        }
    }

    return $null
}

function Stop-StaleManagedBackend {
    param($ManagedBackendInfo)

    $listenerInfo = $ManagedBackendInfo.ListenerInfo
    $state = $ManagedBackendInfo.State
    $description = Format-BackendListenerSummary -ListenerInfo $listenerInfo

    Write-StepHeader -Title "Detener backend huérfano del runner" -CommandText $description

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $stoppedAny = Stop-ProcessTree -RootProcessId ([int]$state.RootProcessId)
    if (-not $stoppedAny) {
        throw "No se pudo detener el backend huérfano detectado del runner."
    }

    if (-not (Wait-BackendDown -MaxWaitSeconds 15)) {
        throw "Se intentó detener el backend huérfano del runner, pero localhost:8080 sigue respondiendo."
    }

    $stopwatch.Stop()
    Clear-ManagedBackendState
    Write-StepSuccess ("Backend huérfano detenido en {0:N1}s" -f $stopwatch.Elapsed.TotalSeconds)
}

function Get-LastMeaningfulLogLine {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return $null
    }

    $lines = @(Get-Content -Path $Path -Tail 20 -ErrorAction SilentlyContinue | Where-Object { $_ -and $_.Trim() })
    if ($lines.Count -gt 0) {
        return $lines[-1].Trim()
    }

    return $null
}

function Get-RecentLogExcerpt {
    param(
        [string]$Path,
        [int]$Tail = 20
    )

    if (-not (Test-Path $Path)) {
        return $null
    }

    return ((Get-Content -Path $Path -Tail $Tail -ErrorAction SilentlyContinue | Out-String).Trim())
}

function Get-ResultDirectories {
    if (-not (Test-Path $resultsDir)) {
        return @()
    }

    return @(Get-ChildItem -Path $resultsDir -Directory | Sort-Object LastWriteTimeUtc)
}

function Get-NewResultDirectory {
    param([string[]]$BeforePaths)

    $directories = Get-ResultDirectories
    $newDirectory = $directories |
        Where-Object { $BeforePaths -notcontains $_.FullName } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1

    if ($newDirectory) {
        return $newDirectory.FullName
    }

    $latestDirectory = $directories | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if ($latestDirectory) {
        return $latestDirectory.FullName
    }

    return $null
}

function Format-LatencyValue {
    param($Metric)

    if ($null -eq $Metric -or $Metric.count -le 0 -or $null -eq $Metric.p95) {
        return "-"
    }

    return ("p95 {0} ms · p50 {1} ms" -f $Metric.p95, $Metric.p50)
}

function Show-RunSummary {
    param([string]$ResultDirectory)

    if (-not $ResultDirectory) {
        Write-StepWarning "No se ha encontrado una carpeta de resultados reciente para resumir."
        return
    }

    $summaryPath = Join-Path $ResultDirectory "summary.json"
    if (-not (Test-Path $summaryPath)) {
        Write-StepWarning ("No existe summary.json en {0}" -f $ResultDirectory)
        return
    }

    $summary = Get-Content -Path $summaryPath -Raw | ConvertFrom-Json
    $statusColor = if ($summary.status -eq "passed") { "Green" } else { "Red" }

    Write-Host ""
    Write-Host "Resumen de la ejecución" -ForegroundColor Green
    Write-Host "---------------------" -ForegroundColor Green
    Write-Host ("  Estado      : {0}" -f $summary.status) -ForegroundColor $statusColor
    Write-Host ("  Carpeta     : {0}" -f $ResultDirectory) -ForegroundColor White
    Write-Host ("  Operaciones : {0}" -f $summary.totals.operations) -ForegroundColor White
    Write-Host ("  Entregas    : {0}" -f $summary.totals.deliveries) -ForegroundColor White
    Write-Host ("  Errores     : {0}" -f $summary.totals.errors) -ForegroundColor White
    Write-Host ("  HTTP        : {0}" -f (Format-LatencyValue $summary.latencies.httpMs)) -ForegroundColor White
    Write-Host ("  Fan-out     : {0}" -f (Format-LatencyValue $summary.latencies.fanoutMs)) -ForegroundColor White

    if ($summary.failureMessage) {
        Write-Host ("  Fallo       : {0}" -f $summary.failureMessage) -ForegroundColor Red
    }
}

function Invoke-NativeCommand {
    param(
        [string]$Title,
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory,
        [string]$FailureMessage,
        [switch]$IgnoreExitCode
    )

    $displayCommand = Format-DisplayCommand ([System.IO.Path]::GetFileName($FilePath)) $Arguments
    Write-StepHeader -Title $Title -CommandText $displayCommand
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

    Push-Location $WorkingDirectory
    try {
        & $FilePath @Arguments
        $exitCode = if ($null -ne $LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
        $script:LastNativeExitCode = $exitCode
        if ($exitCode -ne 0) {
            $stopwatch.Stop()
            if ($IgnoreExitCode) {
                Write-StepWarning ("Terminó en {0:N1}s con código {1}" -f $stopwatch.Elapsed.TotalSeconds, $exitCode)
                return
            }

            throw ("{0} (código {1})" -f $FailureMessage, $exitCode)
        }
    } finally {
        Pop-Location
    }

    $stopwatch.Stop()
    Write-StepSuccess ("Completado en {0:N1}s" -f $stopwatch.Elapsed.TotalSeconds)
}

function Start-BackendProcess {
    param(
        [string]$ShellPath,
        [int]$MaxWaitSeconds
    )

    if (-not (Test-Path $tmpDir)) {
        New-Item -Path $tmpDir -ItemType Directory | Out-Null
    }

    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $script:BackendLogFile = Join-Path $tmpDir ("backend-{0}.log" -f $timestamp)
    $backendCommand = @"
Set-Location '$backendDir'
`$env:APP_SEED_ORGANIZER_PASSWORD = '$seedOrganizerPassword'
`$env:SPRING_DEVTOOLS_RESTART_ENABLED = 'false'
.\mvnw.cmd spring-boot:run *> '$script:BackendLogFile'
"@

    $shellName = [System.IO.Path]::GetFileName($ShellPath)
    $displayCommand = "{0} -NoLogo -NoProfile -Command <backend spring-boot:run con log en {1}>" -f $shellName, $script:BackendLogFile
    Write-StepHeader -Title "Iniciar backend Spring Boot" -CommandText $displayCommand

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $script:BackendProcess = Start-Process -FilePath $ShellPath -ArgumentList @("-NoLogo", "-NoProfile", "-Command", $backendCommand) -WorkingDirectory $backendDir -PassThru -WindowStyle Hidden
    $script:BackendStartedByScript = $true
    Save-ManagedBackendState -RootProcessId $script:BackendProcess.Id -LogFile $script:BackendLogFile

    Write-StepInfo ("PID backend: {0}" -f $script:BackendProcess.Id)
    Write-StepInfo ("Log backend: {0}" -f $script:BackendLogFile)

    $elapsed = 0
    $lastPrintedLine = $null

    while ($elapsed -lt $MaxWaitSeconds) {
        $script:BackendProcess.Refresh()
        if ($script:BackendProcess.HasExited) {
            $stopwatch.Stop()
            $excerpt = Get-RecentLogExcerpt -Path $script:BackendLogFile
            $details = if ($excerpt) { "`n`nÚltimas líneas:`n$excerpt" } else { "" }
            throw ("El backend terminó antes de estar listo.{0}" -f $details)
        }

        if (Test-BackendUp) {
            $stopwatch.Stop()
            Write-StepSuccess ("Backend listo en {0:N1}s" -f $stopwatch.Elapsed.TotalSeconds)
            return
        }

        $lastLine = Get-LastMeaningfulLogLine -Path $script:BackendLogFile
        if ($lastLine -and $lastLine -ne $lastPrintedLine) {
            Write-StepInfo (("[{0,3}s] {1}" -f $elapsed, $lastLine))
            $lastPrintedLine = $lastLine
        } elseif ($elapsed -eq 0 -or ($elapsed % 10 -eq 0)) {
            Write-StepInfo (("[{0,3}s] Esperando /actuator/health..." -f $elapsed))
        }

        Start-Sleep -Seconds 2
        $elapsed += 2
    }

    $stopwatch.Stop()
    $excerpt = Get-RecentLogExcerpt -Path $script:BackendLogFile
    $details = if ($excerpt) { "`n`nÚltimas líneas:`n$excerpt" } else { "" }
    throw ("El backend no respondió con /actuator/health en {0}s.{1}" -f $MaxWaitSeconds, $details)
}

if (-not (Test-Path $loadTestsDir)) {
    throw "No se encuentra la carpeta load-tests en $loadTestsDir"
}

$shellPath = Get-PreferredShellPath
$shellName = [System.IO.Path]::GetFileName($shellPath)
$backendWasRunning = Test-BackendUp
$managedBackendInfo = if (-not $NoReset -and $backendWasRunning) { Get-StaleManagedBackendInfo } else { $null }
$needsBackendStart = (-not $backendWasRunning) -or ($null -ne $managedBackendInfo)

$shouldInstall = (-not $NoInstall) -or (-not (Test-Path (Join-Path $loadTestsDir "node_modules")))

$stepNames = New-Object System.Collections.Generic.List[string]
if ($managedBackendInfo) {
    $stepNames.Add("cleanup")
}
if (-not $NoReset) {
    $stepNames.Add("reset")
}
if ($needsBackendStart) {
    $stepNames.Add("backend")
}
if ($shouldInstall) {
    $stepNames.Add("install")
}
$stepNames.Add("scenario")
$script:TotalSteps = $stepNames.Count

Write-Banner
Write-Host ("  Escenario : {0}" -f $Scenario) -ForegroundColor White
if ($ScenarioArgs.Count -gt 0) {
    Write-Host ("  Overrides : {0}" -f (($ScenarioArgs | ForEach-Object { Format-DisplayArgument $_ }) -join ' ')) -ForegroundColor DarkGray
}
Write-Host ("  Shell     : {0}" -f $shellName) -ForegroundColor DarkGray
Write-Host ("  Backend   : {0}" -f $(if ($backendWasRunning -and -not $managedBackendInfo) { "reutilizar localhost:8080" } else { "arranque estable (sin restart de DevTools)" })) -ForegroundColor DarkGray
Write-Host ("  Resultados: {0}" -f $resultsDir) -ForegroundColor DarkGray

$resultDirectoriesBeforeRun = @(Get-ResultDirectories | Select-Object -ExpandProperty FullName)

try {
    if ($managedBackendInfo) {
        Stop-StaleManagedBackend -ManagedBackendInfo $managedBackendInfo
        $backendWasRunning = Test-BackendUp
    }

    if (-not $NoReset -and $backendWasRunning) {
        $listenerInfo = Get-BackendListenerInfo
        $listenerSummary = Format-BackendListenerSummary -ListenerInfo $listenerInfo
        throw "No se puede resetear la base de datos mientras el backend está en ejecución. Detectado: $listenerSummary. Detén ese proceso o usa -NoReset."
    }

    if (-not $NoReset) {
        Invoke-NativeCommand -Title "Resetear base de datos local" `
            -FilePath $shellPath `
            -Arguments @("-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $root "reset-db.ps1"), "-Force") `
            -WorkingDirectory $root `
            -FailureMessage "reset-db.ps1 terminó con error"
    }

    if (-not $backendWasRunning) {
        Start-BackendProcess -ShellPath $shellPath -MaxWaitSeconds $BackendWaitSeconds
    } else {
        Write-Host ""
        Write-StepInfo "Backend ya estaba disponible en http://localhost:8080; se reutiliza tal cual."
    }

    if ($shouldInstall) {
        Invoke-NativeCommand -Title "Instalar dependencias de load-tests" `
            -FilePath "npm" `
            -Arguments @("--prefix", $loadTestsDir, "install") `
            -WorkingDirectory $root `
            -FailureMessage "npm install terminó con error"
    } else {
        Write-Host ""
        Write-StepInfo "Se reutiliza node_modules existente; no se ejecuta npm install."
    }

    $scenarioCommandArguments = @("--prefix", $loadTestsDir, "run", "run", "--", "--scenario", $Scenario) + $ScenarioArgs
    Invoke-NativeCommand -Title ("Lanzar escenario '{0}'" -f $Scenario) `
        -FilePath "npm" `
        -Arguments $scenarioCommandArguments `
        -WorkingDirectory $root `
        -FailureMessage ("La ejecución del escenario '{0}' terminó con error" -f $Scenario) `
        -IgnoreExitCode
    $scenarioExitCode = $script:LastNativeExitCode

    $resultDirectory = Get-NewResultDirectory -BeforePaths $resultDirectoriesBeforeRun
    Show-RunSummary -ResultDirectory $resultDirectory

    if ($scenarioExitCode -ne 0) {
        throw ("La ejecución del escenario '{0}' terminó con error (código {1})" -f $Scenario, $scenarioExitCode)
    }

    if ($script:BackendStartedByScript) {
        if ($cleanupBackendWhenDone) {
            $stoppedAny = Stop-ProcessTree -RootProcessId $script:BackendProcess.Id
            if ($stoppedAny) {
                Wait-BackendDown -MaxWaitSeconds 15 | Out-Null
            }
            Clear-ManagedBackendState
            Write-Host ""
            Write-StepSuccess ("Backend detenido al finalizar (raíz PID {0})" -f $script:BackendProcess.Id)
        } else {
            Write-Host ""
            Write-StepInfo ("El backend iniciado por el script sigue activo en segundo plano (PID {0})." -f $script:BackendProcess.Id)
            Write-StepInfo ("Para detenerlo: Stop-Process -Id {0}" -f $script:BackendProcess.Id)
            Write-StepInfo ("Log disponible en: {0}" -f $script:BackendLogFile)
        }
    }
} catch {
    Write-Host ""
    Write-Host ("✖ {0}" -f $_.Exception.Message) -ForegroundColor Red
    throw
} finally {
    if ($cleanupBackendWhenDone -and $script:BackendStartedByScript -and $script:BackendProcess) {
        try {
            Stop-ProcessTree -RootProcessId $script:BackendProcess.Id | Out-Null
        } catch {
            # Best effort cleanup.
        }

        Clear-ManagedBackendState
    }
}
