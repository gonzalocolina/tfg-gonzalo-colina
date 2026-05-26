<script setup>
import ConfirmationModal from '@/components/ConfirmationModal.vue'
import { useDialogs } from '@/composables/useDialogs'

const { currentDialog, acceptCurrentDialog, dismissCurrentDialog } = useDialogs()
</script>

<template>
  <ConfirmationModal
    :open="Boolean(currentDialog)"
    :title="currentDialog?.title || 'Diálogo'"
    :message="currentDialog?.message || ''"
    :confirm-label="currentDialog?.confirmLabel || 'Aceptar'"
    :cancel-label="currentDialog?.kind === 'confirm' ? currentDialog?.cancelLabel || 'Cancelar' : ''"
    :tone="currentDialog?.tone || 'primary'"
    :close-on-backdrop="currentDialog?.closeOnBackdrop ?? true"
    :close-on-escape="currentDialog?.closeOnEscape ?? true"
    @confirm="acceptCurrentDialog"
    @cancel="dismissCurrentDialog"
  />
</template>
