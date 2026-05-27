<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import OrganizerPageHeader from '@/components/OrganizerPageHeader.vue'
import { getMyEvents } from '@/api/events'
import { useNotificationsStore } from '@/stores/notifications'
import { eventStatusBadgeClass, formatEventStatus, formatResponsibilities } from '@/utils/displayLabels'

const router = useRouter()
const notif = useNotificationsStore()

const events = ref([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const { data } = await getMyEvents()
    events.value = data
  } catch {
    notif.error('Error al cargar los eventos')
  } finally {
    loading.value = false
  }
}

function goToEvent(eventId) {
  router.push({ name: 'dashboard', params: { eventId } })
}

function accessLabel(event) {
  return event.currentUserIsOwner ? 'Organizador principal' : 'Co-organizador'
}

function accessBadgeClass(event) {
  return event.currentUserIsOwner ? 'badge-ready' : 'badge-in-progress'
}

onMounted(load)
</script>

<template>
  <div class="my-events-page">
    <OrganizerPageHeader :show-home-button="false">
      <h1 class="page-title my-events-title">Mis Eventos</h1>
    </OrganizerPageHeader>

    <div v-if="loading" class="loading-text">Cargando eventos…</div>

    <div v-else-if="events.length === 0" class="empty-state">
      <div class="empty-icon">
        <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/></svg>
      </div>
      <p>No tienes eventos todavía.</p>
    </div>

    <div v-else class="events-grid">
      <div
        v-for="event in events"
        :key="event.id"
        class="event-card"
        @click="goToEvent(event.id)"
      >
        <div class="event-card-header">
          <div class="event-card-header__main">
            <h3>{{ event.name }}</h3>
            <div class="event-card-badges">
              <span class="badge" :class="accessBadgeClass(event)">
                {{ accessLabel(event) }}
              </span>
              <span class="badge" :class="eventStatusBadgeClass(event.status)">
                {{ formatEventStatus(event.status) }}
              </span>
            </div>
          </div>
        </div>
        <div class="event-card-body">
          <div class="event-stat">
            <span class="stat-label">Jugadores totales</span>
            <span class="stat-value">{{ event.configuredPlayerCount }}</span>
          </div>
          <div class="event-stat">
            <span class="stat-label">Tiempo</span>
            <span class="stat-value">{{ event.timeLimitMinutes }} min</span>
          </div>
          <div class="event-stat">
            <span class="stat-label">Código</span>
            <span class="stat-value">{{ event.eventCode }}</span>
          </div>
        </div>
        <div v-if="!event.currentUserIsOwner" class="shared-responsibilities">
          <span class="stat-label">Responsabilidades delegadas: </span>
          <span class="stat-value">{{ formatResponsibilities(event.currentUserResponsibilities, 'Sin responsabilidades delegadas') }}</span>
        </div>
        <div class="event-card-footer">
          <small class="text-muted">
            Creado: {{ new Date(event.createdAt).toLocaleDateString() }}
          </small>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.my-events-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 32px 24px;
}

.my-events-title {
  margin: 0;
}

.loading-text {
  text-align: center;
  color: var(--c-text-muted);
  padding: 60px 0;
}

.empty-state {
  text-align: center;
  padding: 80px 0;
  color: var(--c-text-muted);
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 12px;
}

.empty-state .btn {
  margin-top: 16px;
}

.events-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 20px;
}

.event-card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 10px;
  padding: 20px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.event-card:hover {
  border-color: var(--c-primary);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
}

.event-card-header {
  margin-bottom: 16px;
}

.event-card-header__main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.event-card-header h3 {
  margin: 0;
  font-size: 1.1rem;
  flex: 1;
  margin-right: 8px;
}

.event-card-badges {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.event-card-body {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.event-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-label {
  font-size: 0.75rem;
  color: var(--c-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.stat-value {
  font-size: 0.95rem;
  font-weight: 600;
}

.shared-responsibilities {
  margin-bottom: 12px;
}

.event-card-footer {
  border-top: 1px solid var(--c-border);
  padding-top: 10px;
}
</style>
