<script setup>
import { computed } from 'vue'

const props = defineProps({
  series: {
    type: Array,
    default: () => [],
  },
})

const SVG_WIDTH = 960
const SVG_HEIGHT = 320
const MARGINS = Object.freeze({
  top: 28,
  right: 72,
  bottom: 52,
  left: 72,
})

const plotWidth = SVG_WIDTH - MARGINS.left - MARGINS.right
const plotHeight = SVG_HEIGHT - MARGINS.top - MARGINS.bottom

function parseTimestamp(timestamp) {
  const value = new Date(timestamp).getTime()
  return Number.isFinite(value) ? value : null
}

function parseMetric(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function formatAxisValue(value) {
  if (Number.isInteger(value)) {
    return String(value)
  }

  return value.toFixed(1).replace(/\.0$/, '')
}

function formatTickLabel(timestamp) {
  return new Date(timestamp).toLocaleString('es-ES', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const normalizedSeries = computed(() =>
  props.series
    .map((point, index) => ({
      id: point.id ?? `point-${index}`,
      timestamp: point.timestamp,
      time: parseTimestamp(point.timestamp),
      villainDamage: parseMetric(point.villainDamage),
      threat: parseMetric(point.threat),
    }))
    .filter((point) => point.time !== null),
)

const hasSeries = computed(() => normalizedSeries.value.length > 0)
const startTime = computed(() => normalizedSeries.value[0]?.time ?? null)
const endTime = computed(() => {
  const lastPoint = normalizedSeries.value[normalizedSeries.value.length - 1]
  return lastPoint?.time ?? null
})
const damageMax = computed(() =>
  normalizedSeries.value.reduce((maxValue, point) => Math.max(maxValue, point.villainDamage), 0),
)
const threatMax = computed(() =>
  normalizedSeries.value.reduce((maxValue, point) => Math.max(maxValue, point.threat), 0),
)
const damageDomainMax = computed(() => (damageMax.value > 0 ? damageMax.value : 1))
const threatDomainMax = computed(() => (threatMax.value > 0 ? threatMax.value : 1))

function xFor(time) {
  if (startTime.value === null || endTime.value === null) {
    return MARGINS.left
  }

  if (startTime.value === endTime.value) {
    return MARGINS.left + (plotWidth / 2)
  }

  return MARGINS.left + (((time - startTime.value) / (endTime.value - startTime.value)) * plotWidth)
}

function yFor(value, domainMax) {
  return MARGINS.top + plotHeight - ((value / domainMax) * plotHeight)
}

function buildStepPath(metric, domainMax) {
  if (!hasSeries.value) {
    return ''
  }

  const [firstPoint, ...restPoints] = normalizedSeries.value
  let path = `M ${xFor(firstPoint.time)} ${yFor(firstPoint[metric], domainMax)}`

  for (const point of restPoints) {
    path += ` H ${xFor(point.time)} V ${yFor(point[metric], domainMax)}`
  }

  return path
}

const damagePath = computed(() => buildStepPath('villainDamage', damageDomainMax.value))
const threatPath = computed(() => buildStepPath('threat', threatDomainMax.value))

const damagePoints = computed(() =>
  normalizedSeries.value.map((point) => ({
    ...point,
    x: xFor(point.time),
    y: yFor(point.villainDamage, damageDomainMax.value),
  })),
)

const threatPoints = computed(() =>
  normalizedSeries.value.map((point) => ({
    ...point,
    x: xFor(point.time),
    y: yFor(point.threat, threatDomainMax.value),
  })),
)

const guideLines = computed(() =>
  [0, 0.5, 1]
    .map((ratio) => ({
      ratio,
      y: MARGINS.top + plotHeight - (ratio * plotHeight),
      damageValue: formatAxisValue(damageDomainMax.value * ratio),
      threatValue: formatAxisValue(threatDomainMax.value * ratio),
    }))
    .reverse(),
)

const xTicks = computed(() => {
  if (!hasSeries.value || startTime.value === null) {
    return []
  }

  if (startTime.value === endTime.value) {
    return [{
      value: startTime.value,
      x: xFor(startTime.value),
      label: formatTickLabel(startTime.value),
      anchor: 'middle',
    }]
  }

  const middleValue = startTime.value + ((endTime.value - startTime.value) / 2)

  return [
    {
      value: startTime.value,
      x: xFor(startTime.value),
      label: formatTickLabel(startTime.value),
      anchor: 'start',
    },
    {
      value: middleValue,
      x: xFor(middleValue),
      label: formatTickLabel(middleValue),
      anchor: 'middle',
    },
    {
      value: endTime.value,
      x: xFor(endTime.value),
      label: formatTickLabel(endTime.value),
      anchor: 'end',
    },
  ]
})

const shouldRenderMarkers = computed(() => normalizedSeries.value.length <= 40)
const latestPoint = computed(() => normalizedSeries.value[normalizedSeries.value.length - 1] ?? null)
</script>

<template>
  <div class="trend-chart" data-testid="statistics-trend-chart">
    <div v-if="!hasSeries" class="text-muted">
      No hay datos suficientes para dibujar la evolución temporal.
    </div>

    <template v-else>
      <div class="trend-chart__legend" aria-hidden="true">
        <span class="trend-chart__legend-item trend-chart__legend-item--damage">
          <span class="trend-chart__legend-swatch"></span>
          Daño acumulado · último: {{ latestPoint?.villainDamage ?? 0 }}
        </span>
        <span class="trend-chart__legend-item trend-chart__legend-item--threat">
          <span class="trend-chart__legend-swatch"></span>
          Amenaza actual · última: {{ latestPoint?.threat ?? 0 }}
        </span>
      </div>

      <svg
        class="trend-chart__svg"
        :viewBox="`0 0 ${SVG_WIDTH} ${SVG_HEIGHT}`"
        role="img"
        aria-labelledby="trend-chart-title trend-chart-desc"
      >
        <title id="trend-chart-title">Evolución temporal de daño y amenaza</title>
        <desc id="trend-chart-desc">
          La línea azul representa el daño acumulado al villano y la línea naranja la amenaza actual,
          cada una con su propia escala vertical.
        </desc>

        <text
          :x="MARGINS.left"
          :y="16"
          class="trend-chart__axis-title trend-chart__axis-title--damage"
        >
          Daño acumulado
        </text>
        <text
          :x="SVG_WIDTH - MARGINS.right"
          :y="16"
          text-anchor="end"
          class="trend-chart__axis-title trend-chart__axis-title--threat"
        >
          Amenaza actual
        </text>

        <line
          :x1="MARGINS.left"
          :x2="SVG_WIDTH - MARGINS.right"
          :y1="SVG_HEIGHT - MARGINS.bottom"
          :y2="SVG_HEIGHT - MARGINS.bottom"
          class="trend-chart__axis"
        />
        <line
          :x1="MARGINS.left"
          :x2="MARGINS.left"
          :y1="MARGINS.top"
          :y2="SVG_HEIGHT - MARGINS.bottom"
          class="trend-chart__axis"
        />
        <line
          :x1="SVG_WIDTH - MARGINS.right"
          :x2="SVG_WIDTH - MARGINS.right"
          :y1="MARGINS.top"
          :y2="SVG_HEIGHT - MARGINS.bottom"
          class="trend-chart__axis"
        />

        <g v-for="guide in guideLines" :key="guide.ratio">
          <line
            :x1="MARGINS.left"
            :x2="SVG_WIDTH - MARGINS.right"
            :y1="guide.y"
            :y2="guide.y"
            class="trend-chart__grid"
          />
          <text
            :x="MARGINS.left - 12"
            :y="guide.y + 4"
            text-anchor="end"
            class="trend-chart__axis-label trend-chart__axis-label--damage"
          >
            {{ guide.damageValue }}
          </text>
          <text
            :x="SVG_WIDTH - MARGINS.right + 12"
            :y="guide.y + 4"
            text-anchor="start"
            class="trend-chart__axis-label trend-chart__axis-label--threat"
          >
            {{ guide.threatValue }}
          </text>
        </g>

        <path :d="damagePath" class="trend-chart__line trend-chart__line--damage" />
        <path :d="threatPath" class="trend-chart__line trend-chart__line--threat" />

        <g v-if="shouldRenderMarkers">
          <circle
            v-for="point in damagePoints"
            :key="`damage-${point.id}`"
            :cx="point.x"
            :cy="point.y"
            r="3"
            class="trend-chart__point trend-chart__point--damage"
          />
          <circle
            v-for="point in threatPoints"
            :key="`threat-${point.id}`"
            :cx="point.x"
            :cy="point.y"
            r="3"
            class="trend-chart__point trend-chart__point--threat"
          />
        </g>

        <g v-for="tick in xTicks" :key="tick.value">
          <line
            :x1="tick.x"
            :x2="tick.x"
            :y1="SVG_HEIGHT - MARGINS.bottom"
            :y2="SVG_HEIGHT - MARGINS.bottom + 6"
            class="trend-chart__tick"
          />
          <text
            :x="tick.x"
            :y="SVG_HEIGHT - MARGINS.bottom + 24"
            :text-anchor="tick.anchor"
            class="trend-chart__axis-label trend-chart__axis-label--time"
          >
            {{ tick.label }}
          </text>
        </g>
      </svg>
    </template>
  </div>
</template>

<style scoped>
.trend-chart {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.trend-chart__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.trend-chart__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9rem;
  font-weight: 600;
}

.trend-chart__legend-item--damage {
  color: var(--c-primary);
}

.trend-chart__legend-item--threat {
  color: var(--c-threat);
}

.trend-chart__legend-swatch {
  display: inline-block;
  width: 16px;
  border-top: 3px solid currentColor;
  border-radius: 999px;
}

.trend-chart__svg {
  width: 100%;
  height: auto;
}

.trend-chart__axis,
.trend-chart__tick {
  stroke: var(--c-border);
  stroke-width: 1;
}

.trend-chart__grid {
  stroke: var(--c-border);
  stroke-width: 1;
  stroke-dasharray: 4 4;
  opacity: 0.75;
}

.trend-chart__line {
  fill: none;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.trend-chart__line--damage {
  stroke: var(--c-primary);
}

.trend-chart__line--threat {
  stroke: var(--c-threat);
}

.trend-chart__point {
  stroke-width: 2;
  fill: var(--c-surface);
}

.trend-chart__point--damage {
  stroke: var(--c-primary);
}

.trend-chart__point--threat {
  stroke: var(--c-threat);
}

.trend-chart__axis-title,
.trend-chart__axis-label {
  font-size: 12px;
}

.trend-chart__axis-title--damage,
.trend-chart__axis-label--damage {
  fill: var(--c-primary);
}

.trend-chart__axis-title--threat,
.trend-chart__axis-label--threat {
  fill: var(--c-threat);
}

.trend-chart__axis-label--time {
  fill: var(--c-text-muted);
}

@media (max-width: 640px) {
  .trend-chart__legend {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
