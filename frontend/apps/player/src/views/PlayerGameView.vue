<script setup>
import { ref, computed, onMounted, onUnmounted, unref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useGameStateStore } from '@/stores/gameState'
import { useNotificationsStore } from '@/stores/notifications'
import { useWebSocket } from '@/composables/useWebSocket'
import { useProtectedImageUrls } from '@/composables/useProtectedImageUrls'
import { getGameState } from '@/api/gameState'
import { getEventStructure } from '@/api/setup'
import { getInterPodCards } from '@/api/interPod'
import PlayerHeader from '@/components/PlayerHeader.vue'
import VillainHpCard from '@/components/VillainHpCard.vue'
import ThreatCard from '@/components/ThreatCard.vue'
import TimerDisplay from '@/components/TimerDisplay.vue'
import FinalSummary from '@/components/FinalSummary.vue'
import ReportDamageDialog from '@/components/ReportDamageDialog.vue'
import ReportThreatDialog from '@/components/ReportThreatDialog.vue'
import InterPodAlertDialog from '@/components/InterPodAlertDialog.vue'
import PodOverviewCard from '@/components/PodOverviewCard.vue'

const auth = useAuthStore()
const gs = useGameStateStore()
const notif = useNotificationsStore()
const eventId = auth.eventId

const eventStructure = ref(null)
const structureLoading = ref(false)

const showDamageDialog = ref(false)
const showThreatActionSheet = ref(false)
const threatDialogMode = ref(null)
const showInterPodDialog = ref(false)
const selectedInterPodCardId = ref(null)

const interPodCards = ref([])
const interPodCardsLoading = ref(false)
const { getProtectedImageUrl, syncImageUrls } = useProtectedImageUrls('auth-player')

const isFinished = computed(() => gs.eventStatus === 'FINISHED')
const isInProgress = computed(() => gs.eventStatus === 'IN_PROGRESS')
const isWaiting = computed(() => gs.eventStatus === 'DRAFT')
const isPaused = computed(() => gs.timerStatus === 'PAUSED')
const isTimerPendingStart = computed(() => isInProgress.value && gs.timerStatus === 'READY')

const pods = computed(() => eventStructure.value?.pods || [])
const unassignedGroups = computed(() => eventStructure.value?.unassignedGroups || [])

const currentGroup = computed(() => {
  const assignedGroup = pods.value.flatMap((pod) => pod.groups || []).find((group) => group.id === auth.groupId)
  if (assignedGroup) {
    return assignedGroup
  }
  return unassignedGroups.value.find((group) => group.id === auth.groupId) || null
})

const currentPod = computed(
  () => (currentGroup.value?.podId ? pods.value.find((pod) => pod.id === currentGroup.value.podId) || null : null),
)

const currentPodId = computed(() => currentPod.value?.id || null)
const podScopeReady = computed(() => eventStructure.value !== null)
const currentPodName = computed(() => currentPod.value?.name || '')
const eventName = computed(() => eventStructure.value?.eventName || '')
const currentGroupLabel = computed(() => normalizeGroupLabel(currentGroup.value?.identifier || auth.groupLabel))
const hasAssignedPod = computed(() => Boolean(currentPod.value))
const currentGroupPendingAssignment = computed(() => Boolean(currentGroup.value && !currentGroup.value.podId))
const { connected, realtimeEvent, connect } = useWebSocket(eventId, {
  podId: currentPodId,
  podScopeReady,
})
const socketConnected = computed(() => Boolean(unref(connected)))
const activeInterPodCards = computed(() => interPodCards.value.filter((card) => !isInterPodCardDefeated(card)))
const playerActionNotice = computed(() => {
  if (isFinished.value) return null

  if (isPaused.value) {
    return {
      iconType: 'pause',
      title: 'Partida pausada',
      message: 'No puedes realizar acciones hasta que el organizador la reanude.',
    }
  }

  if (isTimerPendingStart.value) {
    return {
      iconType: 'timer',
      title: 'Contador pendiente de inicio',
      message: 'La partida ya ha comenzado, pero el organizador todavía no ha iniciado el contador. Las acciones están bloqueadas temporalmente.',
    }
  }

  return null
})
const playerActionsLocked = computed(() => Boolean(playerActionNotice.value))
const showActions = computed(
  () => auth.isRepresentative && isInProgress.value && !playerActionsLocked.value && hasAssignedPod.value,
)
const canRemoveThreat = computed(() => Number(gs.schemeThreatCurrent) > 0)

