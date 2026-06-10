# API Agent Guide (Frontend)

Objetivo: guia minima para que un agente de frontend integre rapido este backend.

## 1) Base
- Base URL: `http://localhost:8080`
- Auth header: `Authorization: Bearer <accessToken>`
- Content-Type: `application/json`
- WebSocket STOMP endpoint: `/ws` (SockJS)

## 2) Roles de token
- `ORGANIZER`: cuenta del panel organizer. Control total en eventos propios (`events.organizer_id`) y acciones delegadas en eventos donde tiene invitacion aceptada (`co_organizer_invitations`).
- `GROUP_REPRESENTATIVE`: reporta dano/amenaza e inter-pod de su mesa.
- `GROUP_OBSERVER`: solo lectura.

## 3) Flujo recomendado (happy path)
1. Crear evento (publico): `POST /api/events`.
2. Login organizador: `POST /api/auth/organizer/login`.
3. Crear nodos y mesas:
- `POST /api/events/{eventId}/pods`
- `POST /api/events/{eventId}/pods/{podId}/groups`
4. Login mesa (representante u observador): `POST /api/auth/group/login`.
5. Iniciar partida (sin tocar timer): `POST /api/events/{eventId}/game-state/start`.
6. Si hay timer configurado, controlarlo:
- `POST /api/events/{eventId}/game-state/timer/start|pause|resume|adjust`
7. Durante partida:
- Dano: `POST /api/events/{eventId}/game-state/villain-damage`
- Amenaza: `POST /api/events/{eventId}/game-state/threat`
8. Leer estado global:
- Polling: `GET /api/events/{eventId}/game-state`
- Realtime: suscripcion STOMP a topics.

## 4) Endpoints clave

### Auth
- `POST /api/auth/register`
  - req: `{ "username", "email", "password" }`
- `POST /api/auth/organizer/login`
  - req: `{ "username", "password" }`
  - nota: todas las cuentas del panel usan este login; el rol por evento se resuelve con `organizer_id` + `co_organizer_invitations`.
- `POST /api/auth/group/login`
  - req: `{ "eventId", "identifier", "asObserver"? }`
  - nota: `identifier` es el nombre de la mesa; `asObserver=true` emite token de solo lectura.
- `POST /api/auth/group/login-by-code`
  - req: `{ "code", "groupLabel"?, "playerCount"?, "asObserver"? }`
  - nota: `code` es el código del evento; `groupLabel` identifica la mesa (se crea si no existe).
- resp comun:
  - `{ "tokenType", "accessToken", "expiresInSeconds", "role", "eventId", "groupId", "username", "displayName" }`

### Evento
- `POST /api/events` (publico)
  - req: `{ "name", "configuredPlayerCount", "timeLimitMinutes", "organizerUsername", "organizerDisplayName", "organizerEmail", "organizerPassword" }`
  - nota: `timeLimitMinutes=0` => evento sin temporizador.
- `GET /api/events/{eventId}`
- `PUT /api/events/{eventId}`
- `DELETE /api/events/{eventId}`

### Setup
- `POST /api/events/{eventId}/pods`
  - req: `{ "name", "sortOrder" }`
- `POST /api/events/{eventId}/pods/{podId}/groups`
  - req: `{ "identifier", "playerCount" }`
  - resp: `{ "id", "podId", "identifier", "playerCount" }`.
- `GET /api/events/{eventId}/structure`

### Roles
- `POST /api/events/{eventId}/roles/co-organizer-invitations`
  - req: `{ "username", "responsibilities": ["VILLAIN_HP"|"THREAT"|"TIMER"] }`
- `GET /api/events/{eventId}/roles/co-organizers`
- `GET /api/events/{eventId}/roles/co-organizer-invitations`
- `POST /api/co-organizer-invitations/{invitationId}/accept`
- `POST /api/co-organizer-invitations/{invitationId}/reject`

### Estado de partida
- `GET /api/events/{eventId}/game-state`
- `POST /api/events/{eventId}/game-state/start`
- `POST /api/events/{eventId}/game-state/villain-damage`
  - req: `{ "damage" }`
