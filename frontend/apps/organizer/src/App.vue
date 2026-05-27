<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppSidebar from '@/components/AppSidebar.vue'
import DialogHost from '@/components/DialogHost.vue'
import NotificationToast from '@/components/NotificationToast.vue'

const route = useRoute()
const auth = useAuthStore()

const sidebarlessRoutes = new Set(['my-events', 'invitations', 'villains', 'inter-pod-cards', 'login', 'register', 'create-event'])

const showSidebar = computed(() => {
  return auth.isAuthenticated && !sidebarlessRoutes.has(route.name)
})
</script>

<template>
  <div :class="{ 'app-layout': showSidebar }">
    <AppSidebar v-if="showSidebar" />
    <main :class="{ 'app-main': showSidebar }">
      <router-view />
    </main>
    <DialogHost />
    <NotificationToast />
  </div>
</template>
