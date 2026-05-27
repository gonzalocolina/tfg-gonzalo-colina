<script setup>
import OrganizerHeaderActions from '@/components/OrganizerHeaderActions.vue'

defineProps({
  showHomeButton: {
    type: Boolean,
    default: true,
  },
})
</script>

<template>
  <header class="organizer-page-header" data-test="organizer-page-header">
    <OrganizerHeaderActions :show-home-button="showHomeButton" class="organizer-page-header__actions" />
    <div class="organizer-page-header__content">
      <slot />
    </div>
  </header>
</template>

<style scoped>
.organizer-page-header {
  display: flex;
  flex-wrap: wrap;
  flex-direction: row-reverse;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 16px 24px;
  margin-bottom: 32px;
}

.organizer-page-header__actions {
  flex: 0 1 auto;
}

.organizer-page-header__content {
  min-width: 0;
}
</style>
