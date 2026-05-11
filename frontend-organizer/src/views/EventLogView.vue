<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getEventLogs } from '@/api/logs'
import { getEventStructure } from '@/api/setup'
import { useNotificationsStore } from '@/stores/notifications'

const route = useRoute()
const notif = useNotificationsStore()
const eventId = computed(() => route.params.eventId)

const logs = ref([])
const loading = ref(true)

// Filters
const filterGroupId = ref('')
const filterActionType = ref('')
const groups = ref([])

const ACTION_TYPES = [
  'EVENT_CREATED',
  'EVENT_UPDATED',
  'EVENT_STARTED',
  'POD_CREATED',
  'GROUP_CREATED',
  'VILLAIN_DAMAGE_REPORTED',
  'VILLAIN_DAMAGE_DELEGATED',
  'THREAT_REPORTED',
  'THREAT_DELEGATED',
  'VILLAIN_HP_CORRECTED',
  'THREAT_CORRECTED',
  'TIMER_STARTED',
  'TIMER_PAUSED',
  'TIMER_RESUMED',
  'TIMER_ADJUSTED',
  'INTER_POD_CARD_CREATED',
  'INTER_POD_CARD_DAMAGED',
  'INTER_POD_CARD_STATUS_UPDATED',
  'INTER_POD_CARD_DELETED',
  'EVENT_FINISHED',
]

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
            <option v-for="t in ACTION_TYPES" :key="t" :value="t">{{ t }}</option>
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
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in logs" :key="log.id">
              <td style="white-space: nowrap">{{ formatTime(log.timestamp) }}</td>
              <td>{{ log.actor || '-' }}</td>
              <td>{{ log.groupIdentifier || '-' }}</td>
              <td>
                <span class="badge badge-ready">{{ log.actionType }}</span>
              </td>
              <td>{{ log.previousValue || '-' }}</td>
              <td>{{ log.newValue || '-' }}</td>
              <td>{{ log.notes || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
