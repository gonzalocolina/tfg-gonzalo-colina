<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getEvent } from '@/api/events'
import {
  getCoOrganizerInvitations,
  getCoOrganizers,
  inviteCoOrganizer,
  revokeCoOrganizerInvitation,
} from '@/api/roles'
import { useDialogs } from '@/composables/useDialogs'
import { useNotificationsStore } from '@/stores/notifications'
import { formatResponsibility, formatResponsibilities } from '@/utils/displayLabels'

const route = useRoute()
const router = useRouter()
const dialogs = useDialogs()
const notif = useNotificationsStore()
const eventId = computed(() => route.params.eventId)

const event = ref(null)
const coOrganizers = ref([])
const pendingInvitations = ref([])
const loading = ref(true)
const saving = ref(false)
const revokingId = ref(null)
const errorMsg = ref('')

const form = ref({
  username: '',
  responsibilities: [],
})

const RESPONSIBILITIES = ['VILLAIN_HP', 'THREAT', 'TIMER']
const REVOCABLE_EVENT_STATUSES = new Set(['DRAFT', 'READY'])

const availableSlots = computed(() => Math.max(0, 3 - coOrganizers.value.length - pendingInvitations.value.length))
const canRevokeAcceptedCoOrganizers = computed(() =>
  REVOCABLE_EVENT_STATUSES.has(event.value?.status),
)
const hasAcceptedInvitationCoOrganizers = computed(() => coOrganizers.value.some((co) => co.invitationId))

function formatDate(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}

async function load() {
  loading.value = true

  try {
    const { data: eventData } = await getEvent(eventId.value)
    event.value = eventData

    if (!eventData.currentUserIsOwner) {
      notif.error('Solo el organizador principal puede gestionar co-organizadores.')
      await router.replace({ name: 'dashboard', params: { eventId: eventId.value } })
      return
    }

    const [{ data: coOrganizerData }, { data: invitationData }] = await Promise.all([
      getCoOrganizers(eventId.value),
      getCoOrganizerInvitations(eventId.value),
    ])

    coOrganizers.value = coOrganizerData || []
    pendingInvitations.value = invitationData || []
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al cargar la gestión de co-organizadores')
  } finally {
    loading.value = false
  }
}

async function handleInvite() {
  errorMsg.value = ''

  if (!form.value.username.trim()) {
    errorMsg.value = 'Introduce un nombre de usuario válido'
    return
  }

  if (!form.value.responsibilities.length) {
    errorMsg.value = 'Selecciona al menos una responsabilidad'
    return
  }

  saving.value = true

  try {
    await inviteCoOrganizer(eventId.value, {
      username: form.value.username.trim(),
      responsibilities: form.value.responsibilities,
    })
    notif.success('Invitación enviada correctamente')
    form.value = { username: '', responsibilities: [] }
    await load()
  } catch (err) {
    errorMsg.value = err.response?.data?.message || 'Error al enviar la invitación'
  } finally {
    saving.value = false
  }
}

async function handleRevokePending(invitation) {
  const confirmed = await dialogs.confirm({
    title: 'Revocar invitación',
    message: `¿Quieres revocar la invitación enviada a ${invitation.inviteeUsername}?`,
    confirmLabel: 'Revocar',
    tone: 'danger',
  })

  if (!confirmed) {
    return
  }

  revokingId.value = invitation.id

  try {
    await revokeCoOrganizerInvitation(eventId.value, invitation.id)
    notif.success('Invitación revocada')
    await load()
  } catch (err) {
    notif.error(err.response?.data?.message || 'No se pudo revocar la invitación')
  } finally {
    revokingId.value = null
  }
}

