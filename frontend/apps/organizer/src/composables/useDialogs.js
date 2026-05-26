import { readonly, ref } from 'vue'

let nextDialogId = 0

const dialogQueue = []
const currentDialog = ref(null)

function normalizeDialogOptions(options, kind) {
  const normalizedOptions =
    options && typeof options === 'object' && !Array.isArray(options)
      ? options
      : { message: String(options ?? '') }

  return {
    id: ++nextDialogId,
    kind,
    title: normalizedOptions.title?.trim() || (kind === 'confirm' ? 'Confirmar acción' : 'Aviso'),
    message: normalizedOptions.message?.trim() || '',
    confirmLabel: normalizedOptions.confirmLabel?.trim() || (kind === 'confirm' ? 'Confirmar' : 'Aceptar'),
    cancelLabel: kind === 'confirm' ? normalizedOptions.cancelLabel?.trim() || 'Cancelar' : '',
    tone: ['danger', 'warning'].includes(normalizedOptions.tone) ? normalizedOptions.tone : 'primary',
    closeOnBackdrop: normalizedOptions.closeOnBackdrop ?? true,
    closeOnEscape: normalizedOptions.closeOnEscape ?? true,
  }
}

function syncCurrentDialog() {
  currentDialog.value = dialogQueue[0] || null
}

function enqueueDialog(kind, options = {}) {
  return new Promise((resolve) => {
    dialogQueue.push({
      ...normalizeDialogOptions(options, kind),
      resolve,
    })

    if (!currentDialog.value) {
      syncCurrentDialog()
    }
  })
}

function settleCurrentDialog(result) {
  if (!currentDialog.value) {
    return
  }

  const current = dialogQueue.shift()
  syncCurrentDialog()
  current?.resolve(result)
}

function confirm(options) {
  return enqueueDialog('confirm', options)
}

function alert(options) {
  return enqueueDialog('alert', options).then(() => undefined)
}

function acceptCurrentDialog() {
  settleCurrentDialog(true)
}

function dismissCurrentDialog() {
  if (!currentDialog.value) {
    return
  }

  settleCurrentDialog(currentDialog.value.kind !== 'confirm')
}

function resetDialogs() {
  dialogQueue.splice(0, dialogQueue.length)
  currentDialog.value = null
}

export function useDialogs() {
  return {
    currentDialog: readonly(currentDialog),
    confirm,
    alert,
    acceptCurrentDialog,
    dismissCurrentDialog,
    resetDialogs,
  }
}
