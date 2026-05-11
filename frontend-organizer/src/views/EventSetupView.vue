<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { assignGroupToPod, createPod, getEventStructure } from '@/api/setup'
import GroupItem from '@/components/GroupItem.vue'
import PodCard from '@/components/PodCard.vue'
import { useNotificationsStore } from '@/stores/notifications'

const route = useRoute()
const notif = useNotificationsStore()
const eventId = computed(() => route.params.eventId)

const structure = ref(null)
const loading = ref(true)
const creatingPod = ref(false)
const movingGroupId = ref(null)
const activeDragGroup = ref(null)
const activeDropColumn = ref(null)
const newPodName = ref('')

let refreshInterval = null

const pods = computed(() => structure.value?.pods || [])
const pendingGroups = computed(() => structure.value?.unassignedGroups || [])
const hasPendingGroups = computed(() => pendingGroups.value.length > 0)

const assignedGroupCount = computed(() => {
  const total = structure.value?.currentGroupCount ?? 0
  const unassigned = structure.value?.unassignedGroupCount ?? 0
  return total - unassigned
})
const groupAssignedPercent = computed(() => {
  const total = structure.value?.currentGroupCount ?? 0
  return total === 0 ? 0 : Math.round((assignedGroupCount.value / total) * 100)
})
const playerAssignedPercent = computed(() => {
  const total = structure.value?.configuredPlayerCount ?? 0
  const assigned = structure.value?.assignedPlayerCount ?? 0
  return total === 0 ? 0 : Math.round((assigned / total) * 100)
})
const groupsReady = computed(() => (structure.value?.unassignedGroupCount ?? 1) === 0)
const playersReady = computed(() => (structure.value?.unassignedPlayerCount ?? 1) === 0)

function computeNextSortOrder(podList = pods.value) {
  return podList.reduce((maxSortOrder, pod) => Math.max(maxSortOrder, Number(pod.sortOrder ?? -1)), -1) + 1
}

function applySuggestedPodDefaults() {
  const suggestedPodName = structure.value?.suggestedPodName || `Pod ${(pods.value.length || 0) + 1}`
  if (!newPodName.value.trim()) {
    newPodName.value = suggestedPodName
  }
}

async function load({ silent = false } = {}) {
  if (!silent) loading.value = true
  try {
    const { data } = await getEventStructure(eventId.value)
    structure.value = data
    applySuggestedPodDefaults()
  } catch {
    if (!silent) {
      notif.error('Error al cargar la estructura')
    }
  } finally {
    if (!silent) loading.value = false
  }
}

async function handleCreatePod() {
  const resolvedName = newPodName.value.trim() || structure.value?.suggestedPodName || `Pod ${(pods.value.length || 0) + 1}`

  creatingPod.value = true
  try {
    await createPod(eventId.value, {
      name: resolvedName,
      sortOrder: computeNextSortOrder(),
    })
    newPodName.value = ''
    notif.success('Pod creado')
    await load({ silent: true })
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al crear pod')
  } finally {
    creatingPod.value = false
  }
}

function clearDragState() {
  activeDragGroup.value = null
  activeDropColumn.value = null
}

function resolveColumnKey(podId) {
  return podId == null ? 'unassigned' : String(podId)
}

function handleGroupDragStart(group) {
  if (movingGroupId.value) return
  activeDragGroup.value = {
    id: group.id,
    identifier: group.identifier,
    podId: group.podId ?? null,
  }
  activeDropColumn.value = null
}

function handleGroupDragEnd() {
  Promise.resolve().then(() => {
    if (!movingGroupId.value) {
      clearDragState()
    }
  })
}

function setActiveDropColumn(targetPodId) {
  if (!activeDragGroup.value) return
  const targetKey = resolveColumnKey(targetPodId)
  const currentKey = resolveColumnKey(activeDragGroup.value.podId)
  activeDropColumn.value = targetKey === currentKey ? null : targetKey
}

function handleUnassignedDragOver() {
  setActiveDropColumn(null)
}

