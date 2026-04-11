<script setup>
defineProps({
  current: { type: Number, required: true },
  target: { type: Number, required: true },
})

function pct(current, target) {
  if (target <= 0) return 0
  return Math.max(0, Math.min(100, (current / target) * 100))
}

function barColor(current, target) {
  const p = pct(current, target)
  if (p >= 75) return '#e74c3c'
  if (p >= 50) return '#e67e22'
  return '#f1c40f'
}
</script>

<template>
  <div>
    <div class="progress-bar" style="background: var(--c-threat-bg)">
      <div
        class="progress-fill"
        :style="{ width: pct(current, target) + '%', background: barColor(current, target) }"
      />
      <div class="progress-label">{{ current }} / {{ target }}</div>
    </div>
    <div class="flex" style="justify-content: space-between; margin-top: 6px; font-size: 0.78rem">
      <span class="text-muted">{{ pct(current, target).toFixed(1) }}%</span>
      <span v-if="current >= target * 0.75" class="text-danger">¡Peligro!</span>
      <span v-else-if="current >= target * 0.5" class="text-warning">50% amenaza</span>
    </div>
  </div>
</template>
