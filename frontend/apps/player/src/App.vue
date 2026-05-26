<script setup>
import NotificationToast from '@/components/NotificationToast.vue'
</script>

<template>
  <div class="app-shell">
    <router-view />
    <NotificationToast />
  </div>
</template>