- `POST /api/events/{eventId}/game-state/threat`
  - req: `{ "threat" }` (token de representante con `groupId` en JWT)
- `PATCH /api/events/{eventId}/game-state/villain-hp`
  - req: `{ "value" }`
- `PATCH /api/events/{eventId}/game-state/threat`
  - req: `{ "value" }`
- Timer:
  - `POST /api/events/{eventId}/game-state/timer/start`
  - `POST /api/events/{eventId}/game-state/timer/pause`
  - `POST /api/events/{eventId}/game-state/timer/resume`
  - `POST /api/events/{eventId}/game-state/timer/adjust` req `{ "minutesDelta" }`

### Inter-pod cards
- `POST /api/events/{eventId}/inter-pod-cards` (multipart)
  - part `payload` (json): `{ "interPodCardDefinitionId", "sourceGroupId"? (organizador), "metricMax"?, "status"? }`
  - part opcional `image`: png/jpg/jpeg/webp (max 5MB)
- `POST /api/events/{eventId}/inter-pod-cards/{cardId}/progress`
  - req: `{ "amount" }` (reduce `metricCurrent`; a 0 marca `DERROTADA`)
- `PATCH /api/events/{eventId}/inter-pod-cards/{cardId}/status`
  - req: `{ "status": "OCULTA"|"REVELADA"|"DERROTADA" }`
- `DELETE /api/events/{eventId}/inter-pod-cards/{cardId}`
- `GET /api/events/{eventId}/inter-pod-cards?podId=<optional>`
- `GET /api/events/{eventId}/inter-pod-cards/{cardId}/image`
- Respuesta de instancia: `{ "metricMax", "metricCurrent", "cardCategory", ... }` (`cardCategory` define si la métrica es HP o amenaza).
- Catálogo (`/api/inter-pod-card-definitions`, multipart create/update): `defaultMetricMax`, `ruleType`, `cardCategory`.

### Log
- `GET /api/events/{eventId}/logs`
- filtros opcionales:
  - `groupId`
  - `actionType`
- combinables: `?groupId=1&actionType=INTER_POD_CARD_CREATED`

## 5) WebSocket (realtime)
Conectar SockJS+STOMP a `/ws` y suscribirse a:
- `/topic/events/{eventId}/game-state`
- `/topic/events/{eventId}/notifications`
- `/topic/events/{eventId}/game-finished`

Payload tipo:
```json
{
  "operation": "REPORT_THREAT",
  "snapshot": { "eventId": 1, "eventStatus": "IN_PROGRESS" },
  "notifications": ["THREAT_50_PERCENT"],
  "eventFinished": false,
  "emittedAt": "2026-03-11T16:00:00Z"
}
```

## 6) Estados y eventos que debe manejar UI
- `eventStatus`: `DRAFT | READY | IN_PROGRESS | FINISHED | CANCELLED`
- `timerStatus`: `NOT_CONFIGURED | READY | RUNNING | PAUSED | FINISHED`
- `eventResult`: `PLAYERS_VICTORY | THREAT_DEFEAT | TIMEOUT_DEFEAT | CANCELLED`
- notificaciones frecuentes:
  - `VILLAIN_HP_50_PERCENT`, `VILLAIN_HP_25_PERCENT`
  - `THREAT_50_PERCENT`, `THREAT_75_PERCENT`, `THREAT_100_PERCENT`
  - `TIMER_30_MINUTES`, `TIMER_15_MINUTES`, `TIMER_5_MINUTES`
  - `EVENT_FINISHED_*`

## 7) Errores
Formato de error:
```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "...",
  "details": ["..."]
}
```
- 400: regla de negocio/validacion.
- 401: no autenticado o token invalido/expirado.
- 403: rol no autorizado.
- 404: recurso no encontrado.

## 8) Regla practica para frontend-agent
- Guardar token por sesion y rol.
- Si 401: forzar relogin.
- Si 403: ocultar accion y mostrar mensaje de permisos.
- Si `eventStatus=FINISHED`: bloquear mutaciones y mostrar `finalSummary`.
- Combinar `GET /game-state` al entrar + WebSocket para tiempo real.
