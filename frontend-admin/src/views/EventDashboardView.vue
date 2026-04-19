<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getEvent, updateEvent, deleteEvent } from '@/api/events'
import { getVillains } from '@/api/villains'
import { useNotificationsStore } from '@/stores/notifications'

const route = useRoute()
const router = useRouter()
const notif = useNotificationsStore()

const eventId = computed(() => route.params.eventId)
const event = ref(null)
const loading = ref(true)
const editing = ref(false)
const editForm = ref({
  name: '',
  timeLimitMinutes: 180,
  villainDefinitionId: null,
})
const villains = ref([])
const loadingVillains = ref(false)
const saving = ref(false)
const isOwner = computed(() => event.value?.currentUserIsOwner === true)
const delegatedResponsibilities = computed(() => event.value?.currentUserResponsibilities || [])

async function load() {
  loading.value = true
  try {
    const { data } = await getEvent(eventId.value)
    event.value = data
  } catch {
    notif.error('Error al cargar el evento')
  } finally {
    loading.value = false
  }
}

async function loadVillains() {
  loadingVillains.value = true
  try {
    const { data } = await getVillains()
    villains.value = data || []
  } catch {
    /* ignore: dashboard can still render with snapshot */
  } finally {
    loadingVillains.value = false
  }
}

function startEdit() {
  editForm.value = {
    name: event.value.name,
    timeLimitMinutes: event.value.timeLimitMinutes,
    villainDefinitionId: event.value.villainDefinitionId,
  }
  editing.value = true
}

async function saveEdit() {
  saving.value = true
  try {
    const { data } = await updateEvent(eventId.value, editForm.value)
    event.value = data
    editing.value = false
    notif.success('Evento actualizado')
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al actualizar')
  } finally {
    saving.value = false
  }
}

async function handleDelete() {
  if (!confirm('¿Eliminar este evento? Esta acción no se puede deshacer.')) return
  try {
    await deleteEvent(eventId.value)
    notif.success('Evento eliminado')
    router.push({ name: 'my-events' })
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al eliminar')
  }
}

function statusBadgeClass(status) {
  return {
    DRAFT: 'badge-draft',
    READY: 'badge-ready',
    IN_PROGRESS: 'badge-in-progress',
    FINISHED: 'badge-finished',
    CANCELLED: 'badge-cancelled',
  }[status] || 'badge-draft'
}

function prettyCardName(name) {
  if (!name) return '—'
  return name
    .replaceAll('_', ' ')
    .toLowerCase()
    .replaceAll(/(^|\s)\p{L}/gu, (letter) => letter.toUpperCase())
}

onMounted(load)
onMounted(loadVillains)
</script>

