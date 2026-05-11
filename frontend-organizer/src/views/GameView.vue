<script setup>
import { ref, computed, onMounted, onUnmounted, unref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useGameStateStore } from '@/stores/gameState'
import { useNotificationsStore } from '@/stores/notifications'
import { useWebSocket } from '@/composables/useWebSocket'
import { getEventStructure, assignGroupToPod } from '@/api/setup'
import {
  startGame,
  reportVillainDamage,
  reportThreat,
  manualUpdateVillainHp,
  manualUpdateThreat,
  startTimer,
  pauseTimer,
  resumeTimer,
  adjustTimer,
} from '@/api/gameState'
import VillainHpBar from '@/components/VillainHpBar.vue'
import ThreatBar from '@/components/ThreatBar.vue'
import GameTimer from '@/components/GameTimer.vue'
import FinalSummary from '@/components/FinalSummary.vue'
import InterPodAlerts from '@/components/InterPodAlerts.vue'

const route = useRoute()
const gs = useGameStateStore()
const notif = useNotificationsStore()
const eventId = computed(() => route.params.eventId)

const { connected, realtimeEvent, connect } = useWebSocket(eventId.value)
const socketConnected = computed(() => Boolean(unref(connected)))

// Report damage form
const dmgCode = ref('')
const dmgAmount = ref(1)
const dmgLoading = ref(false)

// Report threat form
const threatCode = ref('')
const threatAmount = ref(1)
const threatLoading = ref(false)

// Manual corrections
const manualHp = ref(0)
const manualThreat = ref(0)
const eventStructure = ref(null)
const structureLoading = ref(false)

const isFinished = computed(() => gs.eventStatus === 'FINISHED')
const isInProgress = computed(() => gs.eventStatus === 'IN_PROGRESS')
const pendingGroups = computed(() => eventStructure.value?.unassignedGroups || [])
const canStartGame = computed(() => eventStructure.value !== null && pendingGroups.value.length === 0)

const pendingGroupsSummary = computed(() => pendingGroups.value.map((group) => group.identifier).join(', '))

const pods = computed(() => eventStructure.value?.pods || [])
const assignPodByGroup = ref({})
const assigningGroupId = ref(null)

async function load() {
  try {
    await gs.fetchGameState(eventId.value)
    await loadStructure({ silent: true })
    manualHp.value = gs.villainHpCurrent
    manualThreat.value = gs.schemeThreatCurrent
  } catch {
    notif.error('Error al cargar el estado de la partida')
  }
}

async function loadStructure({ silent = false } = {}) {
  if (!silent) {
    structureLoading.value = true
  }

  try {
    const { data } = await getEventStructure(eventId.value)
    eventStructure.value = data
  } catch {
    if (!silent) {
      notif.error('Error al cargar la estructura del evento')
    }
  } finally {
    if (!silent) {
      structureLoading.value = false
    }
  }
}

async function handleStart() {
  if (!canStartGame.value) {
    notif.error('No puedes iniciar la partida mientras haya mesas pendientes de asignación.')
    return
  }

  try {
    const { data } = await startGame(eventId.value)
    gs.applySnapshot(data.snapshot)
    notif.success('¡Partida iniciada!')
    await loadStructure({ silent: true })
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al iniciar')
  }
}

async function handleAssignGroup(groupId) {
  const podId = assignPodByGroup.value[groupId]
  if (!podId) {
    notif.error('Selecciona un pod antes de asignar.')
    return
  }
  assigningGroupId.value = groupId
  try {
    await assignGroupToPod(eventId.value, groupId, podId)
    notif.success('Mesa asignada al pod')
    await loadStructure({ silent: true })
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al asignar mesa')
  } finally {
    assigningGroupId.value = null
  }
}

async function handleReportDamage() {
  dmgLoading.value = true
  try {
    const { data } = await reportVillainDamage(eventId.value, {
      representativeCode: dmgCode.value,
      damage: dmgAmount.value,
    })
    gs.applySnapshot(data.snapshot)
    notif.success(`Daño registrado: ${dmgAmount.value}`)
    dmgAmount.value = 1
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al reportar daño')
  } finally {
    dmgLoading.value = false
  }
}

async function handleReportThreat() {
  threatLoading.value = true
  try {
    const { data } = await reportThreat(eventId.value, {
      representativeCode: threatCode.value,
      threat: threatAmount.value,
    })
    gs.applySnapshot(data.snapshot)
    notif.success(`Amenaza registrada: ${threatAmount.value}`)
    threatAmount.value = 1
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al reportar amenaza')
  } finally {
    threatLoading.value = false
  }
}

