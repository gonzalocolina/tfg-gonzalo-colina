# Load tests

Arnés de carga local para `God of Lies`.

## Requisitos

- Backend accesible en `http://localhost:8080`
- Node `^20.19.0 || >=22.12.0`
- Usuario seed del backend: `organizer / organizer` (los scripts `start-dev.ps1` y `run-load-tests.ps1` fijan `APP_SEED_ORGANIZER_PASSWORD=organizer` al arrancar el backend)

## Instalación

Desde la raíz del repositorio:

- `npm --prefix load-tests install`

## Escenarios disponibles

- `smoke`: recorrido corto para validar bootstrap, login, websocket y fan-out básico.
- `nominal-100-10writers`: partida nominal con 100 clientes conectados, 10 escritores y una operación por segundo.
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

Si el script arranca el backend, por defecto lo detiene automáticamente al terminar para no dejar un Spring Boot huérfano ocupando `localhost:8080`. Si quieres reutilizarlo tras la ejecución, usa `-KeepBackendWhenDone`.

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
