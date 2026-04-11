<script setup>
import { computed, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useGameStateStore } from '@/stores/gameState'
import { useNotificationsStore } from '@/stores/notifications'
import { reportVillainDamage } from '@/api/gameState'

const emit = defineEmits(['close'])

const auth = useAuthStore()
const gs = useGameStateStore()
const notif = useNotificationsStore()

const amount = ref(1)
const loading = ref(false)
const actionBlockedReason = computed(() => {
  if (gs.timerStatus === 'PAUSED') {
    return 'La partida está pausada. No puedes reportar daño hasta que se reanude.'
  }

  if (gs.eventStatus === 'IN_PROGRESS' && gs.timerStatus === 'READY') {
    return 'La partida ya ha comenzado, pero el organizador todavía no ha iniciado el contador. No puedes reportar daño todavía.'
  }

  return ''
})
const actionsBlocked = computed(() => Boolean(actionBlockedReason.value))

function increment() {
  if (amount.value < gs.villainHpCurrent) amount.value++
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
    const { data } = await reportVillainDamage(auth.eventId, {
      representativeCode: auth.code,
      damage: amount.value,
    })
    gs.applySnapshot(data.snapshot)
    notif.success(`Daño registrado: ${amount.value}`)
    emit('close')
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al reportar daño')
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
      <div class="modal-title">Reportar Daño al Villano</div>

      <div v-if="actionsBlocked" class="warn-box" style="margin-bottom: 12px">
        {{ actionBlockedReason }}
      </div>

      <div class="stepper mb-2">
        <button class="stepper-btn" :disabled="amount <= 1" @click="decrement">−</button>
        <div class="stepper-value">{{ amount }}</div>
        <button class="stepper-btn" :disabled="amount >= gs.villainHpCurrent" @click="increment">+</button>
      </div>

      <button class="btn btn-primary btn-block btn-lg" :disabled="loading || actionsBlocked" @click="handleSubmit">
        {{ loading ? 'Enviando…' : 'Confirmar daño' }}
      </button>
      <button class="btn btn-secondary btn-block mt-1" @click="emit('close')">Cancelar</button>
    </div>
  </div>
</template>