async function handleRevokeAccepted(coOrganizer) {
  if (!coOrganizer.invitationId) {
    return
  }

  if (!canRevokeAcceptedCoOrganizers.value) {
    notif.error('Solo se puede revocar un co-organizador antes de iniciar la partida.')
    return
  }

  const confirmed = await dialogs.confirm({
    title: 'Revocar acceso',
    message: `¿Quieres revocar el acceso de ${coOrganizer.username} como co-organizador?`,
    confirmLabel: 'Revocar acceso',
    tone: 'danger',
  })

  if (!confirmed) {
    return
  }

  revokingId.value = coOrganizer.invitationId

  try {
    await revokeCoOrganizerInvitation(eventId.value, coOrganizer.invitationId)
    notif.success('Co-organizador revocado')
    await load()
  } catch (err) {
    notif.error(err.response?.data?.message || 'No se pudo revocar el co-organizador')
  } finally {
    revokingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div>
    <h1 class="page-title">Roles - Co-organizadores</h1>

    <p v-if="event" class="text-muted" style="margin-bottom: 16px">
      Evento: {{ event.name }} · Plazas disponibles: {{ availableSlots }} / 3
    </p>

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
            <th>Email</th>
            <th>Responsabilidades</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="co in coOrganizers" :key="co.userId">
            <td>{{ co.username }}</td>
            <td>{{ co.email || '-' }}</td>
            <td>
              <span
                v-for="responsibility in co.responsibilities"
                :key="responsibility"
                class="badge badge-ready"
                style="margin-right: 4px"
              >
                {{ formatResponsibility(responsibility) }}
              </span>
            </td>
            <td>
              <button
                v-if="co.invitationId"
                class="btn btn-secondary btn-sm"
                :disabled="revokingId === co.invitationId || !canRevokeAcceptedCoOrganizers"
                :title="canRevokeAcceptedCoOrganizers ? '' : 'Solo disponible antes de iniciar la partida'"
                @click="handleRevokeAccepted(co)"
              >
                {{ revokingId === co.invitationId ? 'Revocando…' : 'Revocar acceso' }}
              </button>
              <span v-else class="text-muted">-</span>
            </td>
          </tr>
        </tbody>
      </table>

      <p
        v-if="!loading && !canRevokeAcceptedCoOrganizers && hasAcceptedInvitationCoOrganizers"
        class="text-muted"
        style="margin-top: 12px"
      >
        Los co-organizadores que aceptaron invitación solo se pueden revocar antes de iniciar la partida.
      </p>
    </div>

    <div class="card">
      <div class="card-header">
        <h3>Invitaciones pendientes</h3>
        <span class="text-muted">{{ pendingInvitations.length }}</span>
      </div>

      <div v-if="loading" class="text-muted">Cargando…</div>

      <div v-else-if="!pendingInvitations.length" class="text-muted">
        No hay invitaciones pendientes.
      </div>

      <table v-else>
        <thead>
          <tr>
            <th>Usuario</th>
            <th>Email</th>
            <th>Responsabilidades</th>
            <th>Creada</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="invitation in pendingInvitations" :key="invitation.id">
            <td>{{ invitation.inviteeUsername }}</td>
            <td>{{ invitation.inviteeEmail || '-' }}</td>
            <td>{{ formatResponsibilities(invitation.responsibilities) }}</td>
            <td>{{ formatDate(invitation.createdAt) }}</td>
            <td>
              <button
                class="btn btn-secondary btn-sm"
                :disabled="revokingId === invitation.id"
                @click="handleRevokePending(invitation)"
              >
                {{ revokingId === invitation.id ? 'Revocando…' : 'Revocar' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="card">
      <h3 style="margin-bottom: 12px">Nueva invitación</h3>

      <form @submit.prevent="handleInvite">
        <fieldset class="form-group">
          <div class="form-group">
            <label for="co-username">Nombre de usuario</label>
            <input
              id="co-username"
              v-model="form.username"
              class="form-control"
              required
              maxlength="60"
              placeholder="usuario registrado en el panel organizer"
            />
          </div>

          <legend class="form-group-legend">Responsabilidades</legend>
          <div class="checkbox-group">
            <label v-for="responsibility in RESPONSIBILITIES" :key="responsibility">
              <input v-model="form.responsibilities" type="checkbox" :value="responsibility" />
              {{ formatResponsibility(responsibility) }}
            </label>
          </div>
        </fieldset>

        <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>

        <p v-if="availableSlots === 0" class="text-muted">
          Ya no quedan plazas disponibles para nuevas invitaciones.
        </p>

        <div class="form-actions">
          <button class="btn btn-primary" :disabled="saving || availableSlots === 0">
            {{ saving ? 'Enviando…' : 'Enviar invitación' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