const podSummary = computed(() => {
  if (!eventStructure.value) return ''

  const podCount = pods.value.length
  const groupCount = eventStructure.value.currentGroupCount || 0
  const playerCount = eventStructure.value.assignedPlayerCount || 0
  const pendingCount = eventStructure.value.unassignedGroupCount || 0

  const pieces = [
    `${podCount} ${podCount === 1 ? 'nodo' : 'nodos'}`,
    `${groupCount} ${groupCount === 1 ? 'mesa' : 'mesas'}`,
    `${playerCount} ${playerCount === 1 ? 'jugador' : 'jugadores'}`,
  ]

  if (pendingCount > 0) {
    pieces.push(`${pendingCount} ${pendingCount === 1 ? 'mesa pendiente' : 'mesas pendientes'}`)
  }

  return pieces.join(' · ')
})

function normalizeGroupLabel(label) {
  if (!label) return 'Mesa sin identificar'
  if (/^mesa\b/i.test(label)) return label
  if (/^jugador\b/i.test(label)) return label.replace(/^jugador\b/i, 'Mesa')
  return `Mesa ${label}`
}

function interPodSourceGroupLabel(card) {
  return normalizeGroupLabel(card?.sourceGroupIdentifier)
}

function buildInterPodImageEntries(entries) {
  return entries
    .filter((card) => card?.id != null && card?.imageUrl)
    .map((card) => ({
      id: card.id,
      url: card.imageUrl,
    }))
}

async function loadGameState({ silent = false } = {}) {
  try {
    const { data } = await getGameState(eventId)
    gs.applySnapshot(data)
  } catch {
    if (!silent) {
      notif.error('Error al cargar el estado de la partida')
    }
  }
}

async function loadStructure({ silent = false } = {}) {
  if (!eventId) return

  if (!silent) {
    structureLoading.value = true
  }

  try {
    const { data } = await getEventStructure(eventId)
    eventStructure.value = data
  } catch {
    if (!silent) {
      notif.error('Error al cargar la estructura de nodos y mesas')
    }
  } finally {
    if (!silent) {
      structureLoading.value = false
    }
  }
}

async function loadInterPodCards({ silent = false } = {}) {
  const podId = currentPodId.value
  if (!podId) {
    interPodCards.value = []
    await syncImageUrls([])
    return
  }

  if (!silent) {
    interPodCardsLoading.value = true
  }

  try {
    const { data } = await getInterPodCards(eventId, podId)
    interPodCards.value = data || []
    await syncImageUrls(buildInterPodImageEntries(interPodCards.value))
  } catch {
    /* ignore */
  } finally {
    if (!silent) {
      interPodCardsLoading.value = false
    }
  }
}

function prettyCardName(name) {
  if (!name) return '-'
  return name
    .replaceAll('_', ' ')
    .toLowerCase()
    .replaceAll(/(^|\s)\p{L}/gu, (letter) => letter.toUpperCase())
}

function resolveInterPodCardStatus(card) {
  if (!card) return '-'

  const currentValue = Number(card.metricCurrent)
  if (Number.isFinite(currentValue) && currentValue <= 0) {
    return 'DERROTADA'
  }

  if (card.status === 'OCULTA') {
    return 'OCULTA'
  }

  return 'REVELADA'
}

