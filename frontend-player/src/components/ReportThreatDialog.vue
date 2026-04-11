<script setup>
import { computed, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useGameStateStore } from '@/stores/gameState'
import { useNotificationsStore } from '@/stores/notifications'
import { reportThreat } from '@/api/gameState'

const emit = defineEmits(['close'])

const auth = useAuthStore()
const gs = useGameStateStore()
const notif = useNotificationsStore()

const amount = ref(1)
const loading = ref(false)
const actionBlockedReason = computed(() => {
  if (gs.timerStatus === 'PAUSED') {
    return 'La partida está pausada. No puedes reportar amenaza hasta que se reanude.'
  }

  if (gs.eventStatus === 'IN_PROGRESS' && gs.timerStatus === 'READY') {
    return 'La partida ya ha comenzado, pero el organizador todavía no ha iniciado el contador. No puedes reportar amenaza todavía.'
  }

  return ''
})
const actionsBlocked = computed(() => Boolean(actionBlockedReason.value))

function increment() {
  amount.value++
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
    const { data } = await reportThreat(auth.eventId, {
      representativeCode: auth.code,
      threat: amount.value,
    })
    gs.applySnapshot(data.snapshot)
    notif.success(`Amenaza registrada: ${amount.value}`)
    emit('close')
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al reportar amenaza')
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
      <div class="modal-title">Reportar Amenaza</div>

      <div v-if="actionsBlocked" class="warn-box" style="margin-bottom: 12px">
        {{ actionBlockedReason }}
      </div>

      <div class="stepper mb-2">
        <button class="stepper-btn" :disabled="amount <= 1" @click="decrement">−</button>
        <div class="stepper-value">{{ amount }}</div>
        <button class="stepper-btn" @click="increment">+</button>
      </div>

      <button class="btn btn-danger btn-block btn-lg" :disabled="loading || actionsBlocked" @click="handleSubmit">
        {{ loading ? 'Enviando…' : 'Confirmar amenaza' }}
      </button>
      <button class="btn btn-secondary btn-block mt-1" @click="emit('close')">Cancelar</button>
    </div>
  </div>
</template>
