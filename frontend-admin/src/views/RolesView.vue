Estoy utilizando visual Studio Code.<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { assignCoOrganizer, getCoOrganizers } from '@/api/roles'
import { useNotificationsStore } from '@/stores/notifications'

const route = useRoute()
const notif = useNotificationsStore()
const eventId = computed(() => route.params.eventId)

const coOrganizers = ref([])
const loading = ref(true)

const form = ref({
  username: '',
  displayName: '',
  email: '',
  password: '',
  responsibilities: [],
})
const saving = ref(false)
const errorMsg = ref('')

const RESPONSIBILITIES = ['VILLAIN_HP', 'THREAT', 'TIMER']

async function load() {
  loading.value = true
  try {
    const { data } = await getCoOrganizers(eventId.value)
    coOrganizers.value = data
  } catch {
    /* ignore */
  } finally {
    loading.value = false
  }
}

async function handleAssign() {
  errorMsg.value = ''
  if (!form.value.responsibilities.length) {
    errorMsg.value = 'Selecciona al menos una responsabilidad'
    return
  }
  saving.value = true
  try {
    await assignCoOrganizer(eventId.value, form.value)
    notif.success('Co-organizador asignado')
    form.value = { username: '', displayName: '', email: '', password: '', responsibilities: [] }
    await load()
  } catch (err) {
    errorMsg.value = err.response?.data?.message || 'Error al asignar co-organizador'
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <h1 class="page-title">Roles — Co-organizadores</h1>

    <!-- Current co-organizers -->
    <div class="card">
      <div class="card-header">
        <h3>Co-organizadores actuales</h3>
        <span class="text-muted">{{ coOrganizers.length }} / 3</span>
      </div>

      <div v-if="loading" class="text-muted">Cargando…</div>

      <div v-else-if="!coOrganizers.length" class="text-muted">
        No hay co-organizadores asignados.
      </div>

      <table v-else>
        <thead>
          <tr>
            <th>Usuario</th>
            <th>Nombre</th>
            <th>Email</th>
            <th>Responsabilidades</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="co in coOrganizers" :key="co.userId">
            <td>{{ co.username }}</td>
            <td>{{ co.displayName }}</td>
            <td>{{ co.email || '—' }}</td>
            <td>
              <span
                v-for="r in co.responsibilities"
                :key="r"
                class="badge badge-ready"
                style="margin-right: 4px"
              >
                {{ r }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Add form -->
    <div v-if="coOrganizers.length < 3" class="card">
      <h3 style="margin-bottom: 12px">Añadir Co-organizador</h3>
      <form @submit.prevent="handleAssign">
        <div class="form-row">
          <div class="form-group">
            <label for="co-username">Usuario</label>
            <input id="co-username" v-model="form.username" class="form-control" required maxlength="60" />
          </div>
          <div class="form-group">
            <label for="co-displayName">Nombre a mostrar</label>
            <input id="co-displayName" v-model="form.displayName" class="form-control" required maxlength="120" />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label for="co-email">Email (opcional)</label>
            <input id="co-email" v-model="form.email" type="email" class="form-control" maxlength="120" />
          </div>
          <div class="form-group">
            <label for="co-password">Contraseña</label>
            <input
              id="co-password"
              v-model="form.password"
              type="password"
              class="form-control"
              required
              minlength="8"
              maxlength="120"
              autocomplete="new-password"
            />
          </div>
        </div>
        <fieldset class="form-group">
          <legend class="form-group-legend">Responsabilidades</legend>
          <div class="checkbox-group">
            <label v-for="r in RESPONSIBILITIES" :key="r">
              <input type="checkbox" :value="r" v-model="form.responsibilities" />
              {{ r }}
            </label>
          </div>
        </fieldset>
        <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>
        <div class="form-actions">
          <button class="btn btn-primary" :disabled="saving">
            {{ saving ? 'Guardando…' : 'Asignar co-organizador' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