function isInterPodCardDefeated(card) {
  return resolveInterPodCardStatus(card) === 'DERROTADA'
}

function isMinionCard(card) {
  return card?.cardCategory === 'ESBIRRO'
}

function cardMetricText(card) {
  if (!card) return '-'
  const label = isMinionCard(card) ? 'HP' : 'Amenaza'
  return `${label} ${card.metricCurrent}/${card.metricMax}`
}

function cardProgressPercent(card) {
  if (!card) return 0
  const max = Number(card.metricMax)
  return max > 0 ? Math.max(0, Math.min(100, Math.round((Number(card.metricCurrent) / max) * 100))) : 0
}

function cardProgressColor(card) {
  if (!card) return '#555'
  if (isMinionCard(card)) {
    const pct = cardProgressPercent(card)
    if (pct > 60) return '#4caf50'
    if (pct > 30) return '#ff9800'
    return '#f44336'
  }
  return '#e57373'
}

function handleImageLoadError(event) {
  event.target.style.display = 'none'
}

function openInterPodDialog(cardId = null) {
  selectedInterPodCardId.value = cardId == null ? null : Number(cardId)
  showInterPodDialog.value = true
}

function closeInterPodDialog() {
  showInterPodDialog.value = false
  selectedInterPodCardId.value = null
}

function handleInterPodRefreshRequest() {
  if (socketConnected.value) {
    return
  }

  void loadInterPodCards({ silent: true })
}

function closeThreatActionSheet() {
  showThreatActionSheet.value = false
}

function openThreatDialog(mode) {
  threatDialogMode.value = mode
  closeThreatActionSheet()
}

function closeThreatDialog() {
  threatDialogMode.value = null
}

function handleThreatActionSheetOverlayClick(event) {
  if (event.target === event.currentTarget) {
    closeThreatActionSheet()
  }
}

function isRelevantInterPodRealtimeEvent(nextRealtimeEvent) {
  if (!nextRealtimeEvent?.operation?.startsWith('INTER_POD_CARD_')) {
    return false
  }

  if (nextRealtimeEvent?.targetPodId == null) {
    return true
  }

  return String(nextRealtimeEvent.targetPodId) === String(currentPodId.value)
}

// Local timer countdown
let timerInterval = null

function startLocalTimer() {
  stopLocalTimer()
  timerInterval = setInterval(() => {
    if (gs.timerStatus === 'RUNNING' && gs.timerRemainingSeconds > 0) {
      gs.timerRemainingSeconds--
    }
  }, 1000)
}

function stopLocalTimer() {
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
}

watch(showActions, (nextValue) => {
  if (!nextValue) {
    showDamageDialog.value = false
    showThreatActionSheet.value = false
    threatDialogMode.value = null
    showInterPodDialog.value = false
  }
})

watch(currentPodId, (nextPodId, previousPodId) => {
  if (nextPodId === previousPodId) {
    return
  }

  void loadInterPodCards({ silent: true })
})

watch(
  () => realtimeEvent?.value,
  (nextRealtimeEvent) => {
    const operation = nextRealtimeEvent?.operation
    if (!operation) {
      return
    }

    if (operation === 'EVENT_STRUCTURE_UPDATED') {
      void loadStructure({ silent: true })
      return
    }

    if (isRelevantInterPodRealtimeEvent(nextRealtimeEvent)) {
      void loadInterPodCards({ silent: true })
    }
  },
)

watch(socketConnected, (isNowConnected) => {
  if (isNowConnected) {
    void loadGameState({ silent: true })
    void loadStructure({ silent: true })
    void loadInterPodCards({ silent: true })
    return
  }

  void loadGameState({ silent: true })
})

onMounted(() => {
  void loadGameState()
  void loadStructure()
  void loadInterPodCards()
  connect()
  startLocalTimer()
})

onUnmounted(() => {
  stopLocalTimer()
})
</script>

<template>
  <div>
    <PlayerHeader :event-name="eventName" :group-label="currentGroupLabel" :connected="socketConnected" />
    <div class="app-content" :class="{ 'app-content--no-actions': !showActions }">
      <div v-if="playerActionNotice" class="pause-overlay">
        <div class="pause-overlay-content">
          <span class="pause-icon" aria-hidden="true">
            <svg
              v-if="playerActionNotice.iconType === 'pause'"
              class="pause-icon-svg"
              viewBox="0 0 24 24"
              xmlns="http://www.w3.org/2000/svg"
            >
              <rect x="6.5" y="5" width="4.25" height="14" rx="1.5" fill="currentColor" />
              <rect x="13.25" y="5" width="4.25" height="14" rx="1.5" fill="currentColor" />
            </svg>
            <svg
              v-else-if="playerActionNotice.iconType === 'timer'"
              class="pause-icon-svg"
              viewBox="0 0 24 24"
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <circle cx="12" cy="13" r="7" />
              <path d="M12 13V9.5" />
              <path d="M12 13L14.5 15.5" />
              <path d="M9 2H15" />
              <path d="M12 6V2" />
            </svg>
            <template v-else>{{ playerActionNotice.icon }}</template>
          </span>
          <strong>{{ playerActionNotice.title }}</strong>
          <span>{{ playerActionNotice.message }}</span>
        </div>
      </div>

      <div v-if="currentGroupPendingAssignment" class="card">
        <div class="card-title">Pendiente de asignación</div>
        <p class="text-muted" style="margin-bottom: 0">
          Tu mesa aún no está asignada a un pod. El organizador debe ubicarla antes de que puedas jugar.
        </p>
      </div>

      <!-- Waiting state -->
      <div v-if="isWaiting" class="waiting-card card">
        <span class="pulse" />
        <span>Esperando a que el organizador inicie la partida…</span>
      </div>

      <div v-if="structureLoading && !eventStructure" class="card text-center text-muted">
        Cargando nodos y mesas…
      </div>

      <!-- Final summary -->
      <FinalSummary v-if="isFinished && gs.finalSummary" :summary="gs.finalSummary" />

      <!-- Game state -->
      <template v-if="isInProgress || isFinished">
        <TimerDisplay
          v-if="gs.timerStatus !== 'NOT_CONFIGURED'"
          :remaining="gs.timerRemainingSeconds"
          :status="gs.timerStatus"
        />
        <VillainHpCard
          :name="gs.villainName || 'Villano desconocido'"
          :current="gs.villainHpCurrent"
          :max="gs.villainHpMax"
        />
        <ThreatCard :current="gs.schemeThreatCurrent" :target="gs.schemeThreatTarget" />

        <div v-if="activeInterPodCards.length" class="card">
          <div class="card-title">Cartas Inter-Nodo activas</div>
          <div class="interpod-inline-flex">
            <button
              v-for="card in activeInterPodCards"
              :key="card.id"
              type="button"
              class="interpod-inline-card"
              :data-testid="`player-interpod-card-${card.id}`"
              @click="openInterPodDialog(card.id)"
            >
              <img
                v-if="getProtectedImageUrl(card.id)"
                :src="getProtectedImageUrl(card.id)"
                alt=""
                class="interpod-inline-img"
                @error="handleImageLoadError"
              />
              <div class="interpod-inline-info">
                <div class="interpod-inline-header">
                  <strong class="interpod-inline-name">{{ prettyCardName(card.cardName) }}</strong>
                  <span
                    v-if="resolveInterPodCardStatus(card) !== 'REVELADA'"
                    class="interpod-status-badge"
                    :class="`interpod-status--${resolveInterPodCardStatus(card).toLowerCase()}`"
                  >{{ prettyCardName(resolveInterPodCardStatus(card)) }}</span>
                </div>
                <span class="interpod-inline-link text-muted">{{ interPodSourceGroupLabel(card) }}</span>
                <span class="interpod-metric-label">{{ cardMetricText(card) }}</span>
                <div class="interpod-progress-track">
                  <div
                    class="interpod-progress-fill"
                    :style="{ width: cardProgressPercent(card) + '%', background: cardProgressColor(card) }"
                  />
                </div>
              </div>
            </button>
          </div>
        </div>
      </template>

      <template v-else>
        <div v-if="eventStructure && !pods.length && !unassignedGroups.length" class="card text-center text-muted">
          El organizador todavía no ha configurado pods o mesas.
        </div>
      </template>

      <template v-if="eventStructure">
        <div class="card">
          <div class="card-title">Nodos y mesas</div>
          <div class="pod-overview-summary">{{ podSummary }}</div>
          <p class="text-muted mt-1" style="font-size: 0.82rem">
            Tu mesa aparece resaltada para localizarla rápidamente.
          </p>
        </div>

        <div v-if="pods.length" class="pod-overview-grid">
          <PodOverviewCard
            v-for="pod in pods"
            :key="pod.id"
            :pod="pod"
            :current-group-id="currentGroup ? currentGroup.id : null"
          />
        </div>

        <div v-if="unassignedGroups.length" class="card">
          <div class="card-title">Mesas pendientes de asignación</div>
          <ul style="margin: 0; padding-left: 20px">
            <li v-for="group in unassignedGroups" :key="group.id" style="margin-bottom: 6px">
              <strong>{{ group.identifier }}</strong>
              <span class="text-muted"> · {{ group.playerCount }} {{ group.playerCount === 1 ? 'jugador' : 'jugadores' }}</span>
              <span v-if="group.id === currentGroup?.id" class="badge badge-ready" style="margin-left: 8px">
                Tu mesa
              </span>
            </li>
          </ul>
        </div>
      </template>
    </div>

    <!-- Action bar (representative only, in progress only) -->
    <div v-if="showActions" class="action-bar">
      <button type="button" class="btn btn-primary" @click="showDamageDialog = true">Reportar Daño</button>
      <button
        type="button"
        class="btn btn-danger"
        data-testid="threat-actions-trigger"
        @click="showThreatActionSheet = true"
      >
        Amenaza
      </button>
      <button
        type="button"
        class="btn btn-secondary"
        style="flex: 0 0 auto; padding: 10px 14px"
        @click="openInterPodDialog()"
      >
        Cartas Inter-Nodo
      </button>
    </div>

    <div
      v-if="showThreatActionSheet"
      class="modal-overlay"
      data-testid="threat-action-sheet"
      @click="handleThreatActionSheetOverlayClick"
    >
      <div class="modal-panel threat-action-sheet" @click.stop>
        <div class="modal-title">Gestionar Amenaza</div>
        <p class="threat-action-sheet__message">
          Selecciona si quieres añadir o quitar amenaza del plan principal.
        </p>

        <div class="threat-action-sheet__buttons">
          <button
            type="button"
            class="btn btn-danger btn-block btn-lg"
            data-testid="threat-action-add"
            @click="openThreatDialog('add')"
          >
            Añadir amenaza
          </button>
          <button
            type="button"
            class="btn btn-secondary btn-block btn-lg"
            data-testid="threat-action-remove"
            :disabled="!canRemoveThreat"
            @click="openThreatDialog('remove')"
          >
            Quitar amenaza
          </button>
        </div>

        <p v-if="!canRemoveThreat" class="text-muted threat-action-sheet__hint">
          No hay amenaza en el plan principal para retirar ahora mismo.
        </p>

        <button type="button" class="btn btn-secondary btn-block mt-2" @click="closeThreatActionSheet">
          Cancelar
        </button>
      </div>
    </div>

    <!-- Dialogs -->
    <ReportDamageDialog v-if="showDamageDialog" @close="showDamageDialog = false" />
    <ReportThreatDialog v-if="threatDialogMode" :mode="threatDialogMode" @close="closeThreatDialog" />
    <InterPodAlertDialog
      v-if="showInterPodDialog"
      :pod-id="currentPodId"
      :pod-name="currentPodName"
      :initial-card-id="selectedInterPodCardId"
      :cards="interPodCards"
      :cards-loading="interPodCardsLoading"
      :get-image-url="getProtectedImageUrl"
      :realtime-connected="socketConnected"
      @close="closeInterPodDialog"
      @refresh-requested="handleInterPodRefreshRequest"
    />
  </div>
</template>

<style scoped>
.pause-overlay {
  position: sticky;
  top: 0;
  z-index: 50;
  background: #b8860b;
  color: #1a1a1a;
  padding: 14px 16px;
  border-radius: 10px;
  margin-bottom: 12px;
  text-align: center;
  box-shadow: 0 2px 12px rgba(184, 134, 11, 0.4);
  animation: pulse-pause 2s ease-in-out infinite;
}
.pause-overlay-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.pause-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 1.6rem;
}
.pause-icon-svg {
  width: 1.35rem;
  height: 1.35rem;
  display: block;
}
@keyframes pulse-pause {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}
.interpod-inline-flex {
  display: grid;
  gap: 10px;
  margin-top: 8px;
  
  /* Por defecto: 1 sola columna */
  grid-template-columns: repeat(1, minmax(0, 1fr));
}

/* A partir de 800px: 2 columnas idénticas */
@media (min-width: 800px) {
  .interpod-inline-flex {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* A partir de 1200px: 3 columnas idénticas */
@media (min-width: 1200px) {
  .interpod-inline-flex {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.interpod-inline-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--border-color, #3a3c44);
  border-radius: 10px;
  padding: 5px 10px 5px 5px;
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.2s ease, transform 0.15s ease, box-shadow 0.2s ease;
  
  /* Ya no necesitas width, ni flex-grow, ni cálculos aquí. 
     El contenedor Grid se encarga de darles el tamaño exacto. */
}
.interpod-inline-card:hover,
.interpod-inline-card:focus-visible {
  border-color: rgba(212, 175, 55, 0.85);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.3);
  transform: translateY(-2px);
}
.interpod-inline-card:focus-visible {
  outline: 2px solid rgba(212, 175, 55, 0.95);
  outline-offset: 2px;
}
.interpod-inline-img {
  width: 80px;
  height: 80px;
  object-fit: scale-down;
  border-radius: 6px;
  flex-shrink: 0;
  margin-top: 2px;
}
.interpod-inline-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow: hidden;
  flex: 1;
  min-width: 0;
}
.interpod-inline-header {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.interpod-inline-name {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 0.88rem;
  line-height: 1.3;
}
.interpod-status-badge {
  font-size: 0.68rem;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  flex-shrink: 0;
  letter-spacing: 0.02em;
}
.interpod-status--derrotada {
  background: #5a1a1a;
  color: #ff9090;
}
.interpod-status--oculta {
  background: #303035;
  color: #d8d8d8;
}
.interpod-progress-track {
  height: 5px;
  background: rgba(128, 128, 128, 0.2);
  border-radius: 3px;
  overflow: hidden;
}
.interpod-progress-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease, background 0.4s ease;
}
.interpod-metric-label {
  font-size: 0.74rem;
  color: var(--c-text-muted, #999);
  line-height: 1.2;
}
.interpod-inline-link {
  font-size: 0.8rem;
}
.threat-action-sheet__message {
  margin-bottom: 16px;
  color: var(--c-text-muted);
  text-align: center;
  line-height: 1.45;
}
.threat-action-sheet__buttons {
  display: grid;
  gap: 10px;
}
.threat-action-sheet__hint {
  margin-top: 12px;
  text-align: center;
  font-size: 0.82rem;
  line-height: 1.4;
}
</style>
