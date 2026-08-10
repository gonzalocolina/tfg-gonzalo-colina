# Gestión de eventos multijugador de Marvel Champions

Aplicación web para coordinar partidas presenciales del modo **Multijugador épico** de *Marvel Champions*. La plataforma centraliza el estado compartido del evento (vida del villano, amenaza, temporizador, mesas, nodos y cartas inter-nodo) y lo sincroniza en tiempo real entre el equipo organizador y los representantes de cada mesa.

El proyecto se ha desarrollado como Trabajo de Fin de Grado del Grado en Ingeniería Informática de la Universidad de Valladolid.

- **Autor:** Gonzalo José Colina Báscones
- **Tutor:** Mario Corrales Astorgano
- **Licencia:** [MIT](LICENSE)



## Funcionalidades

La aplicación dispone de dos interfaces adaptadas a sus contextos de uso:

- **Organizador:** creación y configuración de eventos, catálogo de villanos y cartas, distribución de mesas en nodos, delegación en coorganizadores y supervisión de la partida.
- **Jugador:** acceso desde móvil mediante un código, consulta del estado global y registro de daño, amenaza y cartas inter-nodo.

Además, conserva un registro auditable de las acciones de la partida, permite aplicar correcciones y genera estadísticas al finalizar el evento.

## Arquitectura


| Componente    | Tecnología             | Responsabilidad                                              |
| ------------- | ---------------------- | ------------------------------------------------------------ |
| Backend       | Java 17 y Spring Boot  | API REST, autenticación JWT, WebSocket/SockJS y persistencia |
| Frontend      | Vue 3 y Vite           | Portal, panel del organizador e interfaz móvil del jugador   |
| Base de datos | H2 en fichero          | Persistencia local o en un volumen Docker                    |
| Despliegue    | Docker Compose y Nginx | Construcción y publicación de la aplicación                  |


El frontend de desarrollo unifica las tres aplicaciones en `http://localhost:5175` y redirige las peticiones de API y WebSocket al backend, disponible en `http://localhost:8080`.

Los modelos de datos se pueden consultar en los diagramas de [dominio](docs/diagrama-modelo-dominio.puml) y [entidad-relación](docs/diagrama-entidad-relacion.puml).

## Inicio rápido



### Requisitos

- Java 17.
- Node.js `^20.19.0` o `>=22.12.0`, con npm.
- PowerShell.

El repositorio incluye Maven Wrapper, por lo que no es necesario instalar Maven.

### Ejecución local

Desde la raíz del repositorio:

```powershell
Copy-Item .env.example .env
npm --prefix frontend install
.\start-dev.ps1
```

Una vez iniciado el entorno:

- Portal: [http://localhost:5175](http://localhost:5175)
- Organizador: [http://localhost:5175/organizer/login](http://localhost:5175/organizer/login)
- Jugador: [http://localhost:5175/player/join](http://localhost:5175/player/join)
- Estado del backend: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

Las credenciales iniciales definidas en `.env.example` son `organizer` / `organizer`. Los valores de ejemplo están pensados exclusivamente para desarrollo local.

Para conocer el arranque manual, el despliegue con Docker, la persistencia y el resto de opciones, consulta el [manual de instalación](docs/manual-instalacion.md).

## Pruebas

Backend:

```powershell
Set-Location backend
.\mvnw.cmd test
```

Frontend, desde la raíz:

```powershell
npm --prefix frontend install
npm --prefix frontend test
```

El proyecto también incluye [escenarios de pruebas de carga](load-tests/README.md).

## Estructura del repositorio

```text
backend/            API, seguridad, tiempo real y persistencia
frontend/           Portal y aplicaciones de organizador y jugador
docs/               Manuales y diagramas del sistema
load-tests/         Pruebas de carga
docker-compose.yml  Despliegue con contenedores
start-dev.ps1       Arranque del entorno de desarrollo
reset-db.ps1        Reinicio de la base de datos local
```



## Documentación

- [Manual de instalación y despliegue](docs/manual-instalacion.md)
- [Manual de usuario](docs/manual-usuario.md)
- [Pruebas de carga](load-tests/README.md)
- [Diagrama del modelo de dominio](docs/diagrama-modelo-dominio.puml)
- [Diagrama entidad-relación](docs/diagrama-entidad-relacion.puml)



## Aviso

*Marvel Champions: The Card Game* y sus elementos relacionados pertenecen a Fantasy Flight Games y a sus respectivos titulares. Este proyecto es una herramienta académica no oficial y no está afiliado con ellos.