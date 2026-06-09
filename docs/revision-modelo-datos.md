# Revisión integral del modelo de datos

## Diagnóstico rápido

El modelo actual mezcla tres tipos de complejidad:

1. **Complejidad real del dominio**
   - evento
   - estado de partida
   - nodos/pods
   - mesas/grupos
   - villano
   - cartas inter-pod

2. **Complejidad accidental del almacenamiento**
   - duplicidad entre `invitations_invitations` y `invitations_assignments`
   - tabla separada `invitations_invitation_responsibilities` para un dato que conceptualmente pertenece a la invitación/asignación
   - selección de catálogo por evento mediante `event_inter_pod_card_definitions`, que añade un nivel extra de relación para una restricción de UI

3. **Nombres poco cercanos al lenguaje del usuario**
   - `event_organizers`
   - `player_table`
   - `pods`
   - `villain_definitions`
   - `inter_pod_card_definitions`

## Qué existe ahora mismo

Verificado en la H2 local del proyecto:

- `event_inter_pod_card` ✅ existe y **sí es necesaria**: representa instancias vivas de cartas dentro de la partida.
- `inter_pod_card_definitions` ✅ existe y **sí tiene sentido**: representa plantillas reutilizables del catálogo.
- `event_inter_pod_card_definitions` ✅ existe, pero **es candidata clara a eliminación**: solo sirve para seleccionar un subconjunto de plantillas por evento.

## Modelo objetivo recomendado

### 1. Cuentas del panel

**Nombre objetivo**: `organizers`

Sustituye a `event_organizers`.

Motivo:
- en este sistema no hay usuarios finales persistidos para jugadores; los jugadores entran por código
- las cuentas persistidas pertenecen al lado organizer/co-organizer
- `event_organizers` es genérico y no ayuda a entender el dominio

Campos deseables:
- `id`
- `username`
- `email`
- `password_hash`
- `role` (ORGANIZER / CO_ORGANIZER)
- `enabled`

> En esta iteración no se renombra todavía toda la aplicación a este vocabulario, pero queda como dirección objetivo.

### 2. Evento

**Nombre actual razonable**: `events`

Debe seguir siendo la raíz del agregado.

Debe contener:
- identidad del evento
- configuración principal
- organizador propietario
- snapshot del villano elegido

### 3. Estado de partida

**Observación**: `game_states` es 1:1 con `events`.

**Objetivo ideal a medio plazo**:
- integrar `game_states` dentro de `events` o renombrarlo a algo más explícito como `event_runtime`

No se toca todavía en esta iteración porque implica un refactor más grande y transversal.

### 4. Pods y mesas

**Nombres objetivo recomendados**:
- `pods` → `nodes`
- `player_table` → `tables`

Motivo:
- en la UI ya se habla de **Nodo** y **Mesa**
- `group` es demasiado ambiguo
- `pod` viene de una fase técnica/intermedia del proyecto

No se renombra todavía en esta iteración para no mezclar demasiados cambios estructurales a la vez.

### 5. Co-organización

**Problema actual**

Ahora mismo hay tres piezas para una sola idea:
- `invitations_invitations`
- `invitations_assignments`
- `invitations_invitation_responsibilities`

Eso complica:
- acceso
- login
- listados
- invitaciones pendientes
- revocación
- diagrama ER

**Modelo objetivo**

Una sola tabla:
- `event_invitations`

Con un solo registro por `(event_id, user_id)`.

Campos:
- `id`
- `event_id`
- `user_id`
- `invited_by_user_id`
- `status` (`PENDING`, `ACCEPTED`, `REJECTED`, `REVOKED`)
- `responsibilities` (en la misma fila, no en tabla aparte)
- `created_at`
- `updated_at`

Ventajas:
- una sola fuente de verdad
- aceptar invitación = cambiar estado a `ACCEPTED`
- asignación directa = crear fila directamente en `ACCEPTED`
- revocación = cambiar estado a `REVOKED`
- acceso y login miran exactamente la misma tabla

### 6. Villanos

**Problema actual**
- `events` guarda referencia a `villain_definition_id`
- y además guarda snapshot completo

**Modelo objetivo recomendado**
- mantener catálogo de plantillas de villano
- guardar en el evento solo el snapshot operativo
- considerar eliminar la FK persistente al template una vez copiado el snapshot

Eso reduciría acoplamiento entre catálogo y evento ya creado.

No se aplica todavía en esta iteración.

### 7. Cartas inter-pod

**Separación correcta**
- `inter_pod_card_definitions` = plantillas
- `event_inter_pod_card` = instancias reales durante la partida

Eso **no es redundante**.

**Lo redundante es**:
- `event_inter_pod_card_definitions`

Porque introduce una selección previa por evento que añade complejidad a:
- creación/edición de evento
- validación de carta permitida
- borrado de plantillas
- diagrama ER

**Modelo objetivo recomendado**
- eliminar `event_inter_pod_card_definitions`
- al crear una carta inter-pod, permitir elegir directamente entre las plantillas visibles del organizer
- la instancia de carta ya guarda snapshot suficiente (`cardName`, `cardCategory`, valores de hp/threat e imagen)

No se elimina todavía en esta iteración para no mezclar dos refactors estructurales grandes a la vez.

## Cambios priorizados en esta iteración

1. **Consolidar invitaciones y asignaciones de co-organizador en una sola entidad persistente**
2. **Eliminar la tabla separada de responsabilidades de invitación**
3. **Mantener el resto del modelo estable por ahora**
4. **Dejar documentadas las siguientes simplificaciones recomendadas**
   - quitar `event_inter_pod_card_definitions`
   - renombrar `event_organizers`, `pods`, `player_table`
   - estudiar integrar `game_states` en `events`

## Resultado esperado tras esta iteración

- menos tablas
- menos joins
- menos duplicidad de lógica
- más claridad en control de acceso y login de co-organizador
- diagrama ER más simple y cercano al dominio real
