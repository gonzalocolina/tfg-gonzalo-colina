<script setup>
import { computed } from 'vue'

const props = defineProps({
  remaining: { type: Number, required: true },
  status: { type: String, required: true },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['start', 'pause', 'resume', 'adjust'])

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
</script>

<template>
  <div class="card mt-2">
    <h3 class="mb-1 text-center">Temporizador</h3>
    <div class="timer-display" :style="{ color: timerColor }">
      {{ formatted }}
    </div>
    <div class="text-center text-muted" style="font-size: 0.82rem; margin-bottom: 8px">
      {{ status === 'RUNNING' ? 'En marcha' : status === 'PAUSED' ? 'Pausado' : status }}
    </div>
    <div v-if="!disabled" class="timer-controls">
      <button
        v-if="status === 'READY'"
        class="btn btn-primary btn-sm"
        @click="emit('start')"
      >
        Iniciar
      </button>
      <button
        v-if="status === 'RUNNING'"
        class="btn btn-secondary btn-sm"
        @click="emit('pause')"
      >
        Pausar
      </button>
      <button
        v-if="status === 'PAUSED'"
        class="btn btn-primary btn-sm"
        @click="emit('resume')"
      >
        Reanudar
      </button>
      <button
        v-if="status === 'RUNNING' || status === 'PAUSED'"
        class="btn btn-secondary btn-sm"
        @click="emit('adjust', -5)"
      >
        −5 min
      </button>
      <button
        v-if="status === 'RUNNING' || status === 'PAUSED'"
        class="btn btn-secondary btn-sm"
        @click="emit('adjust', 5)"
      >
        +5 min
      </button>
      <button
        v-if="status === 'RUNNING' || status === 'PAUSED'"
        class="btn btn-secondary btn-sm"
        @click="emit('adjust', 15)"
      >
        +15 min
      </button>
    </div>
  </div>
</template>
