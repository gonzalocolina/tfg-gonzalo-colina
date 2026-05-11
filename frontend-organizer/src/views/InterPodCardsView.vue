<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationsStore } from '@/stores/notifications'
import { getEvent, updateEvent } from '@/api/events'
import {
  getInterPodCardDefinitions,
  createInterPodCardDefinition,
  updateInterPodCardDefinition,
  deleteInterPodCardDefinition,
} from '@/api/interPodCardDefinitions'
import { useProtectedImageUrls } from '@/composables/useProtectedImageUrls'

const route = useRoute()
const notif = useNotificationsStore()

const eventId = computed(() => route?.params?.eventId || null)
const isEventScoped = computed(() => Boolean(eventId.value))
const backRoute = computed(() => {
  if (eventId.value) {
    return {
      name: 'dashboard',
      params: { eventId: eventId.value },
    }
  }

  return { name: 'my-events' }
})
const backLabel = computed(() => (eventId.value ? 'Volver al evento' : 'Volver a eventos'))
const pageTitle = computed(() => (isEventScoped.value ? 'Cartas Inter-Nodo del Evento' : 'Cartas Inter-Nodo'))

const definitions = ref([])
const event = ref(null)
const loading = ref(true)
const loadingEvent = ref(false)
const savingCreate = ref(false)
const savingEdit = ref(false)
const savingSelection = ref(false)
const deletingId = ref(null)
const editingId = ref(null)
const selectedDefinitionIds = ref([])

const CATEGORY_OPTIONS = ['PLAN', 'ESBIRRO']
const STATUS_OPTIONS = ['OCULTA', 'REVELADA', 'DERROTADA']

const createForm = ref({
  name: '',
  cardCategory: 'ESBIRRO',
  defaultHpMax: 8,
  defaultStatus: 'REVELADA',
})
const createImage = ref(null)

const editForm = ref({
  name: '',
  cardCategory: 'ESBIRRO',
  defaultHpMax: 8,
  defaultStatus: 'REVELADA',
})
const editImage = ref(null)
const editImagePreviewUrl = ref(null)
const { getProtectedImageUrl, syncImageUrls } = useProtectedImageUrls('auth')

const canEditEventSelection = computed(() => {
  if (!isEventScoped.value || !event.value) return false
  return event.value.currentUserIsOwner === true && event.value.status === 'DRAFT'
})

const currentEventDefinitionIds = computed(() =>
  (event.value?.selectedInterPodCardDefinitions || []).map((definition) => definition.id),
)

const eventSelectionChanged = computed(() => !sameIdSelection(selectedDefinitionIds.value, currentEventDefinitionIds.value))

const eventSelectionDisabledMessage = computed(() => {
  if (!isEventScoped.value || loadingEvent.value || !event.value) return ''
  if (event.value.currentUserIsOwner !== true) {
    return 'Solo el organizador principal puede cambiar las cartas inter-nodo del evento.'
  }
  if (event.value.status !== 'DRAFT') {
    return 'Las cartas inter-nodo del evento solo se pueden cambiar mientras el evento está en borrador.'
  }
  return ''
})

const selectedDefinitionNames = computed(() =>
  definitions.value
    .filter((definition) => selectedDefinitionIds.value.includes(definition.id))
    .map((definition) => prettyCardName(definition.name)),
)

function buildDefinitionImageEntries(entries) {
  return entries
    .filter((definition) => definition?.id != null && definition?.imageUrl)
    .map((definition) => ({
      id: definition.id,
      url: definition.imageUrl,
    }))
}

function normalizeIdSelection(ids) {
  return [...new Set((ids || []).filter((id) => id != null))].sort((left, right) => left - right)
}

function sameIdSelection(left, right) {
  const normalizedLeft = normalizeIdSelection(left)
  const normalizedRight = normalizeIdSelection(right)
  return normalizedLeft.length === normalizedRight.length && normalizedLeft.every((id, index) => id === normalizedRight[index])
}

