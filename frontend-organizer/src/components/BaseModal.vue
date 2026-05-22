<script setup>
import { computed, nextTick, onUnmounted, ref, useSlots, watch } from 'vue'

const modalId = Math.random().toString(36).slice(2, 10)

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  ariaLabel: { type: String, default: 'Diálogo' },
  tone: {
    type: String,
    default: 'neutral',
    validator: (value) => ['neutral', 'primary', 'warning', 'danger'].includes(value),
  },
  size: {
    type: String,
    default: 'md',
    validator: (value) => ['sm', 'md', 'lg'].includes(value),
  },
  closeOnBackdrop: { type: Boolean, default: true },
  closeOnEscape: { type: Boolean, default: true },
  showCloseButton: { type: Boolean, default: true },
})

const emit = defineEmits(['close'])

const slots = useSlots()
const dialogRef = ref(null)
const hasHeader = computed(() => Boolean(props.title) || Boolean(slots.header) || props.showCloseButton)

const titleId = `app-modal-title-${modalId}`
const descriptionId = `app-modal-description-${modalId}`

let previousBodyOverflow = ''
let previouslyFocusedElement = null

function focusDialog() {
  nextTick(() => {
    dialogRef.value?.focus()
  })
}

function handleKeydown(event) {
  if (!props.open || !props.closeOnEscape || event.key !== 'Escape') {
    return
  }

  emit('close')
}

function enableModalEffects() {
  if (globalThis.document !== undefined) {
    previousBodyOverflow = globalThis.document.body.style.overflow
    globalThis.document.body.style.overflow = 'hidden'
    const activeElement = globalThis.document.activeElement
    previouslyFocusedElement =
      globalThis.HTMLElement !== undefined && activeElement instanceof globalThis.HTMLElement
        ? activeElement
        : null
  }

  if (globalThis.window !== undefined) {
    globalThis.addEventListener('keydown', handleKeydown)
  }

  focusDialog()
}

function disableModalEffects() {
  if (globalThis.window !== undefined) {
    globalThis.removeEventListener('keydown', handleKeydown)
  }

  if (globalThis.document !== undefined) {
    globalThis.document.body.style.overflow = previousBodyOverflow
  }

  if (previouslyFocusedElement?.isConnected) {
    previouslyFocusedElement.focus()
  }

  previouslyFocusedElement = null
}

function handleBackdropClick() {
  if (props.closeOnBackdrop) {
    emit('close')
  }
}

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      enableModalEffects()
      return
    }

    disableModalEffects()
  },
)

onUnmounted(() => {
  disableModalEffects()
})
</script>

<template>
  <Teleport to="body">
    <Transition name="app-modal-fade">
      <div v-if="open" class="app-modal-backdrop" @click.self="handleBackdropClick">
        <dialog
          ref="dialogRef"
          class="app-modal"
          :class="[`app-modal--${size}`, `app-modal--tone-${tone}`]"
          open
          aria-modal="true"
          :aria-labelledby="title ? titleId : undefined"
          :aria-describedby="description ? descriptionId : undefined"
          :aria-label="title ? undefined : ariaLabel"
          tabindex="-1"
        >
          <header v-if="hasHeader" class="app-modal__header">
            <div class="app-modal__heading">
              <slot name="header">
                <h2 v-if="title" :id="titleId" class="app-modal__title">{{ title }}</h2>
              </slot>
            </div>
            <button
              v-if="showCloseButton"
              type="button"
              class="app-modal__close"
              :aria-label="`Cerrar ${title || ariaLabel}`"
              @click="emit('close')"
            >
              ×
            </button>
          </header>

          <div class="app-modal__body">
            <p v-if="description" :id="descriptionId" class="app-modal__description">{{ description }}</p>
            <slot />
          </div>

          <footer v-if="$slots.footer" class="app-modal__footer">
            <slot name="footer" />
          </footer>
        </dialog>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.app-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(2, 6, 23, 0.78);
  backdrop-filter: blur(10px);
}

