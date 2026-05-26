<script setup>
import { computed } from 'vue'
import BaseModal from '@/components/BaseModal.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, required: true },
  message: { type: String, default: '' },
  confirmLabel: { type: String, default: 'Confirmar' },
  cancelLabel: { type: String, default: 'Cancelar' },
  tone: { type: String, default: 'primary' },
  closeOnBackdrop: { type: Boolean, default: true },
  closeOnEscape: { type: Boolean, default: true },
})

const emit = defineEmits(['confirm', 'cancel'])

const confirmButtonClass = computed(() => (props.tone === 'danger' ? 'btn-danger' : 'btn-primary'))
const toneSymbol = computed(() => {
  if (props.tone === 'danger' || props.tone === 'warning') {
    return '!'
  }

  return '?'
})
</script>

<template>
  <BaseModal
    :open="open"
    :title="title"
    :description="''"
    size="sm"
    :tone="tone"
    :close-on-backdrop="closeOnBackdrop"
    :close-on-escape="closeOnEscape"
    @close="emit('cancel')"
  >
    <div class="confirmation-modal__body">
      <div class="confirmation-modal__icon" :class="`confirmation-modal__icon--${tone}`" aria-hidden="true">
        {{ toneSymbol }}
      </div>
      <div class="confirmation-modal__content">
        <p v-if="message" class="confirmation-modal__message">{{ message }}</p>
      </div>
    </div>

    <template #footer>
      <button v-if="cancelLabel" type="button" class="btn btn-secondary confirmation-modal__button" @click="emit('cancel')">
        {{ cancelLabel }}
      </button>
      <button type="button" class="btn confirmation-modal__button" :class="confirmButtonClass" @click="emit('confirm')">
        {{ confirmLabel }}
      </button>
    </template>
  </BaseModal>
</template>

<style scoped>
.confirmation-modal__body {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
}

.confirmation-modal__icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 999px;
  font-size: 1.35rem;
  font-weight: 800;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.confirmation-modal__content {
  flex: 1;
  min-width: 0;
}

.confirmation-modal__icon--primary {
  background: color-mix(in srgb, var(--c-primary) 74%, black);
  color: #fff;
}

.confirmation-modal__icon--warning {
  background: color-mix(in srgb, var(--c-warning) 74%, black);
  color: #fff;
}

.confirmation-modal__icon--danger {
  background: color-mix(in srgb, var(--c-danger) 74%, black);
  color: #fff;
}

.confirmation-modal__message {
  margin: 0;
  color: var(--modal-text);
  line-height: 1.6;
  font-size: 0.98rem;
}

.confirmation-modal__button {
  min-width: 112px;
  justify-content: center;
}

.confirmation-modal__button.btn-secondary {
  background: var(--modal-bg-soft);
  color: var(--modal-text);
  border: 1px solid var(--modal-border-strong);
}

.confirmation-modal__button.btn-secondary:hover:not(:disabled) {
  background: var(--modal-bg-elevated);
}

.confirmation-modal__button.btn-danger,
.confirmation-modal__button.btn-primary {
  border: 1px solid var(--modal-border);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
}
</style>
