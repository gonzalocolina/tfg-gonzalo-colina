# Manual de instalación y despliegue

Este documento explica cómo preparar y ejecutar la aplicación en desarrollo local o mediante Docker Compose. Los comandos de PowerShell se ejecutan desde la raíz del repositorio salvo que se indique lo contrario.

## Requisitos

### Desarrollo local

- Java 17.
- Node.js `^20.19.0` o `>=22.12.0`, con npm.
- PowerShell para los scripts auxiliares.

El Maven Wrapper está incluido en `backend/`, por lo que no es necesario instalar Maven.

### Docker

- Docker.
- Docker Compose.

## Configuración

Crea el fichero de configuración local a partir del ejemplo:

```powershell
Copy-Item .env.example .env
```

Antes de utilizar la aplicación fuera de un entorno local, cambia el secreto JWT, la contraseña inicial y los orígenes permitidos. El fichero `.env` no debe incorporarse al control de versiones.

### Variables de entorno

| Variable | Valor de ejemplo o predeterminado | Uso |
| --- | --- | --- |
| `APP_JWT_SECRET` | obligatorio | Secreto utilizado para firmar los tokens JWT |
| `APP_JWT_EXPIRATION_MINUTES` | `120` | Duración de los tokens |
| `APP_SEED_ORGANIZER_PASSWORD` | `organizer` en `.env.example` | Contraseña del usuario inicial `organizer` |
| `APP_ALLOWED_ORIGINS` | `http://*:*,https://*:*` | Orígenes admitidos por WebSocket/SockJS |
| `APP_DATA_DIR` | `%LOCALAPPDATA%\mc-tfg` con `start-dev.ps1` | Directorio de la base H2 y las imágenes |
| `APP_INTER_POD_CARD_IMAGES_DIR` | `<APP_DATA_DIR>/inter-pod-cards` | Ubicación alternativa para las imágenes |
| `APP_INTER_POD_CARD_MAX_IMAGE_BYTES` | `5242880` | Tamaño máximo de cada imagen, en bytes |
| `SERVER_PORT` | `8080` | Puerto interno del backend en ejecución local |
| `BACKEND_PORT` | `8080` | Puerto del backend publicado por Compose |
| `FRONTEND_PORT` | `5175` | Puerto del frontend publicado por Compose |

`APP_JWT_SECRET` no tiene valor predeterminado en Spring Boot. Debe estar definido antes de iniciar el backend.

## Ejecución local recomendada

Instala primero las dependencias del frontend:

```powershell
npm --prefix frontend install
```

Después, inicia el entorno:

```powershell
.\start-dev.ps1
```

El script:

1. carga las variables de `.env`;
2. comprueba `APP_JWT_SECRET` y `APP_SEED_ORGANIZER_PASSWORD`;
3. prepara el directorio de datos;
4. limpia y arranca el backend;
5. espera a que responda el endpoint de salud;
6. abre el frontend unificado.

El script no instala las dependencias npm automáticamente. Si es la primera ejecución o ha cambiado `frontend/package-lock.json`, ejecuta de nuevo `npm --prefix frontend install`.

Direcciones disponibles:

- Portal: <http://localhost:5175>
- Organizador: <http://localhost:5175/organizer/login>
- Jugador: <http://localhost:5175/player/join>
- API y backend: <http://localhost:8080>
- Estado del backend: <http://localhost:8080/actuator/health>
- Consola H2 local: <http://localhost:8080/h2-console>

## Arranque manual

El arranque manual permite ejecutar cada componente en su propia terminal.

### Backend

Define como mínimo el secreto JWT y, si quieres unas credenciales iniciales conocidas, la contraseña del organizador:

```powershell
Set-Location backend
$env:APP_JWT_SECRET = "un-secreto-local"
$env:APP_SEED_ORGANIZER_PASSWORD = "organizer"
.\mvnw.cmd clean spring-boot:run
```

Si no se define `APP_SEED_ORGANIZER_PASSWORD`, el usuario `organizer` se crea con una contraseña aleatoria que se muestra en el registro del backend.

### Frontend

En otra terminal, desde la raíz:

```powershell
npm --prefix frontend install
npm --prefix frontend run dev
```

El servidor de desarrollo inicia el portal y las aplicaciones de organizador y jugador, y las publica a través del puerto `5175`.

## Despliegue con Docker Compose

Prepara `.env` y construye los servicios:

```powershell
Copy-Item .env.example .env
docker compose up --build -d
```

Compose construye el backend y el frontend, publica los puertos configurados y guarda los datos del backend en el volumen `backend-data`.

Para detener los servicios sin eliminar los datos:

```powershell
docker compose down
```

Para detenerlos y eliminar también el volumen persistente:

```powershell
docker compose down -v
```

Esta última operación borra la base de datos y las imágenes almacenadas en el volumen.

## Persistencia

La base de datos utiliza H2 en modo fichero y se denomina `mc_database`.

- Con `start-dev.ps1`: `%LOCALAPPDATA%\mc-tfg\mc_database.*`, salvo que `APP_DATA_DIR` indique otra ruta.
- Con arranque manual desde `backend/`: `backend/data/mc_database.*`, si no se define `APP_DATA_DIR`.
- Con Docker Compose: `/app/data/mc_database.*` dentro del volumen `backend-data`.

Las imágenes de cartas inter-nodo se guardan de forma predeterminada en el subdirectorio `inter-pod-cards` del mismo directorio de datos.

### Reiniciar la base local

Detén primero el backend. Después ejecuta:

```powershell
.\reset-db.ps1
```

El script muestra los ficheros que va a eliminar y solicita escribir `BORRAR`. Para omitir la confirmación:

```powershell
.\reset-db.ps1 -Force
```

Solo se borran los ficheros `mc_database.*`; las imágenes y otros recursos del directorio se conservan. La base se recreará en el siguiente arranque.

## Comprobación del despliegue

Tras iniciar el sistema:

1. Comprueba que <http://localhost:8080/actuator/health> responde con estado `UP`.
2. Abre <http://localhost:5175>.
3. Verifica las rutas de organizador y jugador.
4. Inicia sesión y confirma que las actualizaciones en tiempo real funcionan.
5. Reinicia el entorno y comprueba que los datos siguen disponibles.

Si los puertos `8080` o `5175` están ocupados, libera los procesos que los utilizan o ajusta la configuración correspondiente antes de volver a iniciar.

## Pruebas

### Backend

```powershell
Set-Location backend
.\mvnw.cmd test
```

### Frontend

Desde la raíz:

```powershell
npm --prefix frontend install
npm --prefix frontend test
```

### Pruebas de carga

El arnés de carga requiere que el backend esté disponible en `http://localhost:8080`:

```powershell
npm --prefix load-tests install
npm --prefix load-tests run run -- --scenario smoke
```

También se puede usar el flujo guiado:

```powershell
.\run-load-tests.ps1 -Scenario smoke
```

Consulta los escenarios, parámetros y resultados en el [manual de pruebas de carga](../load-tests/README.md).