async function handleManualHp() {
  try {
    const { data } = await manualUpdateVillainHp(eventId.value, manualHp.value)
    gs.applySnapshot(data.snapshot)
    notif.success('HP del villano corregido')
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error')
  }
}

async function handleManualThreat() {
  try {
    const { data } = await manualUpdateThreat(eventId.value, manualThreat.value)
    gs.applySnapshot(data.snapshot)
    notif.success('Amenaza corregida')
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error')
  }
}

async function handleTimerStart() {
  try {
    const { data } = await startTimer(eventId.value)
    gs.applySnapshot(data.snapshot)
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error')
  }
}

async function handleTimerPause() {
  try {
    const { data } = await pauseTimer(eventId.value)
    gs.applySnapshot(data.snapshot)
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error')
  }
}

async function handleTimerResume() {
  try {
    const { data } = await resumeTimer(eventId.value)
    gs.applySnapshot(data.snapshot)
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error')
  }
}

async function handleTimerAdjust(delta) {
  try {
    const { data } = await adjustTimer(eventId.value, delta)
    gs.applySnapshot(data.snapshot)
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error')
  }
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

watch(
  () => realtimeEvent?.value,
  (nextRealtimeEvent) => {
    if (nextRealtimeEvent?.operation === 'EVENT_STRUCTURE_UPDATED') {
      void loadStructure({ silent: true })
    }
  },
)

watch(socketConnected, (isNowConnected) => {
  if (isNowConnected) {
    void loadStructure({ silent: true })
  }
})

onMounted(() => {
  load()
  connect()
  startLocalTimer()
})

onUnmounted(() => {
  stopLocalTimer()
})
</script>

<template>
  <div>
    <div class="flex" style="align-items: center; justify-content: space-between">
      <h1 class="page-title" style="margin-bottom: 0">Panel de Partida</h1>
      <div class="flex gap-1" style="align-items: center">
        <span
          class="badge"
          :class="{
            'badge-in-progress': isInProgress,
            'badge-finished': isFinished,
            'badge-ready': gs.eventStatus === 'READY',
            'badge-draft': gs.eventStatus === 'DRAFT',
          }"
        >
          {{ gs.eventStatus }}
        </span>
        <span
          :style="{ color: socketConnected ? 'var(--c-success)' : 'var(--c-danger)', fontSize: '0.82rem' }"
        >
          ● {{ socketConnected ? 'Conectado' : 'Desconectado' }}
        </span>
      </div>
    </div>
    <p class="text-muted" style="font-size: 0.9rem; margin-top: 6px">
      Villano activo: <strong>{{ gs.villainName || 'Villano desconocido' }}</strong>
    </p>

    <!-- Start game button -->
    <div v-if="gs.eventStatus === 'READY' || gs.eventStatus === 'DRAFT'" class="card text-center mt-2">
      <p class="text-muted mb-2">
        <template v-if="gs.eventStatus === 'DRAFT'">
          El evento está en configuración. Puedes iniciar la partida cuando todos estén preparados.
        </template>
        <template v-else>
          El evento está listo. Inicia la partida cuando todos estén preparados.
        </template>
      </p>
      <div v-if="!canStartGame" class="warn-box" style="text-align: left; margin-bottom: 12px">
        No puedes iniciar la partida todavía: hay mesas pendientes de asignación.
        <div v-if="pendingGroupsSummary" style="margin-top: 6px; font-size: 0.85rem">
          {{ pendingGroupsSummary }}
        </div>
      </div>
      <button class="btn btn-primary" :disabled="structureLoading || !canStartGame" @click="handleStart">
        Iniciar partida
      </button>
    </div>

    <!-- Final summary -->
    <FinalSummary v-if="isFinished && gs.finalSummary" :summary="gs.finalSummary" />

    <!-- Game state panels -->
    <template v-if="isInProgress || isFinished">
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px" class="mt-2">
        <!-- Villain HP -->
        <div class="card">
          <h3 class="mb-1">HP del Villano</h3>
          <VillainHpBar :current="gs.villainHpCurrent" :max="gs.villainHpMax" />
          <div class="text-center mt-1 text-muted" style="font-size: 0.85rem">
            Daño total reportado: {{ gs.totalReportedVillainDamage }}
          </div>
        </div>

        <!-- Threat -->
        <div class="card">
          <h3 class="mb-1">Amenaza del Esquema</h3>
          <ThreatBar :current="gs.schemeThreatCurrent" :target="gs.schemeThreatTarget" />
        </div>
      </div>

      <!-- Timer -->
      <GameTimer
        v-if="gs.timerStatus !== 'NOT_CONFIGURED'"
        :remaining="gs.timerRemainingSeconds"
        :status="gs.timerStatus"
        :disabled="isFinished"
        @start="handleTimerStart"
        @pause="handleTimerPause"
        @resume="handleTimerResume"
        @adjust="handleTimerAdjust"
      />

      <!-- Action panels (only in progress) -->
      <template v-if="isInProgress">
        <!-- Pending group assignment (during IN_PROGRESS) -->
        <div v-if="pendingGroups.length" class="card mt-2" style="border-left: 4px solid var(--c-warning)">
          <h3 class="mb-1">⚠ Mesas pendientes de asignación</h3>
          <p class="text-muted" style="font-size: 0.85rem; margin-bottom: 12px">
            Hay mesas que se han unido y necesitan ser asignadas a un pod para que la partida continúe.
          </p>
          <div v-for="group in pendingGroups" :key="group.id" class="flex gap-1 mb-1" style="align-items: center">
            <strong style="min-width: 120px">{{ group.identifier }}</strong>
            <span class="text-muted" style="font-size: 0.85rem; min-width: 80px">
              {{ group.playerCount }} {{ group.playerCount === 1 ? 'jugador' : 'jugadores' }}
            </span>
            <select
              v-model.number="assignPodByGroup[group.id]"
              class="form-control"
              style="width: 180px"
            >
              <option :value="undefined" disabled>Seleccionar pod</option>
              <option v-for="pod in pods" :key="pod.id" :value="pod.id">{{ pod.name }}</option>
            </select>
            <button
              class="btn btn-primary btn-sm"
              :disabled="!assignPodByGroup[group.id] || assigningGroupId === group.id"
              @click="handleAssignGroup(group.id)"
            >
              {{ assigningGroupId === group.id ? 'Asignando…' : 'Asignar' }}
            </button>
          </div>
        </div>

        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px" class="mt-2">
          <!-- Report damage -->
          <div class="card">
            <h3 class="mb-1">Reportar Daño al Villano</h3>
            <form @submit.prevent="handleReportDamage">
              <div class="form-group">
                <label for="dmg-code">Código representante</label>
                <input id="dmg-code" v-model="dmgCode" class="form-control" required maxlength="16" />
              </div>
              <div class="form-group">
                <label for="dmg-amount">Cantidad de daño</label>
                <input id="dmg-amount" v-model.number="dmgAmount" type="number" class="form-control" min="1" required />
              </div>
              <button class="btn btn-primary btn-sm" :disabled="dmgLoading">
                {{ dmgLoading ? 'Enviando…' : 'Reportar daño' }}
              </button>
            </form>
          </div>

          <!-- Report threat -->
          <div class="card">
            <h3 class="mb-1">Reportar Amenaza</h3>
            <form @submit.prevent="handleReportThreat">
              <div class="form-group">
                <label for="threat-code">Código representante</label>
                <input id="threat-code" v-model="threatCode" class="form-control" required maxlength="16" />
              </div>
              <div class="form-group">
                <label for="threat-amount">Cantidad de amenaza</label>
                <input
                  id="threat-amount"
                  v-model.number="threatAmount"
                  type="number"
                  class="form-control"
                  min="1"
                  required
                />
              </div>
              <button class="btn btn-primary btn-sm" :disabled="threatLoading">
                {{ threatLoading ? 'Enviando…' : 'Reportar amenaza' }}
              </button>
            </form>
          </div>
        </div>

        <!-- Manual corrections -->
        <div class="card mt-2">
          <h3 class="mb-1">Correcciones manuales</h3>
          <p class="text-muted" style="font-size: 0.82rem; margin-bottom: 12px">
            Solo organizador / co-organizador. Se registra en el log.
          </p>
          <div class="flex gap-2" style="align-items: flex-end">
            <div class="form-group" style="margin-bottom: 0">
              <label for="manual-hp">HP del villano</label>
              <div class="flex gap-1">
                <input
                  id="manual-hp"
                  v-model.number="manualHp"
                  type="number"
                  class="form-control"
                  min="0"
                  style="width: 100px"
                />
                <button class="btn btn-secondary btn-sm" @click="handleManualHp">
                  Corregir HP
                </button>
              </div>
            </div>
            <div class="form-group" style="margin-bottom: 0">
              <label for="manual-threat">Amenaza</label>
              <div class="flex gap-1">
                <input
                  id="manual-threat"
                  v-model.number="manualThreat"
                  type="number"
                  class="form-control"
                  min="0"
                  style="width: 100px"
                />
                <button class="btn btn-secondary btn-sm" @click="handleManualThreat">
                  Corregir amenaza
                </button>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- Inter-pod cards -->
      <InterPodAlerts :event-id="eventId" :disabled="isFinished" class="mt-2" />
    </template>
  </div>
</template>
