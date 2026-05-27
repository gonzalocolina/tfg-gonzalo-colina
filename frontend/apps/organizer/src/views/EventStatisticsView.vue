<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FinalSummary from '@/components/FinalSummary.vue'
import StatisticsTrendChart from '@/components/StatisticsTrendChart.vue'
import { getEventLogs } from '@/api/logs'
import { getEventStructure } from '@/api/setup'
import { useGameStateStore } from '@/stores/gameState'
import { useNotificationsStore } from '@/stores/notifications'
import {
  eventStatusBadgeClass,
  formatEventStatus,
  formatLogActionType,
} from '@/utils/displayLabels'
import { buildEventStatistics } from '@/utils/eventStatistics'

const route = useRoute()
const router = useRouter()
const gs = useGameStateStore()
const notif = useNotificationsStore()

const eventId = computed(() => route.params.eventId)
const loading = ref(true)
const loadError = ref('')
const structure = ref(null)
const stats = ref(null)

const eventName = computed(() => structure.value?.eventName || `Evento ${eventId.value}`)
const visibleTimelineEntries = computed(() => stats.value?.timelineEntries.slice(-25) || [])
const rankingRows = computed(() => stats.value?.groups || [])
const highlights = computed(() => stats.value?.highlights || {})
const totals = computed(() => stats.value?.totals || {})
const operationalSummary = computed(() => stats.value?.operationalSummary || {})
const interPodSummary = computed(() => stats.value?.interPodSummary || {})
const progressionSeries = computed(() => stats.value?.progression?.series || [])

