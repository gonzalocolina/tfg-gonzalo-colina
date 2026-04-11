<script setup>
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

defineProps({
  eventName: { type: String, default: '' },
  groupLabel: { type: String, default: '' },
  connected: { type: Boolean, default: false },
})

const auth = useAuthStore()
const router = useRouter()

function handleLogout() {
  auth.logout()
  router.push({ name: 'join' })
}
</script>

<template>
  <header class="player-header">
    <div class="player-header__info">
      <div class="player-header__group">{{ groupLabel || auth.displayName || 'Mesa sin identificar' }}</div>
      <div class="player-header__event">{{ eventName }}</div>
    </div>

    <span
      class="badge"
      :class="auth.isRepresentative ? 'badge-representative' : 'badge-observer'"
    >
      {{ auth.isRepresentative ? 'Representante' : 'Observador' }}
    </span>

    <span class="conn-dot" :class="connected ? 'conn-dot--on' : 'conn-dot--off'" />

    <button
      class="btn btn-secondary"
      style="min-height: 36px; padding: 6px 12px; font-size: 0.8rem"
      @click="handleLogout"
    >
      Salir
    </button>
  </header>
</template>
