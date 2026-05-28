<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import OrganizerPageHeader from '@/components/OrganizerPageHeader.vue'
import { useNotificationsStore } from '@/stores/notifications'
import { getEvent, updateEvent } from '@/api/events'
import {
  getInterPodCardDefinitions,
  createInterPodCardDefinition,
  updateInterPodCardDefinition,
  deleteInterPodCardDefinition,
} from '@/api/interPodCardDefinitions'
import { useDialogs } from '@/composables/useDialogs'
import { useProtectedImageUrls } from '@/composables/useProtectedImageUrls'
import {
  eventStatusBadgeClass,
  formatCardCategory,
  formatCardStatus,
  formatCatalogItemType,
  formatEventStatus,
} from '@/utils/displayLabels'

const route = useRoute()
const dialogs = useDialogs()
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
  defaultThreatMax: 8,
  defaultStatus: 'REVELADA',
  imageUrl: '',
})
const createImage = ref(null)

const editForm = ref({
  name: '',
  cardCategory: 'ESBIRRO',
  defaultHpMax: 8,
  defaultThreatMax: 8,
  defaultStatus: 'REVELADA',
  imageUrl: '',
})
const editImage = ref(null)
const editImagePreviewUrl = ref(null)
const modalImageUrl = ref('')
const modalImageAlt = ref('')
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

function normalizeImageUrl(value) {
  return value?.trim() || ''
}

function isExternalImageUrl(url) {
  return /^https?:\/\//i.test(normalizeImageUrl(url))
}

function getDefinitionImagePreviewUrl(definition) {
  return editImagePreviewUrl.value || normalizeImageUrl(editForm.value.imageUrl) || getProtectedImageUrl(definition.id)
}

function openImageModal(imageUrl, imageAlt = 'Carta inter-nodo') {
  const normalizedUrl = normalizeImageUrl(imageUrl)
  if (!normalizedUrl) return

  modalImageUrl.value = normalizedUrl
  modalImageAlt.value = imageAlt
}

function closeImageModal() {
  modalImageUrl.value = ''
  modalImageAlt.value = ''
}

