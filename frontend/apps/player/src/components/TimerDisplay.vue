<script setup>
import { computed } from 'vue'

const props = defineProps({
  remaining: { type: Number, required: true },
  status: { type: String, required: true },
})

const formatted = computed(() => {
  const total = Math.max(0, props.remaining)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

const timerColor = computed(() => {
  if (props.remaining <= 300) return 'var(--c-danger)'
  if (props.remaining <= 900) return 'var(--c-warning)'
  return 'var(--c-text)'
})

const statusLabel = computed(() => {
  const map = {
    RUNNING: 'En marcha',
    PAUSED: 'Pausado',
    READY: 'Preparado',
    FINISHED: 'Finalizado',
  }
  return map[props.status] || props.status
})
</script>

<template>
  <div class="card text-center">
    <div class="card-title">Temporizador</div>
    <div class="timer-display" :style="{ color: timerColor }">
      {{ formatted }}
    </div>
    <div class="text-muted" style="font-size: 0.8rem; margin-top: 4px">
      {{ statusLabel }}
    </div>
  </div>
</template>
