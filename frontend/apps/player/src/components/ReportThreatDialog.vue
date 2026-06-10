<script setup>
import { computed, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useGameStateStore } from '@/stores/gameState'
import { useNotificationsStore } from '@/stores/notifications'
import { reportThreat, removeThreat } from '@/api/gameState'

const props = defineProps({
  mode: { type: String, default: 'add' },
})

const emit = defineEmits(['close'])

const auth = useAuthStore()
const gs = useGameStateStore()
const notif = useNotificationsStore()

const amount = ref(1)
const loading = ref(false)
const isRemoving = computed(() => props.mode === 'remove')
const dialogTitle = computed(() => (isRemoving.value ? 'Quitar Amenaza del Plan Principal' : 'Añadir Amenaza al Plan Principal'))
const actionVerb = computed(() => (isRemoving.value ? 'retirar amenaza' : 'reportar amenaza'))
const confirmLabel = computed(() => (isRemoving.value ? 'Confirmar retirada' : 'Confirmar amenaza'))
const actionBlockedReason = computed(() => {
  if (gs.timerStatus === 'PAUSED') {
    return `La partida está pausada. No puedes ${actionVerb.value} hasta que se reanude.`
  }

  if (gs.eventStatus === 'IN_PROGRESS' && gs.timerStatus === 'READY') {
    return `La partida ya ha comenzado, pero el organizador todavía no ha iniciado el contador. No puedes ${actionVerb.value} todavía.`
  }

  if (isRemoving.value && gs.schemeThreatCurrent <= 0) {
    return 'No hay amenaza en el plan principal para retirar.'
  }

  return ''
})
const actionsBlocked = computed(() => Boolean(actionBlockedReason.value))

function increment() {
  if (!isRemoving.value || amount.value < gs.schemeThreatCurrent) {
    amount.value++
  }
}

function decrement() {
  if (amount.value > 1) amount.value--
}

async function handleSubmit() {
  if (actionsBlocked.value) {
    notif.error(actionBlockedReason.value)
    return
  }

  loading.value = true
  try {
    const action = isRemoving.value ? removeThreat : reportThreat
    const { data } = await action(auth.eventId, {
      threat: amount.value,
    })
    gs.applySnapshot(data.snapshot)
    notif.success(isRemoving.value ? `Amenaza retirada: ${amount.value}` : `Amenaza registrada: ${amount.value}`)
    emit('close')
  } catch (err) {
    notif.error(err.response?.data?.message || (isRemoving.value ? 'Error al retirar amenaza' : 'Error al reportar amenaza'))
  } finally {
    loading.value = false
  }
}

function handleOverlayClick(e) {
  if (e.target === e.currentTarget) emit('close')
}
</script>

<template>
  <div class="modal-overlay" @click="handleOverlayClick">
    <div class="modal-panel">
      <div class="modal-title">{{ dialogTitle }}</div>

      <div v-if="actionsBlocked" class="warn-box" style="margin-bottom: 12px">
        {{ actionBlockedReason }}
      </div>

      <div class="stepper mb-2">
        <button class="stepper-btn" :disabled="amount <= 1" @click="decrement">−</button>
        <div class="stepper-value">{{ amount }}</div>
        <button class="stepper-btn" @click="increment">+</button>
      </div>

      <button
        class="btn btn-block btn-lg"
        :class="isRemoving ? 'btn-primary' : 'btn-danger'"
        :disabled="loading || actionsBlocked"
        @click="handleSubmit"
      >
        {{ loading ? 'Enviando…' : confirmLabel }}
      </button>
      <button class="btn btn-secondary btn-block mt-1" @click="emit('close')">Cancelar</button>
    </div>
  </div>
</template>
