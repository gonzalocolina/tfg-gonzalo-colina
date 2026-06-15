<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import OrganizerPageHeader from '@/components/OrganizerPageHeader.vue'
import { createEvent } from '@/api/events'
import { getVillains } from '@/api/villains'
import { getInterPodCardDefinitions } from '@/api/interPodCardDefinitions'
import { useNotificationsStore } from '@/stores/notifications'
import { formatCardCategory, formatCatalogItemType, formatInterPodMetricSummary } from '@/utils/displayLabels'

const router = useRouter()
const notif = useNotificationsStore()

const form = ref({
  name: '',
  timeLimitMinutes: 180,
  villainDefinitionId: null,
  interPodCardDefinitionIds: [],
})
const villains = ref([])
const interPodCardDefinitions = ref([])
const loadingVillains = ref(false)
const loadingInterPodCardDefinitions = ref(false)
const loading = ref(false)
const errorMsg = ref('')

async function loadVillains() {
  loadingVillains.value = true
  try {
    const { data } = await getVillains()
    villains.value = data || []
    if (!form.value.villainDefinitionId && villains.value.length) {
      form.value.villainDefinitionId = villains.value[0].id
    }
  } catch (err) {
    errorMsg.value = err.response?.data?.message || 'No se pudo cargar el catálogo de villanos'
  } finally {
    loadingVillains.value = false
  }
}

function prettyCardName(name) {
  if (!name) return '-'
  return name
    .replaceAll('_', ' ')
    .toLowerCase()
    .replaceAll(/(^|\s)\p{L}/gu, (letter) => letter.toUpperCase())
}

function definitionMetricText(definition) {
  return formatInterPodMetricSummary(definition)
}

function toggleInterPodCardDefinition(definitionId) {
  const selectedIds = form.value.interPodCardDefinitionIds
  if (selectedIds.includes(definitionId)) {
    form.value.interPodCardDefinitionIds = selectedIds.filter((id) => id !== definitionId)
    return
  }
  form.value.interPodCardDefinitionIds = [...selectedIds, definitionId]
}

async function loadInterPodCardDefinitions() {
  loadingInterPodCardDefinitions.value = true
  try {
    const { data } = await getInterPodCardDefinitions()
    interPodCardDefinitions.value = data || []
    if (!form.value.interPodCardDefinitionIds.length && interPodCardDefinitions.value.length) {
      form.value.interPodCardDefinitionIds = interPodCardDefinitions.value.map((definition) => definition.id)
    }
  } catch (err) {
    errorMsg.value = err.response?.data?.message || 'No se pudo cargar el catálogo de cartas inter-nodo'
  } finally {
    loadingInterPodCardDefinitions.value = false
  }
}

function toOptionalNumber(value) {
  if (value === '' || value == null) {
    return null
  }

  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : null
}

function buildCreateEventPayload() {
  const timeLimitMinutes = toOptionalNumber(form.value.timeLimitMinutes)
  return {
    name: form.value.name.trim(),
    timeLimitMinutes,
    villainDefinitionId: toOptionalNumber(form.value.villainDefinitionId),
    interPodCardDefinitionIds: form.value.interPodCardDefinitionIds
      .map(toOptionalNumber)
      .filter((definitionId) => definitionId != null),
  }
}

async function handleCreate() {
  errorMsg.value = ''
  if (!form.value.villainDefinitionId) {
    errorMsg.value = 'Selecciona un villano para crear el evento.'
    return
  }
  loading.value = true
  try {
    const { data: event } = await createEvent(buildCreateEventPayload())
    notif.success('Evento creado correctamente')
    router.push({ name: 'dashboard', params: { eventId: event.id } })
  } catch (err) {
    errorMsg.value =
      err.response?.data?.message ||
      err.response?.data?.details?.join(', ') ||
      'Error al crear el evento'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadVillains(), loadInterPodCardDefinitions()])
})
</script>

<template>
  <div style="margin: 24px 32px">
    <OrganizerPageHeader>
      <h1 class="page-title create-event-title">Crear Evento</h1>
    </OrganizerPageHeader>    
  </div>

  <div class="create-event-page">
    <div class="form-card create-event-form-card">
      <form @submit.prevent="handleCreate">
        <div class="form-group">
          <label for="name">Nombre del evento</label>
          <input id="name" v-model="form.name" class="form-control" required maxlength="120" />
        </div>
        <div class="form-group">
          <label for="time">Tiempo límite (min)</label>
          <input
            id="time"
            v-model.number="form.timeLimitMinutes"
            type="number"
            class="form-control"
            min="0"
            max="600"
          />
          <small class="text-muted">0 = sin temporizador</small>
        </div>

        <div class="form-group">
          <label for="villain">Villano</label>
          <select
            id="villain"
            v-model.number="form.villainDefinitionId"
            class="form-control"
            :disabled="loadingVillains || loading"
            required
          >
            <option :value="null" disabled>
              {{ loadingVillains ? 'Cargando villanos…' : 'Selecciona un villano' }}
            </option>
            <option v-for="v in villains" :key="v.id" :value="v.id">
              {{ v.name }} ({{ formatCatalogItemType(v.type) }})
            </option>
          </select>
          <small class="text-muted">
            ¿Quieres crear o editar villanos? <router-link to="/villains">Gestionarlos aquí</router-link>
          </small>
        </div>

        <div class="form-group">
          <p style="margin: 0 0 8px; font-weight: 500">Cartas inter-nodo para este evento</p>
          <div v-if="loadingInterPodCardDefinitions" class="text-muted">Cargando cartas inter-nodo…</div>
          <div v-else-if="!interPodCardDefinitions.length" class="text-muted">
            No hay cartas inter-nodo disponibles. <router-link to="/inter-pod-cards">Créelas aquí</router-link>.
          </div>
          <div v-else class="card" style="padding: 12px; border-radius: 8px">
            <label
              v-for="definition in interPodCardDefinitions"
              :key="definition.id"
              class="flex"
              style="gap: 8px; align-items: center; margin-bottom: 8px; cursor: pointer"
            >
              <input
                type="checkbox"
                :checked="form.interPodCardDefinitionIds.includes(definition.id)"
                :disabled="loading"
                @change="toggleInterPodCardDefinition(definition.id)"
              />
              <span style="margin-left: 5px;">
                <strong>{{ prettyCardName(definition.name) }}</strong>
                <span class="text-muted" style="font-size: 0.85rem">
                  · {{ formatCardCategory(definition.cardCategory) }}
                </span>
              </span>
            </label>
          </div>
          <small class="text-muted">
            Puedes gestionar el catálogo en <router-link to="/inter-pod-cards">Cartas inter-nodo</router-link>.
          </small>
        </div>

        <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>
        <div class="form-actions">
          <button class="btn btn-primary" :disabled="loading" style="flex: 1">
            {{ loading ? 'Creando…' : 'Crear evento' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.create-event-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 24px;
}

.create-event-title {
  margin-bottom: 0;
}

.create-event-form-card {
  max-width: 480px;
  margin: 0 auto;
}
</style>
