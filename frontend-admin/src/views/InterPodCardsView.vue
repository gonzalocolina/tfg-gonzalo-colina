<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationsStore } from '@/stores/notifications'
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

const definitions = ref([])
const loading = ref(true)
const savingCreate = ref(false)
const savingEdit = ref(false)
const deletingId = ref(null)
const editingId = ref(null)

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

function buildDefinitionImageEntries(entries) {
  return entries
    .filter((definition) => definition?.id != null && definition?.imageUrl)
    .map((definition) => ({
      id: definition.id,
      url: definition.imageUrl,
    }))
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

async function load({ forceImages = false } = {}) {
  loading.value = true
  try {
    const { data } = await getInterPodCardDefinitions()
    definitions.value = data || []
    await syncImageUrls(buildDefinitionImageEntries(definitions.value), { force: forceImages })
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al cargar cartas inter-pod')
  } finally {
    loading.value = false
  }
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
  if (!name) return '—'
  return name
    .replaceAll('_', ' ')
    .toLowerCase()
    .replaceAll(/(^|\s)\p{L}/gu, (letter) => letter.toUpperCase())
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

async function handleCreate() {
  savingCreate.value = true
  try {
    await createInterPodCardDefinition(createForm.value, createImage.value)
    notif.success('Carta inter-pod creada')
    resetCreateForm()
    await load()
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al crear carta inter-pod')
  } finally {
    savingCreate.value = false
  }
}

async function handleUpdate(definitionId) {
  savingEdit.value = true
  try {
    const shouldRefreshImages = Boolean(editImage.value)
    await updateInterPodCardDefinition(definitionId, editForm.value, editImage.value)
    notif.success('Carta inter-pod actualizada')
    editingId.value = null
    setEditImage(null)
    await load({ forceImages: shouldRefreshImages })
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al actualizar carta inter-pod')
  } finally {
    savingEdit.value = false
  }
}

async function handleDelete(definition) {
  if (!confirm(`¿Eliminar carta inter-pod "${prettyCardName(definition.name)}"?`)) return
  deletingId.value = definition.id
  try {
    await deleteInterPodCardDefinition(definition.id)
    notif.success('Carta inter-pod eliminada')
    await load()
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al eliminar carta inter-pod')
  } finally {
    deletingId.value = null
  }
}

onMounted(load)
onUnmounted(() => {
  clearEditImagePreview()
})
</script>

<template>
  <div>
    <div class="flex" style="align-items: center; justify-content: space-between; margin-bottom: 16px">
      <h1 class="page-title" style="margin-bottom: 0">Cartas Inter-Pod</h1>
      <router-link class="btn btn-secondary" :to="backRoute">{{ backLabel }}</router-link>
    </div>

    <div class="card">
      <h3 style="margin-bottom: 12px">Crear carta inter-pod personalizada</h3>
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
      <h3 style="margin-bottom: 12px">Catálogo de cartas inter-pod</h3>
      <div v-if="loading" class="text-muted">Cargando…</div>
      <div v-else-if="!definitions.length" class="text-muted">No hay cartas inter-pod disponibles.</div>
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
                  <span v-else class="text-muted">—</span>
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
