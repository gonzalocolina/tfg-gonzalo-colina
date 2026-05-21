<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getEvent } from '@/api/events'
import { useAuthStore } from '@/stores/auth'
import { useGameStateStore } from '@/stores/gameState'
import { formatUserRole } from '@/utils/displayLabels'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const gs = useGameStateStore()
const currentEvent = ref(null)

const eventId = computed(() => route.params.eventId)
const showInvitationsLink = computed(() => route.name === 'my-events')
const currentEventIsOwner = computed(() => currentEvent.value?.currentUserIsOwner === true)
const villainsRoute = computed(() => {
  if (eventId.value) {
    return {
      name: 'event-villains',
      params: { eventId: eventId.value },
    }
  }

  return { name: 'villains' }
})

const interPodCardsRoute = computed(() => {
  if (eventId.value) {
    return {
      name: 'event-inter-pod-cards',
      params: { eventId: eventId.value },
    }
  }

  return { name: 'inter-pod-cards' }
})

// Cargar el estado de la partida cada vez que cambia el evento activo
// para que el sidebar pueda mostrar el enlace "Partida" correctamente
watch(
  eventId,
  async (newId) => {
    if (newId) {
      gs.fetchGameState(newId).catch(() => {})
      try {
        const { data } = await getEvent(newId)
        currentEvent.value = data
      } catch {
        currentEvent.value = null
      }
      return
    }

    currentEvent.value = null
  },
  { immediate: true },
)

const ICONS = {
  dashboard:
    '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>',
  tag:
    '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>',
  mask:
    '<svg fill="currentColor" height="20" width="18" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 460 460" xml:space="preserve" stroke="currentColor" stroke-width="10"><g stroke-width="0"/><g stroke-linecap="round" stroke-linejoin="round"/><path d="M395.629 79.261c-6.186-38.147-24.26-64.763-50.893-74.944-8.875-3.392-18.407-4.796-28.35-4.172a10 10 0 0 0-6.498 16.997c13.285 13.491 21.521 36.156 23.19 63.822 1.787 29.622-3.799 62.36-16.152 94.676a268 268 0 0 1-10.927 24.758c-21.646-16.91-48.183-26.103-76-26.103s-54.354 9.193-76 26.103a269 269 0 0 1-10.927-24.759c-12.354-32.315-17.939-65.054-16.152-94.676 1.669-27.666 9.904-50.331 23.19-63.822A10 10 0 0 0 143.612.144c-9.94-.625-19.475.78-28.35 4.172-26.631 10.182-44.705 36.798-50.891 74.945-5.87 36.195-.358 79.001 15.518 120.532 8.609 22.521 20.451 44.672 32.262 60.563-3.868 12.12-5.825 24.746-5.825 37.612v122.11a10 10 0 0 0 6.027 9.177l69.108 29.922a10 10 0 0 0 9.462-.818 10 10 0 0 0 4.511-8.359v-70.564c0-5.523-4.477-10-10-10H160.65v-59.367l65.453 27.691.018.007c.043.018.089.031.132.048l.008.003c.133.054.268.099.402.147.196.069.391.136.59.193q.104.032.208.061c.112.03.225.052.338.078q.324.072.651.121c.089.014.175.037.264.049l.196.02q.432.044.865.053c.072.002.143.013.215.013l.009-.001.009.001c.071 0 .142-.011.212-.013q.435-.007.869-.053l.193-.02c.088-.011.173-.034.26-.048.221-.035.441-.073.659-.122.112-.025.224-.048.335-.078l.203-.06q.302-.088.6-.196c.132-.047.263-.091.394-.144l.026-.009c.038-.015.077-.026.114-.042l.021-.008 65.453-27.691v59.367h-24.785c-5.522 0-10 4.477-10 10V450c0 5.523 4.478 10 10 10h8.751a10 10 0 0 0 4.441-1.041l60.358-29.922a10 10 0 0 0 5.559-8.959v-122.11c0-12.865-1.958-25.492-5.826-37.612 11.812-15.89 23.653-38.042 32.263-60.562 15.878-41.532 21.39-84.338 15.521-120.533zM154.547 285.77a10 10 0 0 0-13.897 9.209v84.456c0 5.523 4.477 10 10 10h24.785v45.337l-49.108-21.262V297.968c0-9.887 1.4-19.593 4.12-28.959l57.243 2.446 19.92 36.764zm179.127 128.104-49.109 24.345v-48.784h24.785c5.522 0 10-4.477 10-10V294.98a10 10 0 0 0-13.897-9.209l-53.064 22.449 19.92-36.764 57.244-2.446a103.6 103.6 0 0 1 4.12 28.959zm27.755-221.222c-7.961 20.825-19.231 41.911-29.979 56.258l-65.658 2.806a10 10 0 0 0-8.365 5.227L230 307.559l-27.426-50.616a10 10 0 0 0-8.366-5.227l-65.658-2.806c-10.749-14.347-22.018-35.433-29.979-56.258-14.637-38.288-19.771-77.421-14.458-110.19 4.894-30.177 18.037-51.055 37.075-58.978-8.061 15.353-12.91 34.391-14.23 56.275-1.957 32.443 4.071 68.067 17.434 103.021 5.058 13.232 11.013 25.921 17.737 37.874a10 10 0 0 0 .511.894 266 266 0 0 0 5.358 9.057c2.915 4.691 9.082 6.13 13.771 3.215 4.691-2.915 6.13-9.081 3.215-13.772-.389-.626-.764-1.268-1.148-1.9 18.574-15.434 41.784-23.854 66.165-23.854s47.59 8.42 66.165 23.854c-.384.631-.759 1.274-1.148 1.9-2.915 4.691-1.476 10.856 3.215 13.772s10.857 1.476 13.771-3.215a265 265 0 0 0 5.361-9.062q.27-.422.493-.864c6.73-11.961 12.69-24.657 17.752-37.899 13.362-34.955 19.392-70.579 17.435-103.022-1.32-21.884-6.17-40.922-14.23-56.275 19.037 7.924 32.181 28.801 37.074 58.978 5.311 32.77.176 71.903-14.46 110.191z"/><path d="M259.484 217.524A85.2 85.2 0 0 0 230 212.295c-10.128 0-20.051 1.76-29.494 5.233-5.184 1.906-7.84 7.653-5.935 12.837a10.005 10.005 0 0 0 9.387 6.552 10 10 0 0 0 3.45-.617A65.3 65.3 0 0 1 230 232.296c7.762 0 15.36 1.346 22.584 4.001 5.188 1.906 10.932-.753 12.836-5.936 1.905-5.185-.752-10.932-5.936-12.837z"/></svg>',
  users:
    '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
  sword:
    '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="14.5 17.5 3 6 3 3 6 3 17.5 14.5"/><line x1="13" y1="19" x2="19" y2="13"/><line x1="16" y1="16" x2="20" y2="20"/><line x1="19" y1="21" x2="21" y2="19"/></svg>',
  clipboard:
    '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><rect x="8" y="2" width="8" height="4" rx="1" ry="1"/></svg>',
  chart:
    '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v18h18"/><path d="m19 9-5 5-4-4-3 3"/></svg>',
  folder:
    '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>',
}

