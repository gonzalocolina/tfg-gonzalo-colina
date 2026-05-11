<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppSidebar from '@/components/AppSidebar.vue'
import NotificationToast from '@/components/NotificationToast.vue'

const route = useRoute()
const auth = useAuthStore()

const showSidebar = computed(() => {
  const noSidebar = ['login', 'register', 'my-events', 'create-event', 'invitations']
  return auth.isAuthenticated && !noSidebar.includes(route.name)
})
</script>

<template>
  <div :class="{ 'app-layout': showSidebar }">
    <AppSidebar v-if="showSidebar" />
    <main :class="{ 'app-main': showSidebar }">
      <router-view />
    </main>
    <NotificationToast />
  </div>
</template>
