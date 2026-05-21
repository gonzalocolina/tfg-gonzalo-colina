<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getEventLogs, revertEventLog } from '@/api/logs'
import { getEventStructure } from '@/api/setup'
import { useNotificationsStore } from '@/stores/notifications'
import { LOG_ACTION_TYPES, formatLogActionType } from '@/utils/displayLabels'

const route = useRoute()
const notif = useNotificationsStore()
const eventId = computed(() => route.params.eventId)

const logs = ref([])
const loading = ref(true)
const revertingLogId = ref(null)

// Filters
const filterGroupId = ref('')
const filterActionType = ref('')
const groups = ref([])

const ACTION_TYPES = LOG_ACTION_TYPES
const REVERTIBLE_ACTION_TYPES = new Set([
  'VILLAIN_DAMAGE_REPORTED',
  'VILLAIN_DAMAGE_DELEGATED',
  'THREAT_REPORTED',
  'THREAT_DELEGATED',
  'THREAT_REMOVED',
  'THREAT_REMOVED_DELEGATED',
  'VILLAIN_HP_CORRECTED',
  'THREAT_CORRECTED',
])

const revertedLogIds = computed(() => new Set(logs.value.map((log) => log.revertedLogId).filter(Boolean)))

async function loadGroups() {
  try {
    const { data } = await getEventStructure(eventId.value)
    const all = []
    for (const pod of data.pods || []) {
      for (const g of pod.groups || []) {
        all.push({ id: g.id, identifier: g.identifier, podName: pod.name })
      }
    }
    for (const g of data.unassignedGroups || []) {
      all.push({ id: g.id, identifier: g.identifier, podName: 'Pendiente de asignación' })
    }
    groups.value = all
  } catch {
    /* ignore */
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const filters = {}
    if (filterGroupId.value) filters.groupId = filterGroupId.value
    if (filterActionType.value) filters.actionType = filterActionType.value
    const { data } = await getEventLogs(eventId.value, filters)
    logs.value = data
  } catch {
    notif.error('Error al cargar los logs')
  } finally {
    loading.value = false
  }
}

function canRevert(log) {
  return REVERTIBLE_ACTION_TYPES.has(log.actionType) && !revertedLogIds.value.has(log.id)
}

async function handleRevert(log) {
  revertingLogId.value = log.id
  try {
    await revertEventLog(eventId.value, log.id, {})
    notif.success('Registro revertido correctamente')
    await loadLogs()
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al revertir el log')
  } finally {
    revertingLogId.value = null
  }
}

function formatTime(iso) {
  if (!iso) return '-'
  return new Date(iso).toLocaleTimeString('es-ES', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

onMounted(() => {
  loadGroups()
  loadLogs()
})
</script>

<template>
  <div>
    <h1 class="page-title">Registro de Eventos (Log)</h1>

    <!-- Filters -->
    <div class="card">
      <div class="flex gap-2" style="align-items: flex-end">
        <div class="form-group" style="margin-bottom: 0; flex: 1">
          <label for="filter-group">Mesa</label>
          <select id="filter-group" v-model="filterGroupId" class="form-control" @change="loadLogs">
            <option value="">Todos</option>
            <option v-for="g in groups" :key="g.id" :value="g.id">
              {{ g.identifier }} ({{ g.podName }})
            </option>
          </select>
        </div>
        <div class="form-group" style="margin-bottom: 0; flex: 1">
          <label for="filter-action">Tipo de acción</label>
          <select id="filter-action" v-model="filterActionType" class="form-control" @change="loadLogs">
            <option value="">Todos</option>
            <option v-for="t in ACTION_TYPES" :key="t" :value="t">{{ formatLogActionType(t) }}</option>
          </select>
        </div>
        <button class="btn btn-secondary btn-sm" @click="loadLogs">Actualizar</button>
      </div>
    </div>

    <!-- Log table -->
    <div class="card mt-1">
      <div v-if="loading" class="text-muted">Cargando…</div>
      <div v-else-if="!logs.length" class="text-muted">No hay entradas de log.</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Hora</th>
              <th>Actor</th>
              <th>Mesa</th>
              <th>Acción</th>
              <th>Antes</th>
              <th>Después</th>
              <th>Notas</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in logs" :key="log.id">
              <td style="white-space: nowrap">{{ formatTime(log.timestamp) }}</td>
              <td>{{ log.actor || '-' }}</td>
              <td>{{ log.groupIdentifier || '-' }}</td>
              <td>
                <span class="badge badge-ready">{{ formatLogActionType(log.actionType) }}</span>
              </td>
              <td>{{ log.previousValue || '-' }}</td>
              <td>{{ log.newValue || '-' }}</td>
              <td>{{ log.notes || '-' }}</td>
              <td style="white-space: nowrap">
                <button
                  v-if="canRevert(log)"
                  :data-testid="`revert-log-${log.id}`"
                  class="btn btn-secondary btn-sm"
                  :disabled="revertingLogId === log.id"
                  @click="handleRevert(log)"
                >
                  {{ revertingLogId === log.id ? 'Revirtiendo…' : 'Revertir' }}
                </button>
                <span v-else-if="revertedLogIds.has(log.id)" class="text-muted">Ya revertido</span>
                <span v-else-if="log.revertedLogId" class="text-muted">Revierte #{{ log.revertedLogId }}</span>
                <span v-else class="text-muted">-</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