const navItems = computed(() => {
  const items = [
    { label: 'Villanos', icon: 'mask', to: villainsRoute.value },
    { label: 'Cartas Inter-Nodo', icon: 'tag', to: interPodCardsRoute.value },
  ]

  if (showInvitationsLink.value) {
    items.unshift({ label: 'Mis invitaciones', icon: 'users', to: { name: 'invitations' } })
  }

  if (!eventId.value) {
    return items
  }

  items.unshift(
    { label: 'Dashboard', icon: 'dashboard', to: { name: 'dashboard', params: { eventId: eventId.value } } },
  )

  if (currentEventIsOwner.value) {
    items.splice(1, 0,
      { label: 'Nodos y Mesas', icon: 'tag', to: { name: 'setup', params: { eventId: eventId.value } } },
      { label: 'Co-organizadores', icon: 'users', to: { name: 'roles', params: { eventId: eventId.value } } },
    )
  }

  // Only show game panel if event is draft, ready, in progress, or finished
  const status = gs.eventStatus
  if (status === 'DRAFT' || status === 'READY' || status === 'IN_PROGRESS' || status === 'FINISHED') {
    items.push({
      label: 'Partida',
      icon: 'sword',
      to: { name: 'game', params: { eventId: eventId.value } },
    })
  }

  if (status === 'FINISHED') {
    items.push({
      label: 'Estadísticas',
      icon: 'chart',
      to: { name: 'statistics', params: { eventId: eventId.value } },
    })
  }

  items.push({
    label: 'Registro (Log)',
    icon: 'clipboard',
    to: { name: 'logs', params: { eventId: eventId.value } },
  })

  return items
})

function handleLogout() {
  auth.logout()
  gs.$reset()
  router.push('/login')
}
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-brand">
      <span class="brand-icon">
        <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
      </span>
      <span class="brand-text">Marvel Champions</span>
    </div>

    <div class="sidebar-user">
      <div class="user-name">{{ auth.displayName || auth.username }}</div>
      <div class="user-role badge" :class="auth.isOrganizer ? 'badge-ready' : 'badge-in-progress'">
        {{ formatUserRole(auth.role) }}
      </div>
    </div>

    <nav class="sidebar-nav">
      <router-link
        :to="{ name: 'my-events' }"
        class="nav-item"
        active-class="nav-item--active"
      >
        <span class="nav-icon" v-html="ICONS.folder"></span>
        <span>Mis Eventos</span>
      </router-link>

      <hr class="nav-divider" />

      <router-link
        v-for="item in navItems"
        :key="item.label"
        :to="item.to"
        class="nav-item"
        active-class="nav-item--active"
      >
        <span class="nav-icon" v-html="ICONS[item.icon]"></span>
        <span>{{ item.label }}</span>
      </router-link>
    </nav>

    <div class="sidebar-footer">
      <button class="btn btn-secondary btn-sm" style="width: 100%;  justify-content: center;" @click="handleLogout">
        Cerrar sesión
      </button>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  width: var(--sidebar-w);
  height: 100vh;
  background: var(--c-surface);
  border-right: 1px solid var(--c-border);
  display: flex;
  flex-direction: column;
  z-index: 100;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 18px 16px;
  border-bottom: 1px solid var(--c-border);
}
.brand-icon {
  display: flex;
  align-items: center;
  color: var(--c-primary);
}
.brand-text {
  font-weight: 700;
  font-size: 1.1rem;
}

.sidebar-user {
  padding: 14px 18px;
  border-bottom: 1px solid var(--c-border);
}
.user-name {
  font-weight: 600;
  font-size: 0.92rem;
  margin-bottom: 4px;
}

.sidebar-nav {
  flex: 1;
  padding: 12px 0;
  overflow-y: auto;
}

.nav-divider {
  border: none;
  border-top: 1px solid var(--c-border, #333);
  margin: 4px 18px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  font-size: 0.9rem;
  color: var(--c-text-muted);
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
}
.nav-item:hover {
  background: var(--c-surface-alt);
  color: var(--c-text);
}
.nav-item--active {
  background: var(--c-surface-alt);
  color: var(--c-primary);
  border-right: 3px solid var(--c-primary);
}
.nav-icon {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sidebar-footer {
  padding: 14px 18px;
  border-top: 1px solid var(--c-border);
}
</style>
