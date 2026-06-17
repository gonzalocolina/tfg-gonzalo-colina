<script setup>
import { ref, computed, onMounted, onUnmounted, unref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useGameStateStore } from '@/stores/gameState'
import { useNotificationsStore } from '@/stores/notifications'
import { useWebSocket } from '@/composables/useWebSocket'
import { getEventStructure } from '@/api/setup'
import {
  startGame,
  delegateVillainDamage,
  delegateRemoveVillainDamage,
  delegateThreat,
  delegateRemoveThreat,
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
import { formatEventStatus } from '@/utils/displayLabels'

const route = useRoute()
const gs = useGameStateStore()
const notif = useNotificationsStore()
const eventId = computed(() => route.params.eventId)

const { connected, realtimeEvent, connect } = useWebSocket(eventId.value)
const socketConnected = computed(() => Boolean(unref(connected)))

// Report damage form
const dmgGroupId = ref('')
const dmgAmount = ref(1)
const dmgJustification = ref('')
const dmgActionLoading = ref('')

// Report threat form
const threatGroupId = ref('')
const threatAmount = ref(1)
const threatJustification = ref('')
const threatActionLoading = ref('')

// Manual corrections
const manualHp = ref(0)
const manualThreat = ref(0)
const manualHpJustification = ref('')
const manualThreatJustification = ref('')
const manualHpLoading = ref(false)
const manualThreatLoading = ref(false)
const manualHpFocused = ref(false)
const manualThreatFocused = ref(false)
const lastSyncedManualHp = ref(null)
const lastSyncedManualThreat = ref(null)
const eventStructure = ref(null)
const structureLoading = ref(false)

const isFinished = computed(() => gs.eventStatus === 'FINISHED')
const isInProgress = computed(() => gs.eventStatus === 'IN_PROGRESS')
const canCorrectFinishedMetricOutcome = computed(
  () => isFinished.value && ['VILLAIN_HP_ZERO', 'THREAT_TARGET_REACHED'].includes(gs.finalSummary?.cause ?? ''),
)
const canManageDelegatedReports = computed(() => isInProgress.value || canCorrectFinishedMetricOutcome.value)
const pendingGroups = computed(() => eventStructure.value?.unassignedGroups || [])
const pods = computed(() => eventStructure.value?.pods || [])
const totalGroupCount = computed(() => Number(eventStructure.value?.currentGroupCount ?? 0))
const hasConfiguredGroups = computed(() => totalGroupCount.value > 0)
const emptyPods = computed(() => pods.value.filter((pod) => (pod.groups?.length ?? 0) === 0))
const hasEmptyPods = computed(() => emptyPods.value.length > 0)
const canStartGame = computed(
  () => eventStructure.value !== null
    && hasConfiguredGroups.value
    && pendingGroups.value.length === 0
    && !hasEmptyPods.value,
)

const pendingGroupsSummary = computed(() => pendingGroups.value.map((group) => group.identifier).join(', '))
const emptyPodsSummary = computed(() => emptyPods.value.map((pod) => pod.name).join(', '))
const startBlockedMessage = computed(() => {
  if (eventStructure.value === null) {
    return 'No se ha podido validar la estructura del evento todavía.'
  }

  if (!hasConfiguredGroups.value) {
    return 'No puedes iniciar la partida todavía: crea al menos una mesa.'
  }

  if (pendingGroups.value.length > 0) {
    return 'No puedes iniciar la partida todavía: hay mesas pendientes de asignación.'
  }

  if (hasEmptyPods.value) {
    return 'No puedes iniciar la partida todavía: hay nodos sin mesas asignadas.'
  }

  return ''
})

const availableGroups = computed(() => {
  const assignedGroups = pods.value.flatMap((pod) =>
    (pod.groups || []).map((group) => ({
      ...group,
      podName: pod.name,
    })),
  )

  const unassignedReportGroups = pendingGroups.value.map((group) => ({
    ...group,
    podName: null,
  }))

  return [...assignedGroups, ...unassignedReportGroups]
    .filter((group) => group?.id != null)
    .sort((left, right) =>
      left.identifier.localeCompare(right.identifier, 'es', {
        numeric: true,
        sensitivity: 'base',
      }),
    )
})
const hasAvailableGroups = computed(() => availableGroups.value.length > 0)

function findReportGroup(groupId) {
  return availableGroups.value.find((group) => String(group.id) === String(groupId)) || null
}

function formatReportGroupLabel(group) {
  return group.podName ? `${group.identifier} · ${group.podName}` : `${group.identifier} · Sin pod asignado`
}

function syncEditableManualValue(modelRef, sourceValue, focusedRef, lastSyncedRef, { force = false } = {}) {
  const normalizedSourceValue = Number(sourceValue ?? 0)
  const currentValue = modelRef.value
  const isEmpty = currentValue === '' || currentValue == null
  const wasPreviouslySynced = Number(currentValue) === lastSyncedRef.value

  if (force || !focusedRef.value || isEmpty || wasPreviouslySynced) {
    modelRef.value = normalizedSourceValue
  }

  lastSyncedRef.value = normalizedSourceValue
}

function syncManualValuesFromState(options = {}) {
  syncEditableManualValue(manualHp, gs.villainHpCurrent, manualHpFocused, lastSyncedManualHp, options)
  syncEditableManualValue(manualThreat, gs.schemeThreatCurrent, manualThreatFocused, lastSyncedManualThreat, options)
}

function setManualFieldFocus(metric, isFocused) {
  if (metric === 'hp') {
    manualHpFocused.value = isFocused
    return
  }

  manualThreatFocused.value = isFocused
}

function requirePositiveDelegatedAmount(rawValue, metricLabel) {
  const parsedValue = Number(rawValue)
  if (!Number.isFinite(parsedValue) || parsedValue < 1) {
    notif.error(`La cantidad de ${metricLabel} debe ser mayor que 0.`)
    return null
  }
  return Math.trunc(parsedValue)
}

async function load() {
  try {
    await gs.fetchGameState(eventId.value)
    await loadStructure({ silent: true })
    syncManualValuesFromState({ force: true })
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
    notif.error(startBlockedMessage.value || 'La estructura del evento todavía no permite iniciar la partida.')
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

async function handleDelegateDamage() {
  const group = findReportGroup(dmgGroupId.value)
  if (!group) {
    notif.error('Selecciona una mesa válida antes de reportar daño.')
    return
  }

  const damage = requirePositiveDelegatedAmount(dmgAmount.value, 'daño')
  if (damage === null) {
    return
  }

  const justification = dmgJustification.value.trim()

  dmgActionLoading.value = 'add'
  try {
    const { data } = await delegateVillainDamage(eventId.value, {
      groupId: group.id,
      damage,
      justification,
    })
    gs.applySnapshot(data.snapshot)
    syncManualValuesFromState({ force: true })
    notif.success(`Daño delegado registrado: ${damage}`)
    dmgAmount.value = 1
    dmgJustification.value = ''
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al reportar daño')
  } finally {
    dmgActionLoading.value = ''
  }
}

async function handleDelegateDamageRemoval() {
  const group = findReportGroup(dmgGroupId.value)
  if (!group) {
    notif.error('Selecciona una mesa válida antes de revertir daño.')
    return
  }

  const damage = requirePositiveDelegatedAmount(dmgAmount.value, 'daño')
  if (damage === null) {
    return
  }

  const justification = dmgJustification.value.trim()

  dmgActionLoading.value = 'remove'
  try {
    const { data } = await delegateRemoveVillainDamage(eventId.value, {
      groupId: group.id,
      damage,
      justification,
    })
    gs.applySnapshot(data.snapshot)
    syncManualValuesFromState({ force: true })
    notif.success(`Daño delegado revertido: ${damage}`)
    dmgAmount.value = 1
    dmgJustification.value = ''
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al revertir daño')
  } finally {
    dmgActionLoading.value = ''
  }
}

async function handleDelegateThreat() {
  const group = findReportGroup(threatGroupId.value)
  if (!group) {
    notif.error('Selecciona una mesa válida antes de reportar amenaza.')
    return
  }

  const threat = requirePositiveDelegatedAmount(threatAmount.value, 'amenaza')
  if (threat === null) {
    return
  }

  const justification = threatJustification.value.trim()

  threatActionLoading.value = 'add'
  try {
    const { data } = await delegateThreat(eventId.value, {
      groupId: group.id,
      threat,
      justification,
    })
    gs.applySnapshot(data.snapshot)
    syncManualValuesFromState({ force: true })
    notif.success(`Amenaza delegada registrada: ${threat}`)
    threatAmount.value = 1
    threatJustification.value = ''
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al reportar amenaza')
  } finally {
    threatActionLoading.value = ''
  }
}

async function handleDelegateThreatRemoval() {
  const group = findReportGroup(threatGroupId.value)
  if (!group) {
    notif.error('Selecciona una mesa válida antes de retirar amenaza.')
    return
  }

  const threat = requirePositiveDelegatedAmount(threatAmount.value, 'amenaza')
  if (threat === null) {
    return
  }

  const justification = threatJustification.value.trim()

  threatActionLoading.value = 'remove'
  try {
    const { data } = await delegateRemoveThreat(eventId.value, {
      groupId: group.id,
      threat,
      justification,
    })
    gs.applySnapshot(data.snapshot)
    syncManualValuesFromState({ force: true })
    notif.success(`Amenaza retirada: ${threat}`)
    threatAmount.value = 1
    threatJustification.value = ''
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al retirar amenaza')
  } finally {
    threatActionLoading.value = ''
  }
}

async function handleManualHp() {
  const justification = manualHpJustification.value.trim()

  manualHpLoading.value = true
  try {
    const { data } = await manualUpdateVillainHp(eventId.value, {
      value: manualHp.value,
      justification,
    })
    gs.applySnapshot(data.snapshot)
    syncManualValuesFromState({ force: true })
    manualHpJustification.value = ''
    notif.success('HP del villano corregido')
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error')
  } finally {
    manualHpLoading.value = false
  }
}

async function handleManualThreat() {
  const justification = manualThreatJustification.value.trim()

  manualThreatLoading.value = true
  try {
    const { data } = await manualUpdateThreat(eventId.value, {
      value: manualThreat.value,
      justification,
    })
    gs.applySnapshot(data.snapshot)
    syncManualValuesFromState({ force: true })
    manualThreatJustification.value = ''
    notif.success('Amenaza corregida')
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error')
  } finally {
    manualThreatLoading.value = false
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

watch(
  () => [gs.villainHpCurrent, gs.schemeThreatCurrent],
  () => {
    syncManualValuesFromState()
  },
  { immediate: true },
)

watch(
  availableGroups,
  (groups) => {
    const validGroupIds = new Set(groups.map((group) => String(group.id)))

    if (dmgGroupId.value && !validGroupIds.has(dmgGroupId.value)) {
      dmgGroupId.value = ''
    }

    if (threatGroupId.value && !validGroupIds.has(threatGroupId.value)) {
      threatGroupId.value = ''
    }
  },
  { immediate: true },
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
            'badge-draft': gs.eventStatus === 'DRAFT',
          }"
        >
          {{ formatEventStatus(gs.eventStatus) }}
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
    <div v-if="gs.eventStatus === 'DRAFT'" class="card text-center mt-2">
      <p class="text-muted mb-2">
        El evento está en configuración. Puedes iniciar la partida cuando todos estén preparados.
      </p>
      <div v-if="eventStructure && !canStartGame" class="warn-box" style="text-align: left; margin-bottom: 12px">
        {{ startBlockedMessage }}
        <div v-if="pendingGroupsSummary && pendingGroups.length" style="margin-top: 6px; font-size: 0.85rem">
          {{ pendingGroupsSummary }}
        </div>
        <div v-else-if="emptyPodsSummary && hasEmptyPods" style="margin-top: 6px; font-size: 0.85rem">
          {{ emptyPodsSummary }}
        </div>
      </div>
      <button data-testid="start-game-button" class="btn btn-primary" :disabled="structureLoading || !canStartGame" @click="handleStart">
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
          <h3 class="mb-1">Puntos de vida del Villano</h3>
          <VillainHpBar :current="gs.villainHpCurrent" :max="gs.villainHpMax" />
          <div class="text-center mt-1 text-muted" style="font-size: 0.85rem">
            Daño total reportado: {{ gs.totalReportedVillainDamage }}
          </div>
        </div>

        <!-- Threat -->
        <div class="card">
          <h3 class="mb-1">Amenaza del Plan Principal</h3>
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

      <template v-if="canManageDelegatedReports">
        <div class="card mt-2">
          <h3 class="mb-1">Registro delegado en nombre de una mesa</h3>
          <p class="text-muted" style="font-size: 0.82rem; margin-bottom: 12px">
            <template v-if="isInProgress">
              Usa esta opción cuando quieras corregir algún valor de daño o amenaza causado por alguna mesa en concreto. El log
              guardará la mesa de origen y el actor que realizó el registro. Puedes añadir una justificación opcional si
              aporta contexto.
            </template>
            <template v-else>
              La partida terminó por vida o amenaza. Usa esta sección para corregir el registro de una mesa; si el ajuste
              elimina la condición de finalización, la partida se reabrirá en pausa.
            </template>
          </p>
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px">
            <form data-testid="delegated-damage-form" @submit.prevent="handleDelegateDamage">
              <h4 class="mb-1">Gestionar daño al villano en nombre de una mesa</h4>
              <div class="form-group">
                <label for="delegated-dmg-group">Mesa</label>
                <select
                  id="delegated-dmg-group"
                  v-model="dmgGroupId"
                  class="form-control"
                  :disabled="!hasAvailableGroups"
                  required
                >
                  <option value="" disabled>
                    {{ hasAvailableGroups ? 'Selecciona una mesa' : 'No hay mesas disponibles' }}
                  </option>
                  <option v-for="group in availableGroups" :key="group.id" :value="String(group.id)">
                    {{ formatReportGroupLabel(group) }}
                  </option>
                </select>
              </div>
              <div class="form-group">
                <label for="delegated-dmg-amount">Cantidad de daño</label>
                <input
                  id="delegated-dmg-amount"
                  v-model.number="dmgAmount"
                  type="number"
                  class="form-control"
                  min="1"
                  required
                />
              </div>
              <div class="form-group">
                <label for="delegated-dmg-justification">Motivo / justificación (opcional)</label>
                <textarea
                  id="delegated-dmg-justification"
                  v-model="dmgJustification"
                  class="form-control"
                  rows="3"
                  maxlength="300"
                />
              </div>
              <div class="text-muted" style="font-size: 0.78rem; margin-top: -8px; margin-bottom: 12px">
                Usa <strong>Revertir daño delegado</strong> si una mesa comunicó daño de más y quieres devolver HP al villano.
              </div>
              <div class="flex gap-1" style="flex-wrap: wrap">
                <button class="btn btn-primary btn-sm" :disabled="Boolean(dmgActionLoading) || !dmgGroupId">
                  {{ dmgActionLoading === 'add' ? 'Enviando…' : 'Registrar daño delegado' }}
                </button>
                <button
                  type="button"
                  class="btn btn-secondary btn-sm"
                  :disabled="Boolean(dmgActionLoading) || !dmgGroupId"
                  @click="handleDelegateDamageRemoval"
                >
                  {{ dmgActionLoading === 'remove' ? 'Enviando…' : 'Revertir daño delegado' }}
                </button>
              </div>
            </form>

            <form data-testid="delegated-threat-form" @submit.prevent="handleDelegateThreat">
              <h4 class="mb-1">Gestionar amenaza del plan principal</h4>
              <div class="form-group">
                <label for="delegated-threat-group">Mesa</label>
                <select
                  id="delegated-threat-group"
                  v-model="threatGroupId"
                  class="form-control"
                  :disabled="!hasAvailableGroups"
                  required
                >
                  <option value="" disabled>
                    {{ hasAvailableGroups ? 'Selecciona una mesa' : 'No hay mesas disponibles' }}
                  </option>
                  <option v-for="group in availableGroups" :key="group.id" :value="String(group.id)">
                    {{ formatReportGroupLabel(group) }}
                  </option>
                </select>
              </div>
              <div class="form-group">
                <label for="delegated-threat-amount">Cantidad de amenaza</label>
                <input
                  id="delegated-threat-amount"
                  v-model.number="threatAmount"
                  type="number"
                  class="form-control"
                  min="1"
                  required
                />
              </div>
              <div class="form-group">
                <label for="delegated-threat-justification">Motivo / justificación (opcional)</label>
                <textarea
                  id="delegated-threat-justification"
                  v-model="threatJustification"
                  class="form-control"
                  rows="3"
                  maxlength="300"
                />
              </div>
              <div class="text-muted" style="font-size: 0.78rem; margin-top: -8px; margin-bottom: 12px">
                Usa <strong>Retirar amenaza delegada</strong> si una mesa comunicó amenaza de más; usa el registro delegado si faltaba reflejarla.
              </div>
              <div class="flex gap-1" style="flex-wrap: wrap">
                <button class="btn btn-primary btn-sm" :disabled="Boolean(threatActionLoading) || !threatGroupId">
                  {{ threatActionLoading === 'add' ? 'Enviando…' : 'Registrar amenaza delegada' }}
                </button>
                <button
                  type="button"
                  class="btn btn-secondary btn-sm"
                  :disabled="Boolean(threatActionLoading) || !threatGroupId"
                  @click="handleDelegateThreatRemoval"
                >
                  {{ threatActionLoading === 'remove' ? 'Enviando…' : 'Retirar amenaza delegada' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </template>

      <template v-if="isInProgress">
        <!-- Manual corrections -->
        <div class="card mt-2">
          <h3 class="mb-1">Correcciones manuales globales</h3>
          <p class="text-muted" style="font-size: 0.82rem; margin-bottom: 12px">
            Usa esta opción para establecer un nuevo valor global. No se atribuye a
            una mesa concreta. Puedes añadir una justificación opcional en el log si quieres dejar más contexto.
          </p>
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px">
            <form data-testid="manual-hp-form" @submit.prevent="handleManualHp">
              <div class="form-group">
                <label for="manual-hp">HP del villano</label>
                <input
                  id="manual-hp"
                  v-model.number="manualHp"
                  type="number"
                  class="form-control"
                  min="0"
                  :placeholder="String(gs.villainHpCurrent ?? 0)"
                  @focus="setManualFieldFocus('hp', true)"
                  @blur="setManualFieldFocus('hp', false)"
                />
              </div>
              <div class="form-group">
                <label for="manual-hp-justification">Justificación (opcional)</label>
                <textarea
                  id="manual-hp-justification"
                  v-model="manualHpJustification"
                  class="form-control"
                  rows="3"
                  maxlength="300"
                />
              </div>
              <button class="btn btn-secondary btn-sm" :disabled="manualHpLoading">
                {{ manualHpLoading ? 'Guardando…' : 'Corregir HP' }}
              </button>
            </form>

            <form data-testid="manual-threat-form" @submit.prevent="handleManualThreat">
              <div class="form-group">
                <label for="manual-threat">Amenaza del plan principal</label>
                <input
                  id="manual-threat"
                  v-model.number="manualThreat"
                  type="number"
                  class="form-control"
                  min="0"
                  :placeholder="String(gs.schemeThreatCurrent ?? 0)"
                  @focus="setManualFieldFocus('threat', true)"
                  @blur="setManualFieldFocus('threat', false)"
                />
              </div>
              <div class="form-group">
                <label for="manual-threat-justification">Justificación (opcional)</label>
                <textarea
                  id="manual-threat-justification"
                  v-model="manualThreatJustification"
                  class="form-control"
                  rows="3"
                  maxlength="300"
                />
              </div>
              <button class="btn btn-secondary btn-sm" :disabled="manualThreatLoading">
                {{ manualThreatLoading ? 'Guardando…' : 'Corregir amenaza' }}
              </button>
            </form>
          </div>
        </div>
      </template>

      <!-- Inter-pod cards -->
      <InterPodAlerts :event-id="eventId" :disabled="isFinished" :realtime-event="realtimeEvent" class="mt-2" />
    </template>
  </div>
</template>
