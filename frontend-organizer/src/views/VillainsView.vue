<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationsStore } from '@/stores/notifications'
import { getEvent, updateEvent } from '@/api/events'
import { getVillains, createVillain, updateVillain, deleteVillain } from '@/api/villains'
import { useDialogs } from '@/composables/useDialogs'
import {
  eventStatusBadgeClass,
  formatCatalogItemType,
  formatEventStatus,
  formatRuleType,
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
const pageTitle = computed(() => (isEventScoped.value ? 'Villanos del Evento' : 'Gestión de Villanos'))

const villains = ref([])
const event = ref(null)
const loading = ref(true)
const loadingEvent = ref(false)
const savingCreate = ref(false)
const savingEdit = ref(false)
const savingSelection = ref(false)
const deletingId = ref(null)
const editingId = ref(null)
const selectedVillainId = ref(null)

const RULE_TYPES = ['MULTIPLIER', 'FIXED_VALUE']

const createForm = ref({
  name: '',
  hpRuleType: 'MULTIPLIER',
  hpRuleValue: 20,
  threatRuleType: 'MULTIPLIER',
  threatRuleValue: 2,
})

const editForm = ref({
  name: '',
  hpRuleType: 'MULTIPLIER',
  hpRuleValue: 20,
  threatRuleType: 'MULTIPLIER',
  threatRuleValue: 2,
})

const canEditEventSelection = computed(() => {
  if (!isEventScoped.value || !event.value) return false
  return event.value.currentUserIsOwner === true && event.value.status === 'DRAFT'
})

const eventSelectionChanged = computed(() => selectedVillainId.value !== event.value?.villainDefinitionId)

const eventSelectionDisabledMessage = computed(() => {
  if (!isEventScoped.value || loadingEvent.value || !event.value) return ''
  if (event.value.currentUserIsOwner !== true) {
    return 'Solo el organizador principal puede cambiar el villano del evento.'
  }
  if (event.value.status !== 'DRAFT') {
    return 'El villano del evento solo se puede cambiar mientras el evento está en borrador.'
  }
  return ''
})

const selectedVillain = computed(() => villains.value.find((villain) => villain.id === selectedVillainId.value) || null)

async function loadVillains() {
  loading.value = true
  try {
    const { data } = await getVillains()
    villains.value = data || []
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al cargar villanos')
  } finally {
    loading.value = false
  }
}

async function loadEventContext() {
  if (!isEventScoped.value) {
    event.value = null
    selectedVillainId.value = null
    return
  }

  loadingEvent.value = true
  try {
    const { data } = await getEvent(eventId.value)
    event.value = data
    selectedVillainId.value = data?.villainDefinitionId ?? null
  } catch (err) {
    event.value = null
    selectedVillainId.value = null
    notif.error(err.response?.data?.message || 'Error al cargar la configuración del evento')
  } finally {
    loadingEvent.value = false
  }
}

async function loadPage() {
  cancelEdit()
  if (isEventScoped.value) {
    await Promise.all([loadVillains(), loadEventContext()])
    return
  }

  event.value = null
  selectedVillainId.value = null
  await loadVillains()
}

function resetCreateForm() {
  createForm.value = {
    name: '',
    hpRuleType: 'MULTIPLIER',
    hpRuleValue: 20,
    threatRuleType: 'MULTIPLIER',
    threatRuleValue: 2,
  }
}

function startEdit(villain) {
  editingId.value = villain.id
  editForm.value = {
    name: villain.name,
    hpRuleType: villain.hpRuleType,
    hpRuleValue: villain.hpRuleValue,
    threatRuleType: villain.threatRuleType,
    threatRuleValue: villain.threatRuleValue,
  }
}

function cancelEdit() {
  editingId.value = null
}

function buildEventUpdatePayload(overrides = {}) {
  return {
    name: event.value.name,
    timeLimitMinutes: event.value.timeLimitMinutes,
    villainDefinitionId: event.value.villainDefinitionId,
    interPodCardDefinitionIds: (event.value.selectedInterPodCardDefinitions || []).map((definition) => definition.id),
    ...overrides,
  }
}

async function handleSaveEventSelection() {
  if (!event.value || selectedVillainId.value == null) return

  savingSelection.value = true
  try {
    const { data } = await updateEvent(
      eventId.value,
      buildEventUpdatePayload({ villainDefinitionId: selectedVillainId.value }),
    )
    event.value = data
    selectedVillainId.value = data?.villainDefinitionId ?? null
    notif.success('Villano del evento actualizado')
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al actualizar el villano del evento')
  } finally {
    savingSelection.value = false
  }
}

async function handleCreate() {
  savingCreate.value = true
  try {
    await createVillain(createForm.value)
    notif.success('Villano creado')
    resetCreateForm()
    await loadVillains()
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al crear villano')
  } finally {
    savingCreate.value = false
  }
}

async function handleUpdate(villainId) {
  savingEdit.value = true
  try {
    await updateVillain(villainId, editForm.value)
    notif.success('Villano actualizado')
    editingId.value = null
    await loadVillains()
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al actualizar villano')
  } finally {
    savingEdit.value = false
  }
}

async function handleDelete(villain) {
  const confirmed = await dialogs.confirm({
    title: 'Eliminar villano',
    message: `¿Quieres eliminar el villano "${villain.name}" del catálogo?`,
    confirmLabel: 'Eliminar',
    tone: 'danger',
  })

  if (!confirmed) return

  deletingId.value = villain.id
  try {
    await deleteVillain(villain.id)
    notif.success('Villano eliminado')
    await loadVillains()
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al eliminar villano')
  } finally {
    deletingId.value = null
  }
}

watch(eventId, () => {
  void loadPage()
}, { immediate: true })
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
            Elige qué villano estará activo en <strong>{{ event?.name || 'este evento' }}</strong>.
            Esta decisión afecta solo a este evento y no cambia el catálogo global.
          </p>
        </div>
        <span v-if="event?.status" class="badge" :class="eventStatusBadgeClass(event.status)">
          {{ formatEventStatus(event.status) }}
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
            <div class="label">Villano actual</div>
            <div class="value" style="font-size: 1rem">{{ event.villainName }}</div>
          </div>
          <div class="summary-item">
            <div class="label">Tipo</div>
            <div class="value" style="font-size: 1rem">{{ formatCatalogItemType(event.villainType) }}</div>
          </div>
        </div>

        <div v-if="loading" class="text-muted">Cargando catálogo de villanos…</div>
        <div v-else-if="!villains.length" class="text-muted">No hay villanos disponibles para seleccionar.</div>
        <div v-else class="selection-grid">
          <label
            v-for="v in villains"
            :key="v.id"
            class="selection-card"
            :class="{ 'is-selected': selectedVillainId === v.id }"
          >
            <input
              type="radio"
              name="event-villain-selection"
              :value="v.id"
              :checked="selectedVillainId === v.id"
              :disabled="!canEditEventSelection || savingSelection"
              @change="selectedVillainId = v.id"
            />
            <div class="selection-card-content">
              <div class="selection-card-actions">
                <div class="selection-card-title">{{ v.name }}</div>
              </div>
              <div class="selection-card-meta">
                HP {{ formatRuleType(v.hpRuleType) }}: {{ v.hpRuleValue }} · Amenaza {{ formatRuleType(v.threatRuleType) }}: {{ v.threatRuleValue }}
              </div>
              <div class="selection-card-tags">
                <span class="badge" :class="v.ownerUsername ? 'badge-in-progress' : 'badge-ready'">
                  {{ v.ownerUsername ? `Personalizado` : 'Sistema' }}
                </span>
              </div>
            </div>
          </label>
        </div>

        <div class="form-actions">
          <button
            v-if="canEditEventSelection"
            class="btn btn-primary"
            :disabled="savingSelection || !eventSelectionChanged || selectedVillainId == null"
            @click="handleSaveEventSelection"
          >
            {{ savingSelection ? 'Guardando…' : 'Guardar villano del evento' }}
          </button>
        </div>

        <p v-if="eventSelectionDisabledMessage" class="warn-box">
          {{ eventSelectionDisabledMessage }}
        </p>
        <p v-else-if="selectedVillain" class="text-muted" style="font-size: 0.9rem; margin-top: 8px">
          Seleccionado: <strong>{{ selectedVillain.name }}</strong>.
        </p>
      </template>
      <div v-else class="text-muted">No se pudo cargar la configuración del evento.</div>
    </div>

    <div class="card">
      <h3 style="margin-bottom: 8px">
        {{ isEventScoped ? 'Crear villano y añadirlo al catálogo' : 'Crear villano personalizado' }}
      </h3>
      <p v-if="isEventScoped" class="text-muted" style="margin-bottom: 12px; font-size: 0.9rem">
        Los villanos que crees aquí se añaden a tu catálogo personal. Después podrás seleccionarlos arriba para este evento.
      </p>
      <form @submit.prevent="handleCreate">
        <div class="form-group">
          <label for="new-villain-name">Nombre</label>
          <input
            id="new-villain-name"
            v-model="createForm.name"
            class="form-control"
            maxlength="120"
            required
          />
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="new-hp-type">Regla HP</label>
            <select id="new-hp-type" v-model="createForm.hpRuleType" class="form-control" required>
              <option v-for="type in RULE_TYPES" :key="type" :value="type">{{ formatRuleType(type) }}</option>
            </select>
          </div>
          <div class="form-group">
            <label for="new-hp-value">Valor HP</label>
            <input
              id="new-hp-value"
              v-model.number="createForm.hpRuleValue"
              class="form-control"
              type="number"
              min="0"
              required
            />
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="new-threat-type">Regla Amenaza</label>
            <select id="new-threat-type" v-model="createForm.threatRuleType" class="form-control" required>
              <option v-for="type in RULE_TYPES" :key="type" :value="type">{{ formatRuleType(type) }}</option>
            </select>
          </div>
          <div class="form-group">
            <label for="new-threat-value">Valor Amenaza</label>
            <input
              id="new-threat-value"
              v-model.number="createForm.threatRuleValue"
              class="form-control"
              type="number"
              min="0"
              required
            />
          </div>
        </div>

        <div class="form-actions">
          <button class="btn btn-primary" :disabled="savingCreate">
            {{ savingCreate ? 'Guardando…' : 'Crear villano' }}
          </button>
        </div>
      </form>
    </div>

    <div class="card mt-2">
      <h3 style="margin-bottom: 8px">
        {{ isEventScoped ? 'Catálogo disponible para este evento' : 'Catálogo de villanos' }}
      </h3>
      <p v-if="isEventScoped" class="text-muted" style="margin-bottom: 12px; font-size: 0.9rem">
        Gestiona aquí los villanos disponibles; la selección concreta del evento se guarda desde el bloque superior.
      </p>
      <div v-if="loading" class="text-muted">Cargando…</div>
      <div v-else-if="!villains.length" class="text-muted">No hay villanos disponibles.</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Tipo</th>
              <th>HP</th>
              <th>Amenaza</th>
              <th>Propietario</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="v in villains" :key="v.id">
              <template v-if="editingId === v.id">
                <td>
                  <input v-model="editForm.name" class="form-control" maxlength="120" required />
                </td>
                <td>{{ formatCatalogItemType(v.type) }}</td>
                <td>
                  <div class="flex gap-1">
                    <select v-model="editForm.hpRuleType" class="form-control" style="width: 130px">
                      <option v-for="type in RULE_TYPES" :key="type" :value="type">{{ formatRuleType(type) }}</option>
                    </select>
                    <input
                      v-model.number="editForm.hpRuleValue"
                      class="form-control"
                      style="width: 90px"
                      type="number"
                      min="0"
                      required
                    />
                  </div>
                </td>
                <td>
                  <div class="flex gap-1">
                    <select v-model="editForm.threatRuleType" class="form-control" style="width: 130px">
                      <option v-for="type in RULE_TYPES" :key="type" :value="type">{{ formatRuleType(type) }}</option>
                    </select>
                    <input
                      v-model.number="editForm.threatRuleValue"
                      class="form-control"
                      style="width: 90px"
                      type="number"
                      min="0"
                      required
                    />
                  </div>
                </td>
                <td>{{ v.ownerUsername || 'Sistema' }}</td>
                <td>
                  <div class="flex gap-1">
                    <button class="btn btn-primary btn-sm" :disabled="savingEdit" @click="handleUpdate(v.id)">
                      Guardar
                    </button>
                    <button class="btn btn-secondary btn-sm" @click="cancelEdit">Cancelar</button>
                  </div>
                </td>
              </template>

              <template v-else>
                <td>{{ v.name }}</td>
                <td>
                  <span class="badge" :class="v.type === 'PREDEFINED' ? 'badge-ready' : 'badge-in-progress'">
                    {{ formatCatalogItemType(v.type) }}
                  </span>
                </td>
                <td>{{ formatRuleType(v.hpRuleType) }}: {{ v.hpRuleValue }}</td>
                <td>{{ formatRuleType(v.threatRuleType) }}: {{ v.threatRuleValue }}</td>
                <td>{{ v.ownerUsername || 'Sistema' }}</td>
                <td>
                  <div v-if="v.type === 'CUSTOM'" class="flex gap-1">
                    <button class="btn btn-secondary btn-sm" @click="startEdit(v)">Editar</button>
                    <button
                      class="btn btn-danger btn-sm"
                      :disabled="deletingId === v.id"
                      @click="handleDelete(v)"
                    >
                      {{ deletingId === v.id ? 'Eliminando…' : 'Eliminar' }}
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