.app-modal {
  --modal-bg: #171c25;
  --modal-bg-elevated: #1b2230;
  --modal-bg-soft: #232c3a;
  --modal-footer-bg: #131821;
  --modal-border: rgba(255, 255, 255, 0.08);
  --modal-border-strong: rgba(255, 255, 255, 0.12);
  --modal-text: #edf2f8;
  --modal-muted: #a7b2c4;
  --modal-highlight: rgba(255, 255, 255, 0.08);
  --modal-outline: rgba(255, 255, 255, 0.04);
  --modal-shadow: 0 28px 90px rgba(2, 6, 23, 0.52);
  position: static;
  inset: auto;
  display: block;
  width: min(100%, 560px);
  max-height: min(calc(100vh - 48px), 680px);
  overflow: hidden;
  margin: 0;
  padding: 0;
  color-scheme: dark;
  color: var(--modal-text);
  border: 1px solid var(--modal-border);
  border-radius: 22px;
  box-shadow:
    var(--modal-shadow),
    0 0 0 1px var(--modal-outline);
}

.app-modal:focus-visible {
  outline: none;
  box-shadow:
    var(--modal-shadow),
    0 0 0 1px var(--modal-outline),
    0 0 0 3px rgba(192, 57, 43, 0.24);
}

.app-modal--sm {
  max-width: 440px;
}

.app-modal--md {
  max-width: 560px;
}

.app-modal--lg {
  max-width: 840px;
}

.app-modal--tone-primary {
  border-color: rgba(192, 57, 43, 0.28);
}

.app-modal--tone-warning {
  border-color: rgba(241, 196, 15, 0.24);
}

.app-modal--tone-danger {
  border-color: rgba(231, 76, 60, 0.34);
  box-shadow:
    0 28px 90px rgba(2, 6, 23, 0.56),
    0 0 0 1px rgba(231, 76, 60, 0.16),
    0 14px 42px rgba(231, 76, 60, 0.14);
}

.app-modal::backdrop {
  display: none;
}

.app-modal__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 24px 0;
}

.app-modal__heading {
  flex: 1;
  min-width: 0;
}

.app-modal__title {
  margin: 0;
  font-size: 1.28rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--modal-text);
}

.app-modal__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 1px solid var(--modal-border-strong);
  border-radius: 999px;
  background: var(--modal-bg-soft);
  color: var(--modal-muted);
  font-size: 1.5rem;
  line-height: 1;
  cursor: pointer;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease, border-color 0.15s ease;
}

.app-modal__close:hover {
  background: var(--modal-bg-elevated);
  border-color: rgba(231, 76, 60, 0.26);
  color: var(--modal-text);
  transform: scale(1.04);
}

.app-modal__close:focus-visible {
  outline: 2px solid rgba(192, 57, 43, 0.55);
  outline-offset: 2px;
  color: var(--modal-text);
}

.app-modal__body {
  padding: 18px 24px 0;
  overflow: auto;
}

.app-modal__description {
  margin: 0;
  color: var(--modal-muted);
  line-height: 1.55;
}

.app-modal__footer {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
  padding: 20px 24px 24px;
}

@media (prefers-color-scheme: light) {
  .app-modal-backdrop {
    background: rgba(15, 23, 42, 0.26);
  }

  .app-modal {
    --modal-bg: #ffffff;
    --modal-bg-elevated: #f8fafc;
    --modal-bg-soft: #eef2f7;
    --modal-footer-bg: #f4f7fb;
    --modal-border: rgba(15, 23, 42, 0.10);
    --modal-border-strong: rgba(15, 23, 42, 0.16);
    --modal-text: #17202d;
    --modal-muted: #5f6877;
    --modal-highlight: rgba(255, 255, 255, 0.92);
    --modal-outline: rgba(15, 23, 42, 0.06);
    --modal-shadow: 0 24px 70px rgba(15, 23, 42, 0.18);
    color-scheme: light;
  }
}

.app-modal-fade-enter-active,
.app-modal-fade-leave-active {
  transition: opacity 0.18s ease;
}

.app-modal-fade-enter-from,
.app-modal-fade-leave-to {
  opacity: 0;
}
</style>