function clearEditImagePreview() {
  if (editImagePreviewUrl.value) {
    URL.revokeObjectURL(editImagePreviewUrl.value)
    editImagePreviewUrl.value = null
  }
}

function setEditImage(file) {
  editImage.value = file || null
  clearEditImagePreview()

  if (editImage.value) {
    editImagePreviewUrl.value = URL.createObjectURL(editImage.value)
  }
}

async function loadDefinitions({ forceImages = false } = {}) {
  loading.value = true
  try {
    const { data } = await getInterPodCardDefinitions()
    definitions.value = data || []
    await syncImageUrls(buildDefinitionImageEntries(definitions.value), { force: forceImages })
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al cargar cartas inter-nodo')
  } finally {
    loading.value = false
  }
}

async function loadEventContext() {
  if (!isEventScoped.value) {
    event.value = null
    selectedDefinitionIds.value = []
    return
  }

  loadingEvent.value = true
  try {
    const { data } = await getEvent(eventId.value)
    event.value = data
    selectedDefinitionIds.value = (data?.selectedInterPodCardDefinitions || []).map((definition) => definition.id)
  } catch (err) {
    event.value = null
    selectedDefinitionIds.value = []
    notif.error(err.response?.data?.message || 'Error al cargar la configuración del evento')
  } finally {
    loadingEvent.value = false
  }
}

async function loadPage() {
  cancelEdit()
  if (isEventScoped.value) {
    await Promise.all([loadDefinitions(), loadEventContext()])
    return
  }

  event.value = null
  selectedDefinitionIds.value = []
  await loadDefinitions()
}

function resetCreateForm() {
  createForm.value = {
    name: '',
    cardCategory: 'ESBIRRO',
    defaultHpMax: 8,
    defaultStatus: 'REVELADA',
  }
  createImage.value = null
}

function prettyCardName(name) {
  if (!name) return '-'
  return name
    .replaceAll('_', ' ')
    .toLowerCase()
    .replaceAll(/(^|\s)\p{L}/gu, (letter) => letter.toUpperCase())
}

function toggleEventDefinition(definitionId) {
  const selectedIds = selectedDefinitionIds.value
  if (selectedIds.includes(definitionId)) {
    selectedDefinitionIds.value = selectedIds.filter((id) => id !== definitionId)
    return
  }
  selectedDefinitionIds.value = [...selectedIds, definitionId]
}

function startEdit(definition) {
  editingId.value = definition.id
  editForm.value = {
    name: definition.name,
    cardCategory: definition.cardCategory,
    defaultHpMax: definition.defaultHpMax,
    defaultStatus: definition.defaultStatus,
  }
  setEditImage(null)
}

function cancelEdit() {
  setEditImage(null)
  editingId.value = null
}

function buildEventUpdatePayload(overrides = {}) {
  return {
    name: event.value.name,
    timeLimitMinutes: event.value.timeLimitMinutes,
    villainDefinitionId: event.value.villainDefinitionId,
    interPodCardDefinitionIds: currentEventDefinitionIds.value,
    ...overrides,
  }
}

async function handleSaveEventSelection() {
  if (!event.value) return

  savingSelection.value = true
  try {
    const { data } = await updateEvent(
      eventId.value,
      buildEventUpdatePayload({ interPodCardDefinitionIds: selectedDefinitionIds.value }),
    )
    event.value = data
    selectedDefinitionIds.value = (data?.selectedInterPodCardDefinitions || []).map((definition) => definition.id)
    notif.success('Cartas inter-nodo del evento actualizadas')
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al actualizar las cartas inter-nodo del evento')
  } finally {
    savingSelection.value = false
  }
}

async function handleCreate() {
  savingCreate.value = true
  try {
    await createInterPodCardDefinition(createForm.value, createImage.value)
    notif.success('Carta inter-nodo creada')
    resetCreateForm()
    await loadDefinitions()
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al crear carta inter-nodo')
  } finally {
    savingCreate.value = false
  }
}