function handleModalKeydown(event) {
  if (event.key === 'Escape') {
    closeImageModal()
  }
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

function handleCreateImageChange(file) {
  createImage.value = file || null
  if (createImage.value) {
    createForm.value.imageUrl = ''
  }
}

function handleCreateImageUrlInput(value) {
  createForm.value.imageUrl = value
  if (normalizeImageUrl(value)) {
    createImage.value = null
  }
}

function handleEditImageChange(file) {
  setEditImage(file || null)
  if (editImage.value) {
    editForm.value.imageUrl = ''
  }
}

function handleEditImageUrlInput(value) {
  editForm.value.imageUrl = value
  if (normalizeImageUrl(value)) {
    setEditImage(null)
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
    defaultThreatMax: 8,
    defaultStatus: 'REVELADA',
    imageUrl: '',
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

function isMinionCategory(category) {
  return category === 'ESBIRRO'
}

function definitionMetricLabel(definition) {
  return isMinionCategory(definition.cardCategory) ? 'HP base' : 'Amenaza base'
}

function definitionMetricValue(definition) {
  return isMinionCategory(definition.cardCategory)
    ? definition.defaultHpMax
    : definition.defaultThreatMax
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
    defaultThreatMax: definition.defaultThreatMax,
    defaultStatus: definition.defaultStatus,
    imageUrl: isExternalImageUrl(definition.imageUrl) ? definition.imageUrl : '',
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
  const confirmed = await dialogs.confirm({
    title: 'Eliminar carta inter-nodo',
    message: `¿Quieres eliminar la carta inter-nodo "${prettyCardName(definition.name)}" del catálogo?`,
    confirmLabel: 'Eliminar',
    tone: 'danger',
  })

  if (!confirmed) return

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

watch(modalImageUrl, (imageUrl) => {
  if (globalThis.window === undefined || globalThis.document === undefined) return

  if (imageUrl) {
    globalThis.addEventListener('keydown', handleModalKeydown)
    globalThis.document.body.style.overflow = 'hidden'
    return
  }

  globalThis.removeEventListener('keydown', handleModalKeydown)
  globalThis.document.body.style.overflow = ''
})

watch(eventId, () => {
  void loadPage()
}, { immediate: true })

onUnmounted(() => {
  closeImageModal()
  if (globalThis.window !== undefined) {
    globalThis.removeEventListener('keydown', handleModalKeydown)
  }
  if (globalThis.document !== undefined) {
    globalThis.document.body.style.overflow = ''
  }
  clearEditImagePreview()
})
</script>

<template>
  <div :class="{ 'catalog-page': !isEventScoped }">
    <OrganizerPageHeader v-if="!isEventScoped">
      <h1 class="page-title" style="margin-bottom: 0">{{ pageTitle }}</h1>
    </OrganizerPageHeader>

    <div v-else class="flex" style="align-items: center; justify-content: space-between; margin-bottom: 16px">
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
          <p class="text-muted" style="font-size: 0.85rem; margin-top: 6px">
            El valor real de cada carta será su valor base multiplicado por el número de mesas del nodo donde aparezca.
          </p>
        </div>
        <span v-if="event?.status" class="badge" :class="eventStatusBadgeClass(event.status)">
          {{ formatEventStatus(event.status) }}
        </span>
      </div>

      <div v-if="loadingEvent" class="text-muted">Cargando configuración del evento…</div>
      <template v-else-if="event">
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
            <div class="selection-card-image-wrap">
              <img
                v-if="getProtectedImageUrl(definition.id)"
                :src="getProtectedImageUrl(definition.id)"
                alt="Carta"
                class="selection-card-image"
              />
              <div v-else class="selection-card-image-placeholder">Sin imagen</div>
              <div class="selection-card-checkbox">
                <input
                  type="checkbox"
                  :checked="selectedDefinitionIds.includes(definition.id)"
                  :disabled="!canEditEventSelection || savingSelection"
                  @change="toggleEventDefinition(definition.id)"
                />
              </div>
              <div v-if="selectedDefinitionIds.includes(definition.id)" class="selection-card-selected-overlay">
                <span class="badge badge-ready" style="font-size: 0.7rem">✓</span>
              </div>
            </div>
            <div class="selection-card-content">
              <div class="selection-card-title">{{ prettyCardName(definition.name) }}</div>
              <div class="selection-card-meta">
                {{ formatCardCategory(definition.cardCategory) }} · {{ definitionMetricLabel(definition) }} {{ definitionMetricValue(definition) }}/mesa
              </div>
              <div class="selection-card-tags">

                <span class="badge" :class="definition.ownerUsername ? 'badge-in-progress' : 'badge-ready'">
                  {{ definition.ownerUsername ? `Personalizada · ${definition.ownerUsername}` : 'Sistema' }}
                </span>
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

    <div v-if="!isEventScoped" class="card">
      <h3 style="margin-bottom: 8px">
        {{ isEventScoped ? 'Crear carta y añadirla al catálogo' : 'Crear carta inter-nodo personalizada' }}
      </h3>
      <p v-if="isEventScoped" class="text-muted" style="margin-bottom: 12px; font-size: 0.9rem">
        Las cartas que crees aquí se añaden a tu catálogo personal. Después podrás marcarlas arriba para este evento.
      </p>
      <p class="text-muted" style="margin-bottom: 12px; font-size: 0.85rem">
        El valor real de cada instancia se calcula como valor base × nº de mesas del nodo.
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
                {{ formatCardCategory(category) }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label :for="isMinionCategory(createForm.cardCategory) ? 'new-card-hp' : 'new-card-threat'">
              {{ isMinionCategory(createForm.cardCategory) ? 'HP base por mesa' : 'Amenaza base por mesa' }}
            </label>
            <input
              v-if="isMinionCategory(createForm.cardCategory)"
              id="new-card-hp"
              v-model.number="createForm.defaultHpMax"
              class="form-control"
              type="number"
              min="1"
              required
            />
            <input
              v-else
              id="new-card-threat"
              v-model.number="createForm.defaultThreatMax"
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
                {{ formatCardStatus(status) }}
              </option>
            </select>
          </div>
        </div>

        <div class="form-group">
          <label for="new-card-image-url">URL de imagen (opcional)</label>
          <input
            id="new-card-image-url"
            v-model="createForm.imageUrl"
            class="form-control"
            type="url"
            placeholder="https://ejemplo.com/carta.jpg"
            @input="handleCreateImageUrlInput($event.target.value)"
          />
          <div class="text-muted" style="font-size: 0.75rem; margin-top: 4px">
            Puedes pegar una URL pública o subir un archivo en el campo siguiente.
          </div>
        </div>

        <div class="form-group">
          <label for="new-card-image">Subir imagen (opcional)</label>
          <input
            id="new-card-image"
            class="form-control"
            type="file"
            accept="image/png,image/jpeg,image/webp"
            @change="handleCreateImageChange($event.target.files?.[0] || null)"
          />
        </div>

        <div class="form-actions">
          <button class="btn btn-primary" :disabled="savingCreate">
            {{ savingCreate ? 'Guardando…' : 'Crear carta' }}
          </button>
        </div>
      </form>
    </div>

    <div v-if="!isEventScoped" class="card mt-2">
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
              <th>Valor base / mesa</th>
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
                <td>{{ formatCatalogItemType(definition.type) }}</td>
                <td>
                  <select v-model="editForm.cardCategory" class="form-control" style="width: 140px">
                    <option v-for="category in CATEGORY_OPTIONS" :key="category" :value="category">
                      {{ formatCardCategory(category) }}
                    </option>
                  </select>
                </td>
                <td>
                  <input
                    v-if="isMinionCategory(editForm.cardCategory)"
                    v-model.number="editForm.defaultHpMax"
                    class="form-control"
                    style="width: 110px"
                    type="number"
                    min="1"
                    required
                  />
                  <input
                    v-else
                    v-model.number="editForm.defaultThreatMax"
                    class="form-control"
                    style="width: 130px"
                    type="number"
                    min="1"
                    required
                  />
                </td>
                <td>
                  <select v-model="editForm.defaultStatus" class="form-control" style="width: 130px">
                    <option v-for="status in STATUS_OPTIONS" :key="status" :value="status">
                      {{ formatCardStatus(status) }}
                    </option>
                  </select>
                </td>
                <td>
                  <button
                    v-if="getDefinitionImagePreviewUrl(definition)"
                    type="button"
                    class="image-preview-trigger image-preview-trigger--edit"
                    :aria-label="`Ampliar imagen de ${prettyCardName(editForm.name || definition.name)}`"
                    @click="openImageModal(getDefinitionImagePreviewUrl(definition), prettyCardName(editForm.name || definition.name))"
                  >
                    <img
                      :src="getDefinitionImagePreviewUrl(definition)"
                      :alt="`Vista previa de ${prettyCardName(editForm.name || definition.name)}`"
                      class="image-preview-thumbnail"
                    />
                  </button>
                  <input
                    v-model="editForm.imageUrl"
                    class="form-control"
                    type="url"
                    placeholder="https://ejemplo.com/carta.jpg"
                    style="width: 180px; margin-bottom: 6px"
                    @input="handleEditImageUrlInput($event.target.value)"
                  />
                  <input
                    class="form-control"
                    type="file"
                    accept="image/png,image/jpeg,image/webp"
                    style="width: 180px"
                    @change="handleEditImageChange($event.target.files?.[0] || null)"
                  />
                  <div class="text-muted" style="font-size: 0.75rem; margin-top: 4px">
                    {{ editImage
                      ? `Nueva imagen: ${editImage.name}`
                      : normalizeImageUrl(editForm.imageUrl)
                        ? 'Usando URL externa.'
                        : 'Elige otra imagen o pega una URL pública para reemplazar la actual.' }}
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
                    {{ formatCatalogItemType(definition.type) }}
                  </span>
                </td>
                <td>{{ formatCardCategory(definition.cardCategory) }}</td>
                <td>{{ definitionMetricLabel(definition) }} {{ definitionMetricValue(definition) }}/mesa</td>
                <td>{{ formatCardStatus(definition.defaultStatus) }}</td>
                <td>
                  <button
                    v-if="getProtectedImageUrl(definition.id)"
                    type="button"
                    class="image-preview-trigger"
                    :aria-label="`Ampliar imagen de ${prettyCardName(definition.name)}`"
                    @click="openImageModal(getProtectedImageUrl(definition.id), prettyCardName(definition.name))"
                  >
                    <img
                      :src="getProtectedImageUrl(definition.id)"
                      :alt="`Miniatura de ${prettyCardName(definition.name)}`"
                      class="image-preview-thumbnail"
                    />
                  </button>
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

    <Teleport to="body">
      <dialog
        v-if="modalImageUrl"
        class="image-modal-backdrop"
        open
        aria-modal="true"
        :aria-label="modalImageAlt ? `Vista ampliada de ${modalImageAlt}` : 'Vista ampliada'"
        @cancel.prevent="closeImageModal"
        @click.self="closeImageModal"
      >
        <button
          type="button"
          class="image-modal-close"
          aria-label="Cerrar imagen ampliada"
          @click="closeImageModal"
        >
          ×
        </button>
        <div class="image-modal-content" @click.stop>
          <img
            :src="modalImageUrl"
            alt=""
            class="image-modal-image"
          />
        </div>
      </dialog>
    </Teleport>
  </div>
</template>

<style scoped>
.catalog-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 32px 24px;
}

.image-preview-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  cursor: zoom-in;
}

.image-preview-trigger--edit {
  display: block;
  margin-bottom: 4px;
}

.image-preview-trigger:focus-visible {
  outline: 2px solid var(--c-primary);
  outline-offset: 2px;
}

.image-preview-thumbnail {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
  display: block;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.image-preview-trigger:hover .image-preview-thumbnail,
.image-preview-trigger:focus-visible .image-preview-thumbnail {
  transform: scale(1.06);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--c-primary) 45%, transparent);
}

.image-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100vw;
  height: 100vh;
  max-width: none;
  max-height: none;
  margin: 0;
  padding: 24px;
  border: 0;
  background: rgba(3, 7, 18, 0.9);
}

.image-modal-content {
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: min(96vw, 1500px);
  max-height: calc(100vh - 48px);
}

.image-modal-image {
  display: block;
  max-width: 100%;
  max-height: calc(100vh - 80px);
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.3);
  box-shadow: 0 24px 50px rgba(0, 0, 0, 0.45);
}

.image-modal-close {
  position: fixed;
  top: 16px;
  right: 16px;
  width: 42px;
  height: 42px;
  border: 0;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.78);
  color: #fff;
  font-size: 1.8rem;
  line-height: 1;
  cursor: pointer;
}

.image-modal-close:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}
</style>