function handlePodDragOver(pod) {
  setActiveDropColumn(pod?.id ?? null)
}

async function moveActiveGroup(targetPodId, targetLabel) {
  if (!activeDragGroup.value) return

  const draggedGroup = activeDragGroup.value
  const currentPodId = draggedGroup.podId ?? null
  const nextPodId = targetPodId ?? null

  if (currentPodId === nextPodId) {
    clearDragState()
    return
  }

  movingGroupId.value = draggedGroup.id
  try {
    await assignGroupToPod(eventId.value, draggedGroup.id, nextPodId)
    notif.success(
      nextPodId == null
        ? `${draggedGroup.identifier} ha quedado sin pod asignado.`
        : `${draggedGroup.identifier} se ha movido a ${targetLabel}.`,
    )
    await load({ silent: true })
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al mover la mesa')
  } finally {
    movingGroupId.value = null
    clearDragState()
  }
}

async function handleUnassignedDrop() {
  await moveActiveGroup(null, 'Sin asignar')
}

async function handlePodDrop(pod) {
  await moveActiveGroup(pod?.id ?? null, pod?.name || 'este pod')
}

function startAutoRefresh() {
  stopAutoRefresh()
  refreshInterval = setInterval(() => {
    if (creatingPod.value || movingGroupId.value || activeDragGroup.value) return
    load({ silent: true })
  }, 4000)
}

function stopAutoRefresh() {
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
}

