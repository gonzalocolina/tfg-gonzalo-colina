import { createRouter, createWebHistory } from 'vue-router'
import JoinView from '@/views/JoinView.vue'
import PlayerGameView from '@/views/PlayerGameView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/join' },
    { path: '/join', name: 'join', component: JoinView, meta: { public: true } },
    { path: '/game', name: 'game', component: PlayerGameView },
  ],
})

router.beforeEach((to) => {
  if (to.meta.public) {
    return
  }

  const raw = sessionStorage.getItem('auth-player')
  if (!raw) {
    return { name: 'join' }
  }

  try {
    const { accessToken } = JSON.parse(raw)
    if (!accessToken) {
      return { name: 'join' }
    }
  } catch {
    return { name: 'join' }
  }
})

export default router