function formatDateTime(isoValue) {
  if (!isoValue) return '-'

  return new Date(isoValue).toLocaleString('es-ES', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function formatDuration(seconds) {
  if (!seconds) return '-'

  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remainingSeconds = seconds % 60
  const parts = []

  if (hours) parts.push(`${hours}h`)
  if (minutes) parts.push(`${minutes}m`)
  parts.push(`${remainingSeconds}s`)

  return parts.join(' ')
}

function formatSignedSeconds(seconds) {
  if (!Number.isFinite(seconds) || seconds === 0) {
    return '0 min'
  }

  const minutes = Math.round(seconds / 60)
  return `${minutes > 0 ? '+' : ''}${minutes} min`
}

function formatChange(entry) {
  if (!entry) return '-'

  if (entry.numericPreviousValue !== null && entry.numericNewValue !== null) {
    return `${entry.previousValue} → ${entry.newValue}`
  }

  if (entry.previousValue && entry.newValue) {
    return `${entry.previousValue} → ${entry.newValue}`
  }

  return entry.newValue || entry.previousValue || '-'
}

function formatGroupHighlight(group, metric) {
  if (!group) return 'Sin datos todavía'
  return `${group.identifier} · ${metric}`
}

async function load() {
  loading.value = true
  loadError.value = ''

  try {
    await gs.fetchGameState(eventId.value)

    if (gs.eventStatus !== 'FINISHED') {
      notif.warning('Las estadísticas solo están disponibles cuando la partida ha finalizado.')
      await router.replace({
        name: 'game',
        params: { eventId: eventId.value },
      })
      return
    }

    const [{ data: logsData }, { data: structureData }] = await Promise.all([
      getEventLogs(eventId.value),
      getEventStructure(eventId.value),
    ])

    structure.value = structureData
    stats.value = buildEventStatistics({
      logs: logsData || [],
      structure: structureData,
      finalSummary: gs.finalSummary,
      gameState: {
        villainHpMax: gs.villainHpMax,
      },
    })
  } catch (err) {
    loadError.value = err.response?.data?.message || 'No se pudieron cargar las estadísticas de la partida.'
    notif.error(loadError.value)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="stats-page-header">
      <div>
        <h1 class="page-title" style="margin-bottom: 0">Estadísticas de la partida</h1>
        <p class="text-muted stats-subtitle">
          {{ eventName }} · Villano: <strong>{{ gs.villainName || 'Sin villano' }}</strong>
        </p>
      </div>

      <div class="stats-page-header__actions">
        <span class="badge" :class="eventStatusBadgeClass(gs.eventStatus)">
          {{ formatEventStatus(gs.eventStatus) }}
        </span>
        <router-link :to="{ name: 'game', params: { eventId } }" class="btn btn-secondary btn-sm">
          Ver partida
        </router-link>
        <router-link :to="{ name: 'logs', params: { eventId } }" class="btn btn-secondary btn-sm">
          Ver registro
        </router-link>
      </div>
    </div>

    <div v-if="loading" class="card text-muted">Cargando estadísticas…</div>

    <div v-else-if="loadError" class="card">
      <p class="text-danger">{{ loadError }}</p>
      <button class="btn btn-secondary btn-sm mt-1" @click="load">Reintentar</button>
    </div>

    <template v-else-if="stats">
      <FinalSummary v-if="gs.finalSummary" :summary="gs.finalSummary" />

      <div class="card">
        <div class="card-header">
          <h2>Vista rápida</h2>
          <span class="text-muted">
            {{ formatDuration(gs.finalSummary?.durationSeconds || 0) }} · {{ totals.activeGroupsCount || 0 }} mesas activas
          </span>
        </div>

        <div class="summary-grid" data-testid="statistics-overview">
          <div class="summary-item">
            <div class="label">Acciones de partida</div>
            <div class="value">{{ totals.actionCount || 0 }}</div>
          </div>
          <div class="summary-item">
            <div class="label">Mesas activas</div>
            <div class="value">{{ totals.activeGroupsCount || 0 }} / {{ totals.totalGroupsCount || 0 }}</div>
          </div>
          <div class="summary-item">
            <div class="label">Jugadores configurados</div>
            <div class="value">{{ totals.configuredPlayerCount || 0 }}</div>
          </div>
          <div class="summary-item">
            <div class="label">Acciones delegadas</div>
            <div class="value">{{ operationalSummary.delegatedActions || 0 }}</div>
          </div>
          <div class="summary-item">
            <div class="label">Correcciones manuales</div>
            <div class="value">{{ operationalSummary.manualCorrections || 0 }}</div>
          </div>
          <div class="summary-item">
            <div class="label">Ajuste neto del timer</div>
            <div class="value">{{ formatSignedSeconds(operationalSummary.timerNetAdjustmentSeconds || 0) }}</div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-header chart-card-header">
          <div>
            <h2>Evolución de daño y amenaza</h2>
            <p class="text-muted chart-card__subtitle">
              Ejes independientes: daño acumulado a la izquierda y amenaza actual a la derecha.
            </p>
          </div>
          <span class="text-muted">{{ progressionSeries.length }} hitos</span>
        </div>

        <div v-if="!progressionSeries.length" class="text-muted">
          No hay suficiente actividad registrada para dibujar la evolución temporal.
        </div>
        <StatisticsTrendChart v-else :series="progressionSeries" />
      </div>

      <div class="card">
        <h2 class="mb-2">Protagonistas de la partida</h2>

        <div class="mvp-row">
          <div class="mvp-tile mvp-tile--damage" :class="{ 'mvp-tile--empty': !highlights.topDamageGroup }">
            <div class="mvp-tile__category">Mayor daño al villano</div>
            <div class="mvp-tile__name">{{ highlights.topDamageGroup?.identifier || '—' }}</div>
            <div class="mvp-tile__stat" v-if="highlights.topDamageGroup">
              <span class="mvp-tile__big-num">{{ highlights.topDamageGroup.damageToVillain }}</span>
              <span class="mvp-tile__unit">daño infligido</span>
            </div>
            <div class="mvp-tile__empty-msg" v-else>Sin datos todavía</div>
          </div>

          <div class="mvp-tile mvp-tile--active" :class="{ 'mvp-tile--empty': !highlights.mostActiveGroup }">
            <div class="mvp-tile__category">Mesa más activa</div>
            <div class="mvp-tile__name">{{ highlights.mostActiveGroup?.identifier || '—' }}</div>
            <div class="mvp-tile__stat" v-if="highlights.mostActiveGroup">
              <span class="mvp-tile__big-num">{{ highlights.mostActiveGroup.activityCount }}</span>
              <span class="mvp-tile__unit">acciones</span>
            </div>
            <div class="mvp-tile__empty-msg" v-else>Sin datos todavía</div>
          </div>

          <div class="mvp-tile mvp-tile--finish" :class="{ 'mvp-tile--empty': !highlights.finalBlowGroup }">
            <div class="mvp-tile__category">Golpe final</div>
            <div class="mvp-tile__name">{{ highlights.finalBlowGroup?.identifier || 'Sin golpe final' }}</div>
            <div class="mvp-tile__stat" v-if="highlights.finalBlowGroup">
              <span class="mvp-tile__unit">Último golpe al villano registrado</span>
            </div>
            <div class="mvp-tile__empty-msg" v-else>No registrado</div>
          </div>
        </div>

        <div class="secondary-row">
          <div class="secondary-item">
            <div class="secondary-item__label">Mayor amenaza añadida</div>
            <div class="secondary-item__value">
              <span v-if="highlights.topThreatAddedGroup">
                {{ highlights.topThreatAddedGroup.identifier }}
                <span class="secondary-item__stat">· {{ highlights.topThreatAddedGroup.threatAdded }}</span>
              </span>
              <span v-else class="text-muted">Sin datos</span>
            </div>
          </div>
          <div class="secondary-item">
            <div class="secondary-item__label">Mayor amenaza retirada</div>
            <div class="secondary-item__value">
              <span v-if="highlights.topThreatRemovedGroup">
                {{ highlights.topThreatRemovedGroup.identifier }}
                <span class="secondary-item__stat">· {{ highlights.topThreatRemovedGroup.threatRemoved }}</span>
              </span>
              <span v-else class="text-muted">Sin datos</span>
            </div>
          </div>
          <div class="secondary-item">
            <div class="secondary-item__label">Primera contribución</div>
            <div class="secondary-item__value">
              <span v-if="highlights.firstContributionGroup">{{ highlights.firstContributionGroup.identifier }}</span>
              <span v-else class="text-muted">Sin datos</span>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <h2 class="mb-2">Operativa de la partida</h2>

        <div class="ops-panel">
          <div class="ops-panel__section">
            <div class="ops-panel__section-title">General</div>
            <div class="ops-kpi-grid">
              <div class="ops-kpi">
                <div class="ops-kpi__val">{{ operationalSummary.delegatedActions || 0 }}</div>
                <div class="ops-kpi__lbl">Acciones delegadas</div>
              </div>
              <div class="ops-kpi">
                <div class="ops-kpi__val">{{ operationalSummary.manualCorrections || 0 }}</div>
                <div class="ops-kpi__lbl">Correcciones manuales</div>
              </div>
              <div class="ops-kpi" :class="{ 'ops-kpi--nonzero': operationalSummary.timerNetAdjustmentSeconds !== 0 }">
                <div class="ops-kpi__val">{{ formatSignedSeconds(operationalSummary.timerNetAdjustmentSeconds || 0) }}</div>
                <div class="ops-kpi__lbl">Ajuste neto del timer</div>
              </div>
              <div class="ops-kpi">
                <div class="ops-kpi__val">{{ operationalSummary.timerPauses || 0 }}</div>
                <div class="ops-kpi__lbl">Pausas de partida</div>
              </div>
            </div>
          </div>

          <div class="ops-panel__divider" aria-hidden="true"></div>

          <div class="ops-panel__section">
            <div class="ops-panel__section-title">Inter-pod</div>
            <div class="ops-kpi-grid">
              <div class="ops-kpi">
                <div class="ops-kpi__val">{{ interPodSummary.created || 0 }}</div>
                <div class="ops-kpi__lbl">Cartas creadas</div>
              </div>
              <div class="ops-kpi">
                <div class="ops-kpi__val">{{ interPodSummary.damageTotal || 0 }}</div>
                <div class="ops-kpi__lbl">Daño aplicado</div>
              </div>
              <div class="ops-kpi">
                <div class="ops-kpi__val">{{ interPodSummary.threatReducedTotal || 0 }}</div>
                <div class="ops-kpi__lbl">Amenaza reducida</div>
              </div>
              <div class="ops-kpi">
                <div class="ops-kpi__val">{{ interPodSummary.defeated || 0 }}</div>
                <div class="ops-kpi__lbl">Cartas derrotadas</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <h2>Rendimiento por mesa</h2>
          <span class="text-muted">Ordenado por daño al villano</span>
        </div>

        <div v-if="!rankingRows.length" class="text-muted">No hay mesas configuradas para este evento.</div>
        <div v-else class="table-wrap">
          <table data-testid="statistics-groups-table">
            <thead>
              <tr>
                <th>Mesa</th>
                <th>Pod</th>
                <th>Jug.</th>
                <th>Daño</th>
                <th>Amenaza +</th>
                <th>Amenaza -</th>
                <th>Balance</th>
                <th>Actividad</th>
                <th>Inter-pod</th>
                <th>Hitos</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="group in rankingRows" :key="group.identifier">
                <td>
                  <strong>{{ group.identifier }}</strong>
                  <div v-if="group.firstContributionAt" class="text-muted table-subvalue">
                    Primera acción: {{ formatDateTime(group.firstContributionAt) }}
                  </div>
                </td>
                <td>{{ group.podName }}</td>
                <td>{{ group.playerCount || '-' }}</td>
                <td>{{ group.damageToVillain }}</td>
                <td>{{ group.threatAdded }}</td>
                <td>{{ group.threatRemoved }}</td>
                <td>{{ group.threatNet }}</td>
                <td>{{ group.activityCount }}</td>
                <td>{{ group.interPodActionCount }}</td>
                <td>
                  <span v-if="group.finalBlow" class="badge badge-ready">Golpe final</span>
                  <span v-else class="text-muted">-</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <h2>Cronología relevante</h2>
          <span class="text-muted">Últimas {{ visibleTimelineEntries.length }} entradas</span>
        </div>

        <div v-if="!visibleTimelineEntries.length" class="text-muted">
          No hay hitos suficientes para reconstruir la partida.
        </div>

        <div v-else class="timeline-list">
          <article v-for="entry in visibleTimelineEntries" :key="entry.id" class="timeline-entry">
            <div class="timeline-entry__time">{{ formatDateTime(entry.timestamp) }}</div>
            <div class="timeline-entry__content">
              <div class="timeline-entry__title-row">
                <span class="badge badge-in-progress">{{ formatLogActionType(entry.actionType) }}</span>
                <span class="text-muted">{{ entry.groupIdentifier || entry.actor || 'Sistema' }}</span>
              </div>
              <div class="timeline-entry__value">{{ formatChange(entry) }}</div>
              <div v-if="entry.notes" class="timeline-entry__notes text-muted">{{ entry.notes }}</div>
            </div>
          </article>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.stats-page-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.stats-page-header__actions {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.stats-subtitle {
  margin-top: 6px;
  font-size: 0.92rem;
}

.chart-card-header {
  align-items: flex-start;
}

.chart-card__subtitle {
  margin: 6px 0 0;
  font-size: 0.86rem;
}

/* MVP podium */
.mvp-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.mvp-tile {
  border-radius: var(--radius);
  padding: 18px 16px;
  background: var(--c-surface-alt);
  border-top: 3px solid transparent;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mvp-tile--damage  { border-top-color: #d97706; }
.mvp-tile--active  { border-top-color: #2563eb; }
.mvp-tile--finish  { border-top-color: #dc2626; }

.mvp-tile--empty { opacity: 0.55; }

.mvp-tile__category {
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--c-text-muted);
}

.mvp-tile__name {
  font-size: 1.1rem;
  font-weight: 700;
  word-break: break-word;
  line-height: 1.2;
}

.mvp-tile__stat {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin-top: 4px;
}

.mvp-tile__big-num {
  font-size: 1.8rem;
  font-weight: 800;
  line-height: 1;
}

.mvp-tile__unit {
  font-size: 0.8rem;
  color: var(--c-text-muted);
}

.mvp-tile__empty-msg {
  font-size: 0.82rem;
  color: var(--c-text-muted);
  font-style: italic;
}

/* Secondary awards row */
.secondary-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-top: 1px solid var(--c-border);
  padding-top: 14px;
}

.secondary-item {
  padding: 6px 14px;
  border-right: 1px solid var(--c-border);
}

.secondary-item:first-child { padding-left: 0; }
.secondary-item:last-child  { border-right: none; }

.secondary-item__label {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--c-text-muted);
  margin-bottom: 4px;
}

.secondary-item__value {
  font-size: 0.88rem;
  font-weight: 600;
}

.secondary-item__stat {
  font-weight: 400;
  color: var(--c-text-muted);
}

/* Ops panel */
.ops-panel {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 24px;
  align-items: start;
}

.ops-panel__divider {
  width: 1px;
  background: var(--c-border);
  align-self: stretch;
  margin-top: 30px;
}

.ops-panel__section-title {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--c-text-muted);
  margin-bottom: 14px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--c-border);
}

.ops-kpi-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 20px;
}

.ops-kpi {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ops-kpi__val {
  font-size: 1.45rem;
  font-weight: 700;
  line-height: 1.1;
}

.ops-kpi__lbl {
  font-size: 0.76rem;
  color: var(--c-text-muted);
}

.ops-kpi--nonzero .ops-kpi__val { color: #d97706; }

.table-subvalue {
  margin-top: 4px;
  font-size: 0.78rem;
}

.timeline-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.timeline-entry {
  display: grid;
  grid-template-columns: 180px 1fr;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  background: var(--c-surface-alt);
}

.timeline-entry__time {
  font-size: 0.82rem;
  color: var(--c-text-muted);
}

.timeline-entry__title-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.timeline-entry__value {
  margin-top: 6px;
  font-weight: 600;
}

.timeline-entry__notes {
  margin-top: 4px;
  font-size: 0.84rem;
}

@media (max-width: 900px) {
  .stats-page-header,
  .timeline-entry {
    grid-template-columns: 1fr;
    display: grid;
  }

  .chart-card-header {
    gap: 10px;
  }

  .stats-page-header {
    display: flex;
    flex-direction: column;
  }

  .stats-page-header__actions {
    justify-content: flex-start;
  }

  .mvp-row {
    grid-template-columns: 1fr;
  }

  .secondary-row {
    grid-template-columns: 1fr;
  }

  .secondary-item {
    padding: 8px 0;
    border-right: none;
    border-bottom: 1px solid var(--c-border);
  }

  .secondary-item:last-child { border-bottom: none; }

  .ops-panel {
    grid-template-columns: 1fr;
  }

  .ops-panel__divider {
    width: 100%;
    height: 1px;
    margin-top: 0;
  }
}
</style>