onMounted(() => {
  load()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<template>
  <div>
    <h1 class="page-title">Configuración - Pods y Mesas</h1>

    <div v-if="loading" class="text-muted">Cargando…</div>

    <template v-else-if="structure">
      <div class="setup-status mb-2">
        <!-- Mesas -->
        <div class="status-metric">
          <div class="status-metric__header">
            <span class="status-metric__label">Mesas asignadas</span>
            <span class="badge" :class="groupsReady ? 'badge-ready' : 'badge-finished'">{{ groupsReady ? 'Listo' : 'Pendientes' }}</span>
          </div>
          <div class="status-metric__fraction">
            <span class="status-metric__current">{{ assignedGroupCount }}</span>
            <span class="status-metric__separator"> / </span>
            <span class="status-metric__total">{{ structure.currentGroupCount }}</span>
          </div>
          <div class="status-metric__bar">
            <div
              class="status-metric__fill"
              :class="groupsReady ? 'status-metric__fill--ready' : 'status-metric__fill--pending'"
              :style="{ width: groupAssignedPercent + '%' }"
            />
          </div>
          <div class="status-metric__sub">
            <span v-if="structure.unassignedGroupCount > 0" class="text-muted">
              {{ structure.unassignedGroupCount }} mesa{{ structure.unassignedGroupCount !== 1 ? 's' : '' }} sin asignar
            </span>
            <span v-else class="status-metric__sub--ok">Todas las mesas están en un pod</span>
          </div>
        </div>

        <!-- Jugadores -->
        <div class="status-metric">
          <div class="status-metric__header">
            <span class="status-metric__label">Jugadores en pods</span>
            <span class="badge" :class="playersReady ? 'badge-ready' : 'badge-finished'">{{ playersReady ? 'Listo' : 'Pendientes' }}</span>
          </div>
          <div class="status-metric__fraction">
            <span class="status-metric__current">{{ structure.assignedPlayerCount }}</span>
            <span class="status-metric__separator"> / </span>
            <span class="status-metric__total">{{ structure.configuredPlayerCount }}</span>
          </div>
          <div class="status-metric__bar">
            <div
              class="status-metric__fill"
              :class="playersReady ? 'status-metric__fill--ready' : 'status-metric__fill--pending'"
              :style="{ width: playerAssignedPercent + '%' }"
            />
          </div>
          <div class="status-metric__sub">
            <span v-if="structure.unassignedPlayerCount > 0" class="text-muted">
              {{ structure.unassignedPlayerCount }} jugador{{ structure.unassignedPlayerCount !== 1 ? 'es' : '' }} sin pod
            </span>
            <span v-else class="status-metric__sub--ok">Todos los jugadores están en pods</span>
          </div>
        </div>

        <!-- Amenaza target -->
        <div class="status-stat">
          <span class="status-stat__label">Amenaza target</span>
          <span class="status-stat__value">{{ structure.schemeThreatTarget }}</span>
          <span class="status-stat__hint text-muted">Objetivo de amenaza global</span>
        </div>
      </div>

      <section class="card create-pod-card" data-testid="create-pod-card">
        <div class="card-header create-pod-card__header">
          <div>
            <h3>Nuevo pod</h3>
            <p class="text-muted create-pod-card__copy">
              Crea los pods primero y luego reparte las mesas arrastrándolas entre columnas.
            </p>
          </div>
        </div>

        <form class="create-pod-card__form" @submit.prevent="handleCreatePod">
          <div class="form-group" style="margin-bottom: 0">
            <label for="newPodName">Nombre del pod</label>
            <input
              id="newPodName"
              v-model="newPodName"
              class="form-control"
              maxlength="80"
              :placeholder="structure.suggestedPodName || 'Pod 1'"
            />
          </div>

          <button class="btn btn-primary create-pod-card__button" :disabled="creatingPod">
            {{ creatingPod ? 'Creando…' : 'Crear pod' }}
          </button>
        </form>
      </section>

      <section class="card board-card">
        <div class="card-header board-card__header">
          <div>
            <h3>Reparto de mesas</h3>
            <p class="text-muted board-card__copy">
              Arrastra cada mesa entre columnas para distribuirla en pods. Si la sueltas en
              <strong>Sin asignar</strong>, quedará desasignada.
            </p>
          </div>
          <span v-if="hasPendingGroups" class="board-card__warning-badge">
            Hay mesas pendientes de asignación
          </span>
        </div>

        <div class="setup-board">
          <section
            class="setup-column setup-column--unassigned"
            :class="{
              'setup-column--drop-active': activeDropColumn === 'unassigned',
            }"
            data-testid="unassigned-column"
            @dragover.prevent="handleUnassignedDragOver"
            @dragenter.prevent="handleUnassignedDragOver"
            @drop.prevent="handleUnassignedDrop"
          >
            <div class="setup-column__header">
              <div>
                <h3>Sin asignar</h3>
                <span class="text-muted">
                  {{ structure.unassignedGroupCount }} mesas · {{ structure.unassignedPlayerCount }} jugadores
                </span>
              </div>
              <span class="badge badge-draft">Pendientes</span>
            </div>

            <p class="setup-column__description text-muted">
              Estas mesas aún no pertenecen a ningún pod. Mientras exista alguna pendiente, la partida no debería iniciarse.
            </p>

            <div v-if="pendingGroups.length" class="setup-column__groups">
              <GroupItem
                v-for="group in pendingGroups"
                :key="group.id"
                :group="group"
                :event-id="eventId"
                :is-dragging="activeDragGroup?.id === group.id"
                :moving="movingGroupId === group.id"
                @changed="load({ silent: true })"
                @drag-start="handleGroupDragStart"
                @drag-end="handleGroupDragEnd"
              />
            </div>
            <div
              v-else
              class="setup-column__empty"
              :class="{ 'setup-column__empty--active': activeDropColumn === 'unassigned' }"
            >
              <span class="text-muted">No hay mesas pendientes. Todo está ya repartido en pods.</span>
            </div>
          </section>

          <PodCard
            v-for="pod in pods"
            :key="pod.id"
            :pod="pod"
            :event-id="eventId"
            :drop-active="activeDropColumn === String(pod.id)"
            :active-drag-group-id="activeDragGroup?.id ?? null"
            :moving-group-id="movingGroupId"
            @changed="load({ silent: true })"
            @group-drag-start="handleGroupDragStart"
            @group-drag-end="handleGroupDragEnd"
            @drag-over-column="handlePodDragOver"
            @drop-group="handlePodDrop"
          />
        </div>

        <p v-if="!pods.length" class="text-muted mt-2">
          Aún no hay pods creados. Crea el primero para empezar a repartir mesas.
        </p>
      </section>
    </template>

    <div v-else class="text-muted">No se pudo cargar la estructura. Comprueba la conexión con el servidor.</div>
  </div>
</template>

<style scoped>
.create-pod-card__header,
.board-card__header {
  align-items: flex-start;
  gap: 16px;
}

.create-pod-card__copy,
.board-card__copy {
  margin-top: 6px;
  font-size: 0.9rem;
  max-width: 64ch;
}

.create-pod-card__form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: end;
}

