<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationsStore } from '@/stores/notifications'
import { getVillains, createVillain, updateVillain, deleteVillain } from '@/api/villains'

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

const villains = ref([])
const loading = ref(true)
const savingCreate = ref(false)
const savingEdit = ref(false)
const deletingId = ref(null)
const editingId = ref(null)

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

async function load() {
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

async function handleCreate() {
  savingCreate.value = true
  try {
    await createVillain(createForm.value)
    notif.success('Villano creado')
    resetCreateForm()
    await load()
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
    await load()
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al actualizar villano')
  } finally {
    savingEdit.value = false
  }
}

async function handleDelete(villain) {
  if (!confirm(`¿Eliminar villano "${villain.name}"?`)) return
  deletingId.value = villain.id
  try {
    await deleteVillain(villain.id)
    notif.success('Villano eliminado')
    await load()
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al eliminar villano')
  } finally {
    deletingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="flex" style="align-items: center; justify-content: space-between; margin-bottom: 16px">
      <h1 class="page-title" style="margin-bottom: 0">Gestión de Villanos</h1>
      <router-link class="btn btn-secondary" :to="backRoute">{{ backLabel }}</router-link>
    </div>

    <div class="card">
      <h3 style="margin-bottom: 12px">Crear villano personalizado</h3>
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
              <option v-for="type in RULE_TYPES" :key="type" :value="type">{{ type }}</option>
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
              <option v-for="type in RULE_TYPES" :key="type" :value="type">{{ type }}</option>
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
      <h3 style="margin-bottom: 12px">Catálogo de villanos</h3>
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
                <td>{{ v.type }}</td>
                <td>
                  <div class="flex gap-1">
                    <select v-model="editForm.hpRuleType" class="form-control" style="width: 130px">
                      <option v-for="type in RULE_TYPES" :key="type" :value="type">{{ type }}</option>
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
                      <option v-for="type in RULE_TYPES" :key="type" :value="type">{{ type }}</option>
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
                    {{ v.type }}
                  </span>
                </td>
                <td>{{ v.hpRuleType }}: {{ v.hpRuleValue }}</td>
                <td>{{ v.threatRuleType }}: {{ v.threatRuleValue }}</td>
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
