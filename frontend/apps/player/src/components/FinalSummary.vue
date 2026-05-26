<script setup>
import { computed } from 'vue'

const props = defineProps({
  summary: { type: Object, required: true },
})

const resultLabel = computed(() => {
  const map = {
    PLAYERS_VICTORY: '¡Victoria de los jugadores!',
    THREAT_DEFEAT: 'Derrota - Amenaza máxima alcanzada',
    TIMEOUT_DEFEAT: 'Derrota - Tiempo agotado',
    CANCELLED: 'Evento cancelado',
  }
  return map[props.summary.result] || props.summary.result
})

const causeLabel = computed(() => {
  const map = {
    VILLAIN_HP_ZERO: 'HP del villano llegó a 0',
    THREAT_REACHED_TARGET: 'La amenaza alcanzó el objetivo',
    TIMER_EXPIRED: 'El temporizador llegó a 0',
    CANCELLED_BY_ORGANIZER: 'Cancelado por el organizador',
  }
  return map[props.summary.cause] || props.summary.cause
})

function formatDuration(seconds) {
  if (!seconds) return '-'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  const parts = []
  if (h) parts.push(`${h}h`)
  if (m) parts.push(`${m}m`)
  parts.push(`${s}s`)
  return parts.join(' ')
}
</script>

<template>
  <div class="card" style="border-color: var(--c-warning)">
    <h2 class="text-center mb-2" style="font-size: 1.2rem">Resumen Final</h2>
    <div
      class="text-center mb-2"
      :style="{
        fontSize: '1.2rem',
        fontWeight: 700,
        color: summary.result === 'PLAYERS_VICTORY' ? 'var(--c-success)' : 'var(--c-danger)',
      }"
    >
      {{ resultLabel }}
    </div>
    <div class="summary-grid">
      <div class="summary-item">
        <div class="label">Causa</div>
        <div class="value" style="font-size: 0.85rem">{{ causeLabel }}</div>
      </div>
      <div class="summary-item">
        <div class="label">Duración</div>
        <div class="value">{{ formatDuration(summary.durationSeconds) }}</div>
      </div>
      <div class="summary-item">
        <div class="label">Daño total</div>
        <div class="value">{{ summary.totalReportedVillainDamage }}</div>
      </div>
      <div class="summary-item">
        <div class="label">Amenaza final</div>
        <div class="value">{{ summary.finalThreat }}</div>
      </div>
    </div>
  </div>
</template>
