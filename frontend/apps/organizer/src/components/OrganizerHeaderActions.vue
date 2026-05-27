<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useGameStateStore } from '@/stores/gameState'

defineProps({
  showHomeButton: {
    type: Boolean,
    default: true,
  },
})

const router = useRouter()
const auth = useAuthStore()
const gameState = useGameStateStore()

function logout() {
  auth.logout()
  gameState.$reset()
  router.push({ name: 'login' })
}
</script>

<template>
  <nav class="header-actions" aria-label="Accesos rápidos del organizador" data-test="organizer-header-actions">
    <router-link
      v-if="showHomeButton"
      :to="{ name: 'my-events' }"
      class="btn btn-secondary"
      data-test="organizer-home-link"
    >
      Mis eventos
    </router-link>
    <router-link :to="{ name: 'invitations' }" class="btn btn-secondary">Mis invitaciones</router-link>
    <router-link :to="{ name: 'villains' }" class="btn btn-secondary">Gestionar villanos</router-link>
    <router-link :to="{ name: 'inter-pod-cards' }" class="btn btn-secondary">Gestionar cartas inter-nodo</router-link>
    <router-link :to="{ name: 'create-event' }" class="btn btn-primary">+ Crear nuevo evento</router-link>
    <button class="btn btn-logout" data-test="organizer-logout" @click="logout">Cerrar sesión</button>
  </nav>
</template>

<style scoped>
.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  width: fit-content;
  max-width: 100%;
  margin-left: auto;
}
</style>
