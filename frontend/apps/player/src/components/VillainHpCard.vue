<script setup>
const props = defineProps({
  name: { type: String, default: '' },
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
  <div class="card">
    <div class="villain-hp-card__header">
      <div class="card-title">HP del Villano</div>
      <div v-if="name" class="text-muted villain-hp-card__name">
        {{ name }}
      </div>
    </div>
    <div class="big-value" :style="{ color: barColor(props.current, props.max) }">
      {{ current }} <small>/ {{ max }}</small>
    </div>
    <div class="progress-bar mt-1" style="background: var(--c-hp-bg)">
      <div
        class="progress-fill"
        :style="{ width: pct(current, max) + '%', background: barColor(current, max) }"
      />
    </div>
    <div class="flex mt-1" style="justify-content: space-between; font-size: 0.78rem">
      <span class="text-muted">{{ pct(current, max).toFixed(1) }}%</span>
      <span v-if="current <= max * 0.25" class="text-success">¡Casi derrotado!</span>
      <span v-else-if="current <= max * 0.5" class="text-warning">50% HP</span>
    </div>
  </div>
</template>

<style scoped>
.villain-hp-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.villain-hp-card__header .card-title {
  margin-bottom: 0;
}

.villain-hp-card__name {
  flex: 1;
  min-width: 0;
  font-size: 0.82rem;
  line-height: 1.2;
  text-align: right;
}
</style>
