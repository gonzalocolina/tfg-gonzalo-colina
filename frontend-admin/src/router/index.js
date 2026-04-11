import { createRouter, createWebHistory } from 'vue-router'

import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import MyEventsView from '@/views/MyEventsView.vue'
import CreateEventView from '@/views/CreateEventView.vue'
import EventDashboardView from '@/views/EventDashboardView.vue'
import EventSetupView from '@/views/EventSetupView.vue'
import RolesView from '@/views/RolesView.vue'
import GameView from '@/views/GameView.vue'
import EventLogView from '@/views/EventLogView.vue'
import VillainsView from '@/views/VillainsView.vue'
import InterPodCardsView from '@/views/InterPodCardsView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/my-events' },
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/register', name: 'register', component: RegisterView, meta: { public: true } },
    { path: '/my-events', name: 'my-events', component: MyEventsView },
    { path: '/create-event', name: 'create-event', component: CreateEventView },
    { path: '/event/:eventId', name: 'dashboard', component: EventDashboardView },
    { path: '/event/:eventId/setup', name: 'setup', component: EventSetupView },
    { path: '/event/:eventId/villains', name: 'event-villains', component: VillainsView },
    { path: '/event/:eventId/inter-pod-cards', name: 'event-inter-pod-cards', component: InterPodCardsView },
    { path: '/villains', name: 'villains', component: VillainsView },
    { path: '/inter-pod-cards', name: 'inter-pod-cards', component: InterPodCardsView },
    { path: '/event/:eventId/roles', name: 'roles', component: RolesView },
    { path: '/event/:eventId/game', name: 'game', component: GameView },
    { path: '/event/:eventId/logs', name: 'logs', component: EventLogView },
  ],
})

router.beforeEach((to, _from, next) => {
  if (to.meta.public) {
    next()
    return
  }

  const raw = sessionStorage.getItem('auth')
  if (!raw) {
    next({ name: 'login' })
    return
  }

  try {
    const { accessToken } = JSON.parse(raw)
    if (!accessToken) {
      next({ name: 'login' })
      return
    }
  } catch {
    next({ name: 'login' })
    return
  }

  next()
})

export default router