.create-pod-card__button {
  min-width: 140px;
  justify-content: center;
}

.board-card {
  overflow: hidden;
}

.board-card__warning-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--c-warning) 16%, var(--c-surface));
  color: var(--c-text);
  font-size: 0.8rem;
  font-weight: 600;
}

.setup-board {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
  align-items: start;
}

.setup-column {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
  padding: 18px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: var(--c-surface);
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.setup-column--unassigned {
  background: color-mix(in srgb, var(--c-surface-alt) 35%, var(--c-surface));
}

.setup-column--drop-active {
  border-color: color-mix(in srgb, var(--c-primary) 80%, var(--c-border));
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--c-primary) 30%, transparent), 0 14px 24px rgba(0, 0, 0, 0.14);
  transform: translateY(-2px);
}

.setup-column__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.setup-column__description {
  font-size: 0.85rem;
}

.setup-column__groups {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.setup-column__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  padding: 18px;
  border: 1px dashed var(--c-border);
  border-radius: var(--radius);
  background: color-mix(in srgb, var(--c-surface-alt) 85%, transparent);
  text-align: center;
}

.setup-column__empty--active {
  border-color: var(--c-primary);
  background: color-mix(in srgb, var(--c-primary) 10%, var(--c-surface));
}

/* ── Setup status panel ────────────────────────────── */

.setup-status {
  display: flex;
  gap: 12px;
  align-items: stretch;
}

.status-metric {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
  background: var(--c-surface-alt);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
}

.status-metric__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.status-metric__label {
  font-size: 0.78rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--c-text-muted);
}

.status-metric__fraction {
  line-height: 1;
}

.status-metric__current {
  font-size: 2rem;
  font-weight: 700;
  color: var(--c-text);
}

.status-metric__separator {
  font-size: 1.2rem;
  font-weight: 400;
  color: var(--c-text-muted);
}

.status-metric__total {
  font-size: 1.2rem;
  font-weight: 500;
  color: var(--c-text-muted);
}

.status-metric__bar {
  height: 6px;
  border-radius: 3px;
  background: color-mix(in srgb, var(--c-border) 60%, transparent);
  overflow: hidden;
}

.status-metric__fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease, background-color 0.3s ease;
}

.status-metric__fill--ready {
  background: var(--c-success);
}

.status-metric__fill--pending {
  background: var(--c-warning);
}

.status-metric__sub {
  font-size: 0.82rem;
  min-height: 1.2em;
}

.status-metric__sub--ok {
  color: var(--c-success);
  font-weight: 500;
}

.status-stat {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: 16px 18px;
  background: var(--c-surface-alt);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  min-width: 130px;
  text-align: center;
}

.status-stat__label {
  font-size: 0.78rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--c-text-muted);
}

.status-stat__value {
  font-size: 2rem;
  font-weight: 700;
  color: var(--c-threat);
  line-height: 1;
}

.status-stat__hint {
  font-size: 0.78rem;
}

@media (max-width: 720px) {
  .create-pod-card__form {
    grid-template-columns: 1fr;
  }

  .create-pod-card__button {
    width: 100%;
  }

  .setup-status {
    flex-direction: column;
  }

  .status-stat {
    text-align: left;
    min-width: unset;
  }
}
</style>