<template>
  <div>
    <h1 class="page-title">Dashboard del Evento</h1>

    <div v-if="loading" class="text-muted">Cargando…</div>

    <template v-else-if="event">
      <!-- Info card -->
      <div class="card">
        <div class="card-header">
          <h2>{{ event.name }}</h2>
          <div style="display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end">
            <span class="badge" :class="isOwner ? 'badge-ready' : 'badge-in-progress'">
              {{ isOwner ? 'Organizador principal' : 'Co-organizador' }}
            </span>
            <span class="badge" :class="statusBadgeClass(event.status)">
              {{ event.status }}
            </span>
          </div>
        </div>

        <div v-if="!isOwner" class="card" style="padding: 14px; margin-bottom: 14px; border-left: 4px solid var(--c-primary)">
          <p style="margin: 0; font-weight: 600">Acceso delegado</p>
          <p class="text-muted" style="margin-top: 6px; font-size: 0.9rem">
            Estás viendo este evento como co-organizador.
            Responsabilidades activas: {{ delegatedResponsibilities.length ? delegatedResponsibilities.join(', ') : 'sin permisos globales específicos' }}.
          </p>
        </div>

        <div v-if="!editing" class="summary-grid">
          <div class="summary-item">
            <div class="label">Jugadores totales</div>
            <div class="value">{{ event.configuredPlayerCount }}</div>
          </div>
          <div class="summary-item">
            <div class="label">Tiempo límite</div>
            <div class="value">
              {{ event.timeLimitMinutes > 0 ? event.timeLimitMinutes + ' min' : 'Sin límite' }}
            </div>
          </div>
          <div class="summary-item">
            <div class="label">Código evento</div>
            <div class="value code-tag">{{ event.eventCode }}</div>
          </div>
          <div class="summary-item">
            <div class="label">HP Villano</div>
            <div class="value">{{ event.villainHpCurrent }} / {{ event.villainHpMax }}</div>
          </div>
          <div class="summary-item">
            <div class="label">Villano</div>
            <div class="value" style="font-size: 1rem">{{ event.villainName }}</div>
          </div>
          <div class="summary-item">
            <div class="label">Amenaza</div>
            <div class="value">{{ event.schemeThreatCurrent }} / {{ event.schemeThreatTarget }}</div>
          </div>
          <div class="summary-item">
            <div class="label">Fórmula HP</div>
            <div class="value" style="font-size: 1rem">
              {{ event.villainHpRuleType }}: {{ event.villainHpRuleValue }}
            </div>
          </div>
          <div class="summary-item">
            <div class="label">Fórmula Amenaza</div>
            <div class="value" style="font-size: 1rem">
              {{ event.villainThreatRuleType }}: {{ event.villainThreatRuleValue }}
            </div>
          </div>
          <div class="summary-item">
            <div class="label">Organizador</div>
            <div class="value" style="font-size: 1rem">{{ event.organizerUsername }}</div>
          </div>
          <div class="summary-item" style="grid-column: 1 / -1">
            <div class="label">Cartas inter-pod seleccionadas</div>
            <div class="value" style="font-size: 1rem">
              <template v-if="event.selectedInterPodCardDefinitions?.length">
                {{ event.selectedInterPodCardDefinitions.map((definition) => prettyCardName(definition.name)).join(', ') }}
              </template>
              <template v-else>
                No hay cartas inter-pod seleccionadas.
              </template>
            </div>
          </div>
        </div>

        <!-- Edit form -->
        <form v-else @submit.prevent="saveEdit">
          <div class="form-group">
            <label for="edit-name">Nombre</label>
            <input id="edit-name" v-model="editForm.name" class="form-control" required maxlength="120" />
          </div>
          <div class="card" style="padding: 14px; margin-bottom: 14px">
            <p style="margin: 0; font-weight: 600">Jugadores totales actuales</p>
            <p class="text-muted" style="margin-top: 6px; font-size: 0.9rem">
              {{ event.configuredPlayerCount }} jugadores. Este valor se calcula automáticamente con las mesas del evento y no se edita manualmente.
            </p>
          </div>
          <div class="form-group">
            <label for="edit-time">Tiempo (min)</label>
            <input
              id="edit-time"
              v-model.number="editForm.timeLimitMinutes"
              type="number"
              class="form-control"
              min="0"
              max="600"
            />
          </div>
          <div class="form-group">
            <label for="edit-villain">Villano</label>
            <select
              id="edit-villain"
              v-model.number="editForm.villainDefinitionId"
              class="form-control"
              :disabled="loadingVillains"
              required
            >
              <option :value="null" disabled>
                {{ loadingVillains ? 'Cargando villanos…' : 'Selecciona un villano' }}
              </option>
              <option v-for="v in villains" :key="v.id" :value="v.id">
                {{ v.name }} ({{ v.type }})
              </option>
            </select>
          </div>
          <div class="form-actions">
            <button class="btn btn-primary" :disabled="saving">Guardar</button>
            <button type="button" class="btn btn-secondary" @click="editing = false">
              Cancelar
            </button>
          </div>
        </form>
      </div>

      <!-- Actions -->
      <div v-if="isOwner && event.status === 'DRAFT'" class="flex gap-1 mt-2">
        <button class="btn btn-secondary" @click="startEdit">Editar evento</button>
        <router-link :to="{ name: 'event-villains', params: { eventId } }" class="btn btn-secondary">
          Gestionar villanos
        </router-link>
        <router-link :to="{ name: 'event-inter-pod-cards', params: { eventId } }" class="btn btn-secondary">
          Gestionar cartas inter-pod
        </router-link>
        <button class="btn btn-danger" @click="handleDelete">Eliminar evento</button>
        <router-link
          :to="{ name: 'setup', params: { eventId } }"
          class="btn btn-primary"
        >
          Configurar Pods y Mesas
        </router-link>
        <router-link
          :to="{ name: 'game', params: { eventId } }"
          class="btn btn-primary"
        >
          Ir a la partida
        </router-link>
      </div>

      <div v-if="isOwner && event.status === 'READY'" class="flex gap-1 mt-2">
        <router-link
          :to="{ name: 'game', params: { eventId } }"
          class="btn btn-primary"
        >
          Ir a la partida
        </router-link>
        <router-link
          :to="{ name: 'setup', params: { eventId } }"
          class="btn btn-secondary"
        >
          Ver estructura
        </router-link>
        <router-link
          :to="{ name: 'logs', params: { eventId } }"
          class="btn btn-secondary"
        >
          Ver registro de eventos
        </router-link>
      </div>

      <div v-if="isOwner && event.status === 'IN_PROGRESS'" class="flex gap-1 mt-2">
        <router-link
          :to="{ name: 'game', params: { eventId } }"
          class="btn btn-primary"
        >
          Panel de partida
        </router-link>
        <router-link
          :to="{ name: 'logs', params: { eventId } }"
          class="btn btn-secondary"
        >
          Ver registro de eventos
        </router-link>
      </div>

      <div v-if="event.status === 'FINISHED'" class="flex gap-1 mt-2">
        <router-link
          :to="{ name: 'game', params: { eventId } }"
          class="btn btn-secondary"
        >
          Ver resumen final
        </router-link>
        <router-link
          :to="{ name: 'logs', params: { eventId } }"
          class="btn btn-secondary"
        >
          Ver registro de eventos
        </router-link>
      </div>

      <div v-if="!isOwner && event.status !== 'FINISHED'" class="flex gap-1 mt-2">
        <router-link
          :to="{ name: 'game', params: { eventId } }"
          class="btn btn-primary"
        >
          Ir al panel del evento
        </router-link>
        <router-link
          :to="{ name: 'logs', params: { eventId } }"
          class="btn btn-secondary"
        >
          Ver registro de eventos
        </router-link>
      </div>
    </template>

    <div v-else class="text-muted">No se pudo cargar el evento. Comprueba la conexión con el servidor.</div>
  </div>
</template>