async function handleUpdate(definitionId) {
  savingEdit.value = true
  try {
    const shouldRefreshImages = Boolean(editImage.value)
    await updateInterPodCardDefinition(definitionId, editForm.value, editImage.value)
    notif.success('Carta inter-nodo actualizada')
    editingId.value = null
    setEditImage(null)
    await loadDefinitions({ forceImages: shouldRefreshImages })
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al actualizar carta inter-nodo')
  } finally {
    savingEdit.value = false
  }
}

async function handleDelete(definition) {
  if (!confirm(`¿Eliminar carta inter-nodo "${prettyCardName(definition.name)}"?`)) return
  deletingId.value = definition.id
  try {
    await deleteInterPodCardDefinition(definition.id)
    notif.success('Carta inter-nodo eliminada')
    await loadDefinitions()
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al eliminar carta inter-nodo')
  } finally {
    deletingId.value = null
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

watch(eventId, () => {
  void loadPage()
}, { immediate: true })

onUnmounted(() => {
  clearEditImagePreview()
})
</script>

<template>
  <div>
    <div class="flex" style="align-items: center; justify-content: space-between; margin-bottom: 16px">
      <h1 class="page-title" style="margin-bottom: 0">{{ pageTitle }}</h1>
      <router-link class="btn btn-secondary" :to="backRoute">{{ backLabel }}</router-link>
    </div>

    <div v-if="isEventScoped" class="card">
      <div class="card-header" style="gap: 12px; align-items: flex-start">
        <div>
          <h3 style="margin-bottom: 6px">Selección para el evento</h3>
          <p class="text-muted" style="font-size: 0.9rem">
            Marca qué cartas inter-nodo estarán disponibles en <strong>{{ event?.name || 'este evento' }}</strong>.
            Esta selección es específica del evento y no modifica el catálogo global.
          </p>
        </div>
        <span v-if="event?.status" class="badge" :class="statusBadgeClass(event.status)">
          {{ event.status }}
        </span>
      </div>

      <div v-if="loadingEvent" class="text-muted">Cargando configuración del evento…</div>
      <template v-else-if="event">
        <div class="summary-grid" style="margin-bottom: 16px">
          <div class="summary-item">
            <div class="label">Evento</div>
            <div class="value" style="font-size: 1rem">{{ event.name }}</div>
          </div>
          <div class="summary-item">
            <div class="label">Seleccionadas</div>
            <div class="value">{{ selectedDefinitionIds.length }}</div>
          </div>
          <div class="summary-item">
            <div class="label">Villano actual</div>
            <div class="value" style="font-size: 1rem">{{ event.villainName }}</div>
          </div>
        </div>

        <div v-if="loading" class="text-muted">Cargando catálogo de cartas inter-nodo…</div>
        <div v-else-if="!definitions.length" class="text-muted">
          No hay cartas inter-nodo disponibles. <router-link :to="{ name: 'inter-pod-cards' }">Créelas aquí</router-link>.
        </div>
        <div v-else class="selection-grid">
          <label
            v-for="definition in definitions"
            :key="definition.id"
            class="selection-card"
            :class="{ 'is-selected': selectedDefinitionIds.includes(definition.id) }"
          >
            <input
              type="checkbox"
              :checked="selectedDefinitionIds.includes(definition.id)"
              :disabled="!canEditEventSelection || savingSelection"
              @change="toggleEventDefinition(definition.id)"
            />
            <img
              v-if="getProtectedImageUrl(definition.id)"
              :src="getProtectedImageUrl(definition.id)"
              alt="Carta"
              class="selection-card-image"
            />
            <div class="selection-card-content">
              <div class="selection-card-actions">
                <div class="selection-card-title">{{ prettyCardName(definition.name) }}</div>
                <span class="badge" :class="definition.type === 'PREDEFINED' ? 'badge-ready' : 'badge-in-progress'">
                  {{ definition.type }}
                </span>
              </div>
              <div class="selection-card-meta">
                {{ definition.cardCategory }} · HP {{ definition.defaultHpMax }}
              </div>
              <div class="selection-card-tags">
                <span class="badge" :class="definition.ownerUsername ? 'badge-in-progress' : 'badge-ready'">
                  {{ definition.ownerUsername ? `Personalizada · ${definition.ownerUsername}` : 'Sistema' }}
                </span>
                <span v-if="selectedDefinitionIds.includes(definition.id)" class="badge badge-ready">Seleccionada</span>
              </div>
            </div>
          </label>
        </div>

        <div class="form-actions">
          <button
            v-if="canEditEventSelection"
            class="btn btn-primary"
            :disabled="savingSelection || !eventSelectionChanged"
            @click="handleSaveEventSelection"
          >
            {{ savingSelection ? 'Guardando…' : 'Guardar cartas del evento' }}
          </button>
        </div>

        <p v-if="eventSelectionDisabledMessage" class="warn-box">
          {{ eventSelectionDisabledMessage }}
        </p>
        <p v-else class="text-muted" style="font-size: 0.9rem; margin-top: 8px">
          <template v-if="selectedDefinitionNames.length">
            Seleccionadas: <strong>{{ selectedDefinitionNames.join(', ') }}</strong>.
          </template>
          <template v-else>
            Si no seleccionas ninguna, el evento no ofrecerá cartas inter-nodo.
          </template>
        </p>
      </template>
      <div v-else class="text-muted">No se pudo cargar la configuración del evento.</div>
    </div>

    <div class="card">
      <h3 style="margin-bottom: 8px">
        {{ isEventScoped ? 'Crear carta y añadirla al catálogo' : 'Crear carta inter-nodo personalizada' }}
      </h3>
      <p v-if="isEventScoped" class="text-muted" style="margin-bottom: 12px; font-size: 0.9rem">
        Las cartas que crees aquí se añaden a tu catálogo personal. Después podrás marcarlas arriba para este evento.
      </p>
      <form @submit.prevent="handleCreate">
        <div class="form-group">
          <label for="new-card-name">Nombre</label>
          <input
            id="new-card-name"
            v-model="createForm.name"
            class="form-control"
            maxlength="120"
            required
          />
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="new-card-category">Categoría</label>
            <select id="new-card-category" v-model="createForm.cardCategory" class="form-control" required>
              <option v-for="category in CATEGORY_OPTIONS" :key="category" :value="category">
                {{ category }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label for="new-card-hp">HP por defecto</label>
            <input
              id="new-card-hp"
              v-model.number="createForm.defaultHpMax"
              class="form-control"
              type="number"
              min="1"
              required
            />
          </div>
          <div class="form-group">
            <label for="new-card-status">Estado por defecto</label>
            <select id="new-card-status" v-model="createForm.defaultStatus" class="form-control">
              <option v-for="status in STATUS_OPTIONS" :key="status" :value="status">
                {{ status }}
              </option>
            </select>
          </div>
        </div>

        <div class="form-group">
          <label for="new-card-image">Imagen (opcional)</label>
          <input
            id="new-card-image"
            class="form-control"
            type="file"
            accept="image/png,image/jpeg,image/webp"
            @change="createImage = $event.target.files?.[0] || null"
          />
        </div>

        <div class="form-actions">
          <button class="btn btn-primary" :disabled="savingCreate">
            {{ savingCreate ? 'Guardando…' : 'Crear carta' }}
          </button>
        </div>
      </form>
    </div>

    <div class="card mt-2">
      <h3 style="margin-bottom: 8px">
        {{ isEventScoped ? 'Catálogo disponible para este evento' : 'Catálogo de cartas inter-nodo' }}
      </h3>
      <p v-if="isEventScoped" class="text-muted" style="margin-bottom: 12px; font-size: 0.9rem">
        Gestiona aquí las cartas disponibles; la selección concreta del evento se guarda desde el bloque superior.
      </p>
      <div v-if="loading" class="text-muted">Cargando…</div>
      <div v-else-if="!definitions.length" class="text-muted">No hay cartas inter-nodo disponibles.</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Tipo</th>
              <th>Categoría</th>
              <th>HP por defecto</th>
              <th>Estado por defecto</th>
              <th>Imagen</th>
              <th>Propietario</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="definition in definitions" :key="definition.id">
              <template v-if="editingId === definition.id">
                <td>
                  <input v-model="editForm.name" class="form-control" maxlength="120" required />
                </td>
                <td>{{ definition.type }}</td>
                <td>
                  <select v-model="editForm.cardCategory" class="form-control" style="width: 140px">
                    <option v-for="category in CATEGORY_OPTIONS" :key="category" :value="category">
                      {{ category }}
                    </option>
                  </select>
                </td>
                <td>
                  <input
                    v-model.number="editForm.defaultHpMax"
                    class="form-control"
                    style="width: 100px"
                    type="number"
                    min="1"
                    required
                  />
                </td>
                <td>
                  <select v-model="editForm.defaultStatus" class="form-control" style="width: 130px">
                    <option v-for="status in STATUS_OPTIONS" :key="status" :value="status">
                      {{ status }}
                    </option>
                  </select>
                </td>
                <td>
                  <img
                    v-if="editImagePreviewUrl || getProtectedImageUrl(definition.id)"
                    :src="editImagePreviewUrl || getProtectedImageUrl(definition.id)"
                    alt=""
                    style="width: 40px; height: 40px; object-fit: cover; border-radius: 4px; margin-bottom: 4px; display: block"
                  />
                  <input
                    class="form-control"
                    type="file"
                    accept="image/png,image/jpeg,image/webp"
                    style="width: 180px"
                    @change="setEditImage($event.target.files?.[0] || null)"
                  />
                  <div class="text-muted" style="font-size: 0.75rem; margin-top: 4px">
                    {{ editImage ? `Nueva imagen: ${editImage.name}` : 'Elige otra imagen para reemplazar la actual.' }}
                  </div>
                </td>
                <td>{{ definition.ownerUsername || 'Sistema' }}</td>
                <td>
                  <div class="flex gap-1">
                    <button class="btn btn-primary btn-sm" :disabled="savingEdit" @click="handleUpdate(definition.id)">
                      Guardar
                    </button>
                    <button class="btn btn-secondary btn-sm" @click="cancelEdit">Cancelar</button>
                  </div>
                </td>
              </template>

              <template v-else>
                <td>{{ prettyCardName(definition.name) }}</td>
                <td>
                  <span class="badge" :class="definition.type === 'PREDEFINED' ? 'badge-ready' : 'badge-in-progress'">
                    {{ definition.type }}
                  </span>
                </td>
                <td>{{ definition.cardCategory }}</td>
                <td>{{ definition.defaultHpMax }}</td>
                <td>{{ definition.defaultStatus }}</td>
                <td>
                  <img
                    v-if="getProtectedImageUrl(definition.id)"
                    :src="getProtectedImageUrl(definition.id)"
                    alt="Carta"
                    style="width: 40px; height: 40px; object-fit: cover; border-radius: 4px"
                  />
                  <span v-else class="text-muted">-</span>
                </td>
                <td>{{ definition.ownerUsername || 'Sistema' }}</td>
                <td>
                  <div v-if="definition.type === 'CUSTOM'" class="flex gap-1">
                    <button class="btn btn-secondary btn-sm" @click="startEdit(definition)">Editar</button>
                    <button
                      class="btn btn-danger btn-sm"
                      :disabled="deletingId === definition.id"
                      @click="handleDelete(definition)"
                    >
                      {{ deletingId === definition.id ? 'Eliminando…' : 'Eliminar' }}
                    </button>
                  </div>
                  <span v-else class="text-muted">No editable</span>
                </td>
              </template>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
