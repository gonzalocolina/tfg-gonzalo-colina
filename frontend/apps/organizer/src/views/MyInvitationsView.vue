<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import OrganizerPageHeader from '@/components/OrganizerPageHeader.vue'
import {
  acceptCoOrganizerInvitation,
  getMyCoOrganizerInvitations,
  rejectCoOrganizerInvitation,
} from '@/api/invitations'
import { useDialogs } from '@/composables/useDialogs'
import { useNotificationsStore } from '@/stores/notifications'
import { formatEventStatus, formatResponsibilities } from '@/utils/displayLabels'

const router = useRouter()
const dialogs = useDialogs()
const notif = useNotificationsStore()

const invitations = ref([])
const loading = ref(true)
const actionId = ref(null)

async function load() {
  loading.value = true
  try {
    const { data } = await getMyCoOrganizerInvitations()
    invitations.value = data || []
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al cargar las invitaciones')
  } finally {
    loading.value = false
  }
}

async function handleAccept(invitation) {
  const confirmed = await dialogs.confirm({
    title: 'Aceptar invitación',
    message: `¿Quieres aceptar la invitación al evento "${invitation.eventName}"?`,
    confirmLabel: 'Aceptar',
  })

  if (!confirmed) return

  actionId.value = invitation.id
  try {
    const { data } = await acceptCoOrganizerInvitation(invitation.id)
    notif.success('Invitación aceptada')
    await load()
    router.push({ name: 'dashboard', params: { eventId: data.eventId } })
  } catch (err) {
    notif.error(err.response?.data?.message || 'No se pudo aceptar la invitación')
  } finally {
    actionId.value = null
  }
}

async function handleReject(invitation) {
  const confirmed = await dialogs.confirm({
    title: 'Rechazar invitación',
    message: `¿Quieres rechazar la invitación al evento "${invitation.eventName}"?`,
    confirmLabel: 'Rechazar',
    tone: 'danger',
  })

  if (!confirmed) return

  actionId.value = invitation.id
  try {
    await rejectCoOrganizerInvitation(invitation.id)
    notif.success('Invitación rechazada')
    await load()
  } catch (err) {
    notif.error(err.response?.data?.message || 'No se pudo rechazar la invitación')
  } finally {
    actionId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div style="margin: 32px 24px">
    <OrganizerPageHeader>
        <div class="page-header-copy">
          <h1 class="page-title">Mis invitaciones</h1>
          
        </div>
      </OrganizerPageHeader>
      <p class="text-muted">
            Revisa las invitaciones pendientes para incorporarte como co-organizador a otros eventos.
          </p>
  </div>

  <div class="invitations-page">
    
    <div v-if="loading" class="card text-muted">Cargando invitaciones…</div>

    <div v-else-if="!invitations.length" class="card empty-state">
      <h3>No tienes invitaciones pendientes</h3>
      <p class="text-muted">Cuando otro organizador te invite a colaborar, la verás aquí.</p>
    </div>

    <div v-else class="invitation-list">
      <article v-for="invitation in invitations" :key="invitation.id" class="card invitation-card">
        <div class="invitation-card__header">
          <div>
            <h3>{{ invitation.eventName }}</h3>
            <p class="text-muted">
              Organiza {{ invitation.organizerUsername }} ·
              Estado actual: {{ formatEventStatus(invitation.eventStatus) }}
            </p>
          </div>
          <span class="badge badge-in-progress" style="height: 26px;">Pendiente</span>
        </div>

        <div class="invitation-summary-grid">
          <div>
            <span class="summary-label">Tiempo límite</span>
            <strong>{{ invitation.timeLimitMinutes }} min</strong>
          </div>
          <div>
            <span class="summary-label">Permisos delegados</span>
            <strong>{{ formatResponsibilities(invitation.responsibilities, 'Sin responsabilidades delegadas') }}</strong>
          </div>
        </div>

        <div class="invitation-card__actions">
          <button
            class="btn btn-primary"
            :disabled="actionId === invitation.id"
            @click="handleAccept(invitation)"
          >
            {{ actionId === invitation.id ? 'Procesando…' : 'Aceptar invitación' }}
          </button>
          <button
            class="btn btn-secondary"
            :disabled="actionId === invitation.id"
            @click="handleReject(invitation)"
          >
            Rechazar
          </button>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.invitations-page {
  margin: 32px 32px;
}

.page-header-copy .page-title {
  margin-bottom: 8px;
}

.invitation-list {
  display: grid;
  gap: 16px;
}

.invitation-card__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.invitation-card__header h3 {
  margin: 0 0 6px;
}

.invitation-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.summary-label {
  display: block;
  font-size: 0.78rem;
  color: var(--c-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 4px;
}

.invitation-card__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.empty-state {
  text-align: center;
}
</style>