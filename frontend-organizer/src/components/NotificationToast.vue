<script setup>
import { useNotificationsStore } from '@/stores/notifications'

const notifications = useNotificationsStore()
</script>

<template>
  <div class="toast-container">
    <div
      v-for="item in notifications.items"
      :key="item.id"
      class="toast"
      :class="'toast-' + item.type"
      @click="notifications.remove(item.id)"
    >
      {{ item.message }}
    </div>
  </div>
</template>
