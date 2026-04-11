<script setup>
defineProps({
  current: { type: Number, required: true },
  max: { type: Number, required: true },
})

function pct(current, max) {
  if (max <= 0) return 0
  return Math.max(0, Math.min(100, (current / max) * 100))
}

function barColor(current, max) {
  const p = pct(current, max)
  if (p > 50) return '#e74c3c'
  if (p > 25) return '#e67e22'
  return '#2ecc71'
}
</script>

<template>
  <div>
    <div class="progress-bar" style="background: var(--c-hp-bg)">
      <div
        class="progress-fill"
        :style="{ width: pct(current, max) + '%', background: barColor(current, max) }"
      />
      <div class="progress-label">{{ current }} / {{ max }}</div>
    </div>
    <div class="flex" style="justify-content: space-between; margin-top: 6px; font-size: 0.78rem">
      <span class="text-muted">{{ pct(current, max).toFixed(1) }}%</span>
      <span v-if="current <= max * 0.25" class="text-success">¡Casi derrotado!</span>
      <span v-else-if="current <= max * 0.5" class="text-warning">50% HP</span>
    </div>
  </div>
</template>
