# Load tests

Arnés de carga local.

## Requisitos

- Backend accesible en `http://localhost:8080`
- Node `^20.19.0 || >=22.12.0`
- Usuario seed del backend: `organizer / organizer` (los scripts `start-dev.ps1` y `run-load-tests.ps1` fijan `APP_SEED_ORGANIZER_PASSWORD=organizer` al arrancar el backend)

## Instalación

Desde la raíz del repositorio:

- `npm --prefix load-tests install`

## Escenarios disponibles

- `smoke`: recorrido corto para validar bootstrap, login, websocket y fan-out básico.
- `nominal-100-10writers`: partida nominal con clientes conectados; cada escritor activo emite una operación por segundo con arranque escalonado, el arnés sustituye el exceso de escrituras positivas por retiradas de amenaza y no aborta al primer fan-out incompleto: sigue hasta el final registrando métricas y errores.
- `burst-100writers`: ráfagas de 100 escritores en olas concurrentes, con reset controlado entre olas para medir convergencia.
- `connect-100`: conexión masiva con rampa y pulsos manuales del organizer.

## Ejecución

Desde la raíz del repositorio:

- `npm --prefix load-tests run run -- --scenario smoke`
- `npm --prefix load-tests run run -- --scenario nominal-100-10writers`
- `npm --prefix load-tests run run -- --scenario burst-100writers`
- `npm --prefix load-tests run run -- --scenario connect-100`

Para acortar escenarios largos durante desarrollo se puede sobrescribir la duración y otros parámetros por CLI, por ejemplo:

- `npm --prefix load-tests run run -- --scenario nominal-100-10writers --hold-ms 15000 --writer-count 4`

En `nominal-100-10writers`, `--operation-interval-ms` se aplica **por escritor**. Por ejemplo, `--writer-count 100 --operation-interval-ms 1000 --hold-ms 30000` apunta a unas `3000` operaciones emitidas en total (100 escritores × 30 s).

Además, el escenario reparte automáticamente la mezcla de operaciones para no forzar un final artificial de partida: conserva una fracción principal de `REPORT_VILLAIN_DAMAGE`, pero cuando el presupuesto seguro de daño se agotaría, el resto del tráfico se emite como `REMOVE_THREAT` en vez de seguir acumulando amenaza positiva.

Durante la fase de emisión, el nominal imprime hitos de progreso del `10%`, `20%`, etc. en la terminal. Para campañas largas, también usa un `deliveryTimeoutMs` por defecto más holgado (`45000` ms) y, si detecta fan-outs incompletos, deja trazas en `errors.json`, `operations.csv` y `summary.json` en vez de abortar al primer caso.
- `npm --prefix load-tests run run -- --scenario burst-100writers --clients 10 --pods 2 --groups-per-pod 5 --wave-count 2 --wave-cooldown-ms 1000`

También se puede usar `run-load-tests.ps1` para resetear la base de datos, arrancar el backend y lanzar un escenario con una UX más guiada en PowerShell 7.

Ejemplos:

- `./run-load-tests.ps1 -Scenario smoke`
- `./run-load-tests.ps1 -Scenario burst-100writers --clients 10 --pods 2 --groups-per-pod 5 --wave-count 2 --wave-cooldown-ms 1000`
- `./run-load-tests.ps1 -Scenario nominal-100-10writers -NoReset -NoInstall --hold-ms 15000 --writer-count 4`

El script:

- muestra pasos numerados y el comando que está ejecutando,
- arranca el backend en modo estable (`SPRING_DEVTOOLS_RESTART_ENABLED=false`) si hace falta,
- espera a `/actuator/health` mostrando progreso y la ruta del log del backend,
- y al final resume el `summary.json` más reciente.

Si el script arranca el backend, por defecto lo detiene al terminar. Si quieres dejarlo vivo en segundo plano y conservar el `PID` y la ruta del log, usa `-KeepBackendWhenDone`.

## Resultados

Cada ejecución genera una carpeta en `load-tests/results/` con:

- `manifest.json`
- `client-sessions.csv`
- `operations.csv`
- `deliveries.csv`
- `resource-samples.csv`
- `summary.json`
- `errors.json` si se registran incidencias

## Notas

La primera iteración del arnés mide correctamente login, conexión realtime, latencia HTTP, fan-out y consistencia de snapshots. Las métricas internas del backend (`heap`, `threads`, `cpu` del proceso Java) quedan preparadas para una segunda fase si se exponen más endpoints de Actuator.


## Ejemplos de comandos

.\run-load-tests.ps1 -Scenario nominal-100-10writers -NoInstall --clients 100 --writer-count 20 --pods 25 --groups-per-pod 4 --hold-ms 30000 --ramp-step 100 --ramp-interval-ms 1000

.\run-load-tests.ps1 -Scenario nominal-100-10writers -NoInstall --clients 100 --writer-count 100 --pods 10 --groups-per-pod 10 --hold-ms 30000 --ramp-step 100 --ramp-interval-ms 1000


Estos dos comandos son los que han sido utilizados para las pruebas de carga que se describen y analizan en la memoria del TFG.