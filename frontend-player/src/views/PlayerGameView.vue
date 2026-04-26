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

const { connected, connect } = useWebSocket(eventId)
const socketConnected = computed(() => Boolean(unref(connected)))

const eventStructure = ref(null)
const structureLoading = ref(false)

const showDamageDialog = ref(false)
const showThreatDialog = ref(false)
const showInterPodDialog = ref(false)
const selectedInterPodCardId = ref(null)

const interPodCards = ref([])
const interPodCardsLoading = ref(false)
const { getProtectedImageUrl, syncImageUrls } = useProtectedImageUrls('auth-player')

const isFinished = computed(() => gs.eventStatus === 'FINISHED')
const isInProgress = computed(() => gs.eventStatus === 'IN_PROGRESS')
const isWaiting = computed(() => ['DRAFT', 'READY'].includes(gs.eventStatus))
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
const currentPodName = computed(() => currentPod.value?.name || '')
const eventName = computed(() => eventStructure.value?.eventName || '')
const currentGroupLabel = computed(() => normalizeGroupLabel(currentGroup.value?.identifier || auth.groupLabel))
const hasAssignedPod = computed(() => Boolean(currentPod.value))
const currentGroupPendingAssignment = computed(() => Boolean(currentGroup.value && !currentGroup.value.podId))
const playerActionNotice = computed(() => {
  if (isFinished.value) return null

  if (isPaused.value) {
    return {
      icon: '⏸',
      title: 'Partida pausada',
      message: 'No puedes realizar acciones hasta que el organizador la reanude.',
    }
  }

  if (isTimerPendingStart.value) {
    return {
      icon: '⏱',
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

const podSummary = computed(() => {
  if (!eventStructure.value) return ''

  const podCount = pods.value.length
  const groupCount = eventStructure.value.currentGroupCount || 0
  const playerCount = eventStructure.value.assignedPlayerCount || 0
  const pendingCount = eventStructure.value.unassignedGroupCount || 0

  const pieces = [
    `${podCount} ${podCount === 1 ? 'pod' : 'pods'}`,
    `${groupCount} ${groupCount === 1 ? 'mesa' : 'mesas'}`,
    `${playerCount} ${playerCount === 1 ? 'jugador en pod' : 'jugadores en pods'}`,
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
      notif.error('Error al cargar la estructura de pods y mesas')
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

  const hpCurrent = Number(card.hpCurrent)
  if (Number.isFinite(hpCurrent) && hpCurrent <= 0) {
    return 'DERROTADA'
  }

  if (card.status === 'OCULTA') {
    return 'OCULTA'
  }

  return 'REVELADA'
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

// Local timer countdown
let timerInterval = null
let structureInterval = null
let gameStateInterval = null

function startLocalTimer() {
  stopLocalTimer()
  timerInterval = setInterval(() => {
    if (gs.timerStatus === 'RUNNING' && gs.timerRemainingSeconds > 0) {
      gs.timerRemainingSeconds--
    }
  }, 1000)
}

function startStructurePolling() {
  stopStructurePolling()
  structureInterval = setInterval(() => {
    loadStructure({ silent: true })
    loadInterPodCards({ silent: true })
  }, 4000)
}

function startGameStatePolling() {
  stopGameStatePolling()
  gameStateInterval = setInterval(() => {
    if (!socketConnected.value) {
      void loadGameState({ silent: true })
    }
  }, 4000)
}

function stopLocalTimer() {
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
}

function stopStructurePolling() {
  if (structureInterval) {
    clearInterval(structureInterval)
    structureInterval = null
  }
}

function stopGameStatePolling() {
  if (gameStateInterval) {
    clearInterval(gameStateInterval)
    gameStateInterval = null
  }
}

watch(showActions, (nextValue) => {
  if (!nextValue) {
    showDamageDialog.value = false
    showThreatDialog.value = false
    showInterPodDialog.value = false
  }
})

watch(currentPodId, (nextPodId, previousPodId) => {
  if (nextPodId === previousPodId) {
    return
  }

  void loadInterPodCards({ silent: true })
})

watch(socketConnected, (isNowConnected) => {
  if (isNowConnected) {
    void loadGameState({ silent: true })
    void loadStructure({ silent: true })
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
  startStructurePolling()
  startGameStatePolling()
})

onUnmounted(() => {
  stopLocalTimer()
  stopStructurePolling()
  stopGameStatePolling()
})
</script>

<template>
  <div>
    <PlayerHeader :event-name="eventName" :group-label="currentGroupLabel" :connected="socketConnected" />
    <div class="app-content" :class="{ 'app-content--no-actions': !showActions }">
      <div v-if="playerActionNotice" class="pause-overlay">
        <div class="pause-overlay-content">
          <span class="pause-icon">{{ playerActionNotice.icon }}</span>
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
        Cargando pods y mesas…
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

        <div v-if="interPodCards.length" class="card">
          <div class="card-title">Cartas Inter-Pod activas</div>
          <div class="interpod-inline-grid">
            <button
              v-for="card in interPodCards"
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
                <strong>{{ prettyCardName(card.cardName) }}</strong>
                <span class="text-muted" style="font-size: 0.8rem">
                  {{ prettyCardName(resolveInterPodCardStatus(card)) }}
                  <template v-if="card.hpMax"> · HP {{ card.hpCurrent }}/{{ card.hpMax }}</template>
                </span>
                <span class="interpod-inline-link text-muted">Pulsa para ver su detalle</span>
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
          <div class="card-title">Pods y mesas</div>
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
      <button class="btn btn-primary" @click="showDamageDialog = true">Reportar Daño</button>
      <button class="btn btn-danger" @click="showThreatDialog = true">Reportar Amenaza</button>
      <button
        class="btn btn-secondary"
        style="flex: 0 0 auto; padding: 10px 14px"
        @click="openInterPodDialog()"
      >
        Cartas Inter-Pod
      </button>
    </div>

    <!-- Dialogs -->
    <ReportDamageDialog v-if="showDamageDialog" @close="showDamageDialog = false" />
    <ReportThreatDialog v-if="showThreatDialog" @close="showThreatDialog = false" />
    <InterPodAlertDialog
      v-if="showInterPodDialog"
      :pod-id="currentPodId"
      :pod-name="currentPodName"
      :initial-card-id="selectedInterPodCardId"
      @close="closeInterPodDialog"
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
  font-size: 1.6rem;
}
@keyframes pulse-pause {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}
.interpod-inline-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
}
.interpod-inline-card {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--card-bg, #23252b);
  border: 1px solid var(--border-color, #333);
  border-radius: 8px;
  padding: 8px 12px;
  min-width: 160px;
  max-width: 260px;
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}
.interpod-inline-card:hover,
.interpod-inline-card:focus-visible {
  border-color: rgba(212, 175, 55, 0.85);
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.24);
  transform: translateY(-1px);
}
.interpod-inline-card:focus-visible {
  outline: 2px solid rgba(212, 175, 55, 0.95);
  outline-offset: 2px;
}
.interpod-inline-img {
  width: 44px;
  height: 44px;
  object-fit: cover;
  border-radius: 6px;
  flex-shrink: 0;
}
.interpod-inline-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
}
.interpod-inline-link {
  font-size: 0.72rem;
}
.interpod-inline-info strong {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 0.9rem;
}
</style>
