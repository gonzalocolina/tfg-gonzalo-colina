<script setup>
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useGameStateStore } from '@/stores/gameState'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const gs = useGameStateStore()

const eventId = computed(() => route.params.eventId)
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
  (newId) => {
    if (newId) {
      gs.fetchGameState(newId).catch(() => {})
    }
  },
  { immediate: true },
)

const ICONS = {
  dashboard:
    '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>',
  tag:
    '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>',
  mask:
    '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2l8 4v6c0 5.55-3.84 10.74-8 10-4.16.74-8-4.45-8-10V6l8-4z"/><path d="M9 10h.01"/><path d="M15 10h.01"/><path d="M8 15c1 .8 2.4 1.2 4 1.2s3-.4 4-1.2"/></svg>',
  users:
    '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
  sword:
    '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="14.5 17.5 3 6 3 3 6 3 17.5 14.5"/><line x1="13" y1="19" x2="19" y2="13"/><line x1="16" y1="16" x2="20" y2="20"/><line x1="19" y1="21" x2="21" y2="19"/></svg>',
  clipboard:
    '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><rect x="8" y="2" width="8" height="4" rx="1" ry="1"/></svg>',
  folder:
    '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>',
}

const navItems = computed(() => {
  const items = [
    { label: 'Villanos', icon: 'mask', to: villainsRoute.value },
    { label: 'Cartas Inter-Pod', icon: 'tag', to: interPodCardsRoute.value },
  ]

  if (!eventId.value) {
    return items
  }

  items.unshift(
    { label: 'Dashboard', icon: 'dashboard', to: { name: 'dashboard', params: { eventId: eventId.value } } },
    { label: 'Pods y Mesas', icon: 'tag', to: { name: 'setup', params: { eventId: eventId.value } } },
    { label: 'Co-organizadores', icon: 'users', to: { name: 'roles', params: { eventId: eventId.value } } },
  )

  // Only show game panel if event is draft, ready, in progress, or finished
  const status = gs.eventStatus
  if (status === 'DRAFT' || status === 'READY' || status === 'IN_PROGRESS' || status === 'FINISHED') {
    items.push({
      label: 'Partida',
      icon: 'sword',
      to: { name: 'game', params: { eventId: eventId.value } },
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
      <span class="brand-text">God of Lies</span>
    </div>

    <div class="sidebar-user">
      <div class="user-name">{{ auth.displayName || auth.username }}</div>
      <div class="user-role badge" :class="auth.isOrganizer ? 'badge-ready' : 'badge-in-progress'">
        {{ auth.role }}
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
      <button class="btn btn-secondary btn-sm" style="width: 100%" @click="handleLogout">
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
