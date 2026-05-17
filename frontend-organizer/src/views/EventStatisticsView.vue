<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FinalSummary from '@/components/FinalSummary.vue'
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

      <div class="stats-two-columns">
        <div class="card">
          <h2 class="mb-2">Líderes destacados</h2>
          <div class="leader-grid">
            <div class="leader-card">
              <div class="leader-card__label">Más daño al villano</div>
              <div class="leader-card__value">{{ formatGroupHighlight(highlights.topDamageGroup, `${highlights.topDamageGroup?.damageToVillain || 0} daño`) }}</div>
            </div>
            <div class="leader-card">
              <div class="leader-card__label">Más amenaza añadida</div>
              <div class="leader-card__value">{{ formatGroupHighlight(highlights.topThreatAddedGroup, `${highlights.topThreatAddedGroup?.threatAdded || 0} amenaza`) }}</div>
            </div>
            <div class="leader-card">
              <div class="leader-card__label">Más amenaza retirada</div>
              <div class="leader-card__value">{{ formatGroupHighlight(highlights.topThreatRemovedGroup, `${highlights.topThreatRemovedGroup?.threatRemoved || 0} retirada`) }}</div>
            </div>
            <div class="leader-card">
              <div class="leader-card__label">Mesa más activa</div>
              <div class="leader-card__value">{{ formatGroupHighlight(highlights.mostActiveGroup, `${highlights.mostActiveGroup?.activityCount || 0} acciones`) }}</div>
            </div>
            <div class="leader-card">
              <div class="leader-card__label">Primera contribución</div>
              <div class="leader-card__value">{{ highlights.firstContributionGroup?.identifier || 'Sin datos todavía' }}</div>
            </div>
            <div class="leader-card">
              <div class="leader-card__label">Golpe final</div>
              <div class="leader-card__value">{{ highlights.finalBlowGroup?.identifier || 'Sin golpe final registrado' }}</div>
            </div>
          </div>
        </div>

        <div class="card">
          <h2 class="mb-2">Operativa e inter-pod</h2>
          <div class="summary-grid">
            <div class="summary-item">
              <div class="label">Temporizador iniciado</div>
              <div class="value">{{ operationalSummary.timerStarts || 0 }}</div>
            </div>
            <div class="summary-item">
              <div class="label">Pausas</div>
              <div class="value">{{ operationalSummary.timerPauses || 0 }}</div>
            </div>
            <div class="summary-item">
              <div class="label">Reanudaciones</div>
              <div class="value">{{ operationalSummary.timerResumes || 0 }}</div>
            </div>
            <div class="summary-item">
              <div class="label">Ajustes de timer</div>
              <div class="value">{{ operationalSummary.timerAdjustments || 0 }}</div>
            </div>
            <div class="summary-item">
              <div class="label">Cartas inter-pod creadas</div>
              <div class="value">{{ interPodSummary.created || 0 }}</div>
            </div>
            <div class="summary-item">
              <div class="label">Daño inter-pod</div>
              <div class="value">{{ interPodSummary.damageTotal || 0 }}</div>
            </div>
            <div class="summary-item">
              <div class="label">Amenaza inter-pod retirada</div>
              <div class="value">{{ interPodSummary.threatReducedTotal || 0 }}</div>
            </div>
            <div class="summary-item">
              <div class="label">Estados/elim. inter-pod</div>
              <div class="value">{{ (interPodSummary.statusUpdates || 0) + (interPodSummary.deleted || 0) }}</div>
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

.stats-two-columns {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;
}

.leader-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.leader-card {
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: var(--c-surface-alt);
  padding: 14px;
}

.leader-card__label {
  font-size: 0.78rem;
  color: var(--c-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 6px;
}

.leader-card__value {
  font-size: 0.98rem;
  font-weight: 600;
}

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
  .stats-two-columns,
  .timeline-entry {
    grid-template-columns: 1fr;
    display: grid;
  }

  .stats-page-header {
    display: flex;
    flex-direction: column;
  }

  .stats-page-header__actions {
    justify-content: flex-start;
  }
}
</style>
