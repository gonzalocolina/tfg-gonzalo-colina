<script setup>
import { computed, nextTick, ref, onMounted, onUnmounted, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useGameStateStore } from '@/stores/gameState'
import { useNotificationsStore } from '@/stores/notifications'
import { useProtectedImageUrls } from '@/composables/useProtectedImageUrls'
import {
  createInterPodCard,
  getInterPodCards,
  reportInterPodCardDamage,
  reportInterPodCardThreat,
} from '@/api/interPod'
import { getEventInterPodCardDefinitions } from '@/api/interPodCardDefinitions'

const props = defineProps({
  podId: { type: [Number, String], default: null },
  podName: { type: String, default: '' },
  initialCardId: { type: [Number, String], default: null },
  refreshTick: { type: Number, default: 0 },
})

const emit = defineEmits(['close'])

const auth = useAuthStore()
const gs = useGameStateStore()
const notif = useNotificationsStore()

const selectedDefinitions = ref([])
const loadingDefinitions = ref(true)
const interPodCardDefinitionId = ref(null)
const creating = ref(false)
const cards = ref([])
const loadingCards = ref(true)
const amountByCard = ref({})
const mutatingCardId = ref(null)
const cardElements = ref({})
const highlightedCardId = ref(null)
const pendingFocusCardId = ref(null)
const previewCard = ref(null)
const previewScale = ref(1)
const previewOffset = ref({ x: 0, y: 0 })
const { getProtectedImageUrl, syncImageUrls } = useProtectedImageUrls('auth-player')

const MAX_PREVIEW_SCALE = 4

let highlightResetTimeout = null
let pinchStartDistance = 0
let pinchStartScale = 1
let panStart = { x: 0, y: 0 }
let previewHistoryActive = false
let ignoreNextPreviewPopState = false

const previewTransformStyle = computed(() => ({
  transform: `translate(${previewOffset.value.x}px, ${previewOffset.value.y}px) scale(${previewScale.value})`,
}))

function buildCardImageEntries(entries) {
  return entries
    .filter((card) => card?.id != null && card?.imageUrl)
    .map((card) => ({
      id: card.id,
      url: card.imageUrl,
    }))
}

const actionsDisabledReason = computed(() => {
  if (!auth.isRepresentative) {
    return 'Solo la mesa representante puede realizar acciones inter-nodo.'
  }
  if (gs.timerStatus === 'PAUSED') {
    return 'La partida está pausada. No puedes realizar acciones inter-nodo hasta que se reanude.'
  }
  if (gs.eventStatus === 'IN_PROGRESS' && gs.timerStatus === 'READY') {
    return 'La partida ya ha comenzado, pero el organizador todavía no ha iniciado el contador. No puedes realizar acciones inter-nodo todavía.'
  }
  if (!props.podId) {
    return 'Tu mesa todavía no está asignada a un pod.'
  }
  return ''
})

const actionsDisabled = computed(() => Boolean(actionsDisabledReason.value))

async function loadCards({ silent = false } = {}) {
  if (!props.podId) {
    cards.value = []
    loadingCards.value = false
    await syncImageUrls([])
    return
  }

  if (!silent) {
    loadingCards.value = true
  }

  try {
    const { data } = await getInterPodCards(auth.eventId, Number(props.podId))
    cards.value = data || []
    await syncImageUrls(buildCardImageEntries(cards.value))
    await focusPendingCard()
  } catch {
    if (!silent) {
      notif.error('Error al cargar las cartas inter-nodo del pod')
    }
  } finally {
    if (!silent) {
      loadingCards.value = false
    }
  }
}

function prettyCardName(name) {
  if (!name) return '-'
  return name
    .replaceAll('_', ' ')
    .toLowerCase()
    .replaceAll(/(^|\s)\p{L}/gu, (letter) => letter.toUpperCase())
}

function resolveInterPodCardStatus(card) {
  if (!card) return '-'

  const currentValue = card.cardCategory === 'ESBIRRO'
    ? Number(card.hpCurrent)
    : Number(card.threatCurrent)
  if (Number.isFinite(currentValue) && currentValue <= 0) {
    return 'DERROTADA'
  }

  if (card.status === 'OCULTA') {
    return 'OCULTA'
  }

  return 'REVELADA'
}

function isMinionCard(card) {
  return card?.cardCategory === 'ESBIRRO'
}

function cardMetricLabel(card) {
  return isMinionCard(card) ? 'HP' : 'Amenaza'
}

function cardCurrentValue(card) {
  return isMinionCard(card) ? card.hpCurrent : card.threatCurrent
}

function cardMaxValue(card) {
  return isMinionCard(card) ? card.hpMax : card.threatMax
}

function cardActionPlaceholder(card) {
  return isMinionCard(card) ? 'Daño' : 'Amenaza'
}

function cardActionLabel(card) {
  return isMinionCard(card) ? 'Registrar daño' : 'Quitar amenaza'
}

async function loadSelectedDefinitions() {
  loadingDefinitions.value = true
  try {
    const { data } = await getEventInterPodCardDefinitions(auth.eventId)
    selectedDefinitions.value = data || []
    if (!selectedDefinitions.value.length) {
      interPodCardDefinitionId.value = null
      return
    }

    if (!selectedDefinitions.value.some((definition) => definition.id === interPodCardDefinitionId.value)) {
      interPodCardDefinitionId.value = selectedDefinitions.value[0].id
    }
  } catch {
    selectedDefinitions.value = []
  } finally {
    loadingDefinitions.value = false
  }
}

async function handleCreate() {
  if (actionsDisabled.value) {
    notif.error(actionsDisabledReason.value)
    return
  }
  if (!interPodCardDefinitionId.value) {
    notif.error('No hay cartas inter-nodo seleccionadas para este evento.')
    return
  }

  creating.value = true
  try {
    await createInterPodCard(
      auth.eventId,
      {
        representativeCode: auth.code,
        interPodCardDefinitionId: interPodCardDefinitionId.value,
      },
    )
    notif.success('Carta inter-nodo registrada')
    await loadCards()
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al crear carta')
  } finally {
    creating.value = false
  }
}

function mapMutationError(err, fallback) {
  if (err.response?.status === 403) {
    return 'Solo puedes modificar cartas inter-nodo del mismo pod.'
  }
  return err.response?.data?.message || fallback
}

async function handleCardProgress(card) {
  if (actionsDisabled.value) {
    notif.error(actionsDisabledReason.value)
    return
  }
  const amount = Number(amountByCard.value[card.id] || 0)
  if (amount < 1) {
    notif.error(isMinionCard(card) ? 'El daño debe ser mayor que 0.' : 'La amenaza debe ser mayor que 0.')
    return
  }

  mutatingCardId.value = card.id
  try {
    if (isMinionCard(card)) {
      await reportInterPodCardDamage(auth.eventId, card.id, {
        representativeCode: auth.code,
        damage: amount,
      })
    } else {
      await reportInterPodCardThreat(auth.eventId, card.id, {
        representativeCode: auth.code,
        threat: amount,
      })
    }
    notif.success(isMinionCard(card) ? 'Daño reportado' : 'Amenaza retirada')
    amountByCard.value[card.id] = ''
    await loadCards()
  } catch (err) {
    notif.error(mapMutationError(err, isMinionCard(card) ? 'Error al reportar daño' : 'Error al retirar amenaza'))
  } finally {
    mutatingCardId.value = null
  }
}

function formatTime(iso) {
  if (!iso) return '-'
  return new Date(iso).toLocaleTimeString('es-ES', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function handleOverlayClick(e) {
  if (e.target === e.currentTarget) emit('close')
}

function handleImageLoadError(event) {
  event.target.style.display = 'none'
}

function normalizeCardId(cardId) {
  if (cardId == null || cardId === '') {
    return null
  }

  const normalized = Number(cardId)
  return Number.isFinite(normalized) ? normalized : null
}

function setCardElement(cardId, element) {
  const normalizedCardId = normalizeCardId(cardId)
  if (normalizedCardId == null) {
    return
  }

  if (element) {
    cardElements.value[normalizedCardId] = element
    return
  }

  delete cardElements.value[normalizedCardId]
}

function highlightCard(cardId) {
  highlightedCardId.value = cardId
  if (highlightResetTimeout) {
    clearTimeout(highlightResetTimeout)
  }
  highlightResetTimeout = setTimeout(() => {
    if (highlightedCardId.value === cardId) {
      highlightedCardId.value = null
    }
  }, 1800)
}

async function focusPendingCard() {
  const cardId = normalizeCardId(pendingFocusCardId.value)
  if (cardId == null) {
    return
  }

  if (!cards.value.some((card) => Number(card.id) === cardId)) {
    return
  }

  highlightCard(cardId)

  await nextTick()

  cardElements.value[cardId]?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  pendingFocusCardId.value = null
}

function resetPreviewTransform() {
  previewScale.value = 1
  previewOffset.value = { x: 0, y: 0 }
  pinchStartDistance = 0
  pinchStartScale = 1
  panStart = { x: 0, y: 0 }
}

function openImagePreview(card) {
  const imageUrl = getProtectedImageUrl(card.id)
  if (!imageUrl) {
    return
  }

  previewCard.value = {
    id: card.id,
    url: imageUrl,
    name: prettyCardName(card.cardName),
  }
  resetPreviewTransform()

  if (!previewHistoryActive && globalThis.window !== undefined) {
    globalThis.window.history.pushState({ __interPodPreview: true }, '', globalThis.window.location.href)
    previewHistoryActive = true
  }
}

function closeImagePreview({ restoreHistory = true } = {}) {
  previewCard.value = null
  resetPreviewTransform()

  if (restoreHistory && previewHistoryActive && globalThis.window !== undefined) {
    previewHistoryActive = false
    ignoreNextPreviewPopState = true
    globalThis.window.history.back()
    return
  }

  previewHistoryActive = false
}

function clampPreviewScale(scale) {
  return Math.min(MAX_PREVIEW_SCALE, Math.max(1, scale))
}

function getTouchDistance(touches) {
  const [firstTouch, secondTouch] = touches
  return Math.hypot(secondTouch.clientX - firstTouch.clientX, secondTouch.clientY - firstTouch.clientY)
}

function handlePreviewTouchStart(event) {
  if (event.touches.length === 2) {
    event.preventDefault()
    pinchStartDistance = getTouchDistance(event.touches)
    pinchStartScale = previewScale.value
    return
  }

  if (event.touches.length === 1 && previewScale.value > 1) {
    const touch = event.touches[0]
    panStart = {
      x: touch.clientX - previewOffset.value.x,
      y: touch.clientY - previewOffset.value.y,
    }
  }
}

function handlePreviewTouchMove(event) {
  if (event.touches.length === 2) {
    event.preventDefault()
    if (!pinchStartDistance) {
      pinchStartDistance = getTouchDistance(event.touches)
      pinchStartScale = previewScale.value
    }

    const nextDistance = getTouchDistance(event.touches)
    previewScale.value = clampPreviewScale((nextDistance / pinchStartDistance) * pinchStartScale)
    if (previewScale.value <= 1) {
      previewOffset.value = { x: 0, y: 0 }
    }
    return
  }

  if (event.touches.length === 1 && previewScale.value > 1) {
    event.preventDefault()
    const touch = event.touches[0]
    previewOffset.value = {
      x: touch.clientX - panStart.x,
      y: touch.clientY - panStart.y,
    }
  }
}

function handlePreviewTouchEnd(event) {
  if (event.touches.length === 1 && previewScale.value > 1) {
    const touch = event.touches[0]
    panStart = {
      x: touch.clientX - previewOffset.value.x,
      y: touch.clientY - previewOffset.value.y,
    }
    pinchStartDistance = 0
    return
  }

  if (event.touches.length === 0 && previewScale.value <= 1) {
    previewOffset.value = { x: 0, y: 0 }
  }
}

function handlePreviewWheel(event) {
  event.preventDefault()
  const delta = event.deltaY < 0 ? 0.25 : -0.25
  previewScale.value = clampPreviewScale(previewScale.value + delta)
  if (previewScale.value <= 1) {
    previewOffset.value = { x: 0, y: 0 }
  }
}

function handlePreviewPopState() {
  if (ignoreNextPreviewPopState) {
    ignoreNextPreviewPopState = false
    return
  }

  if (!previewCard.value && !previewHistoryActive) {
    return
  }

  closeImagePreview({ restoreHistory: false })
}

watch(
  () => props.podId,
  (nextPodId) => {
    if (nextPodId) {
      void loadCards()
      return
    }

    cards.value = []
    loadingCards.value = false
  },
  { immediate: true },
)

watch(
  () => props.refreshTick,
  (nextRefreshTick, previousRefreshTick) => {
    if (nextRefreshTick === previousRefreshTick || !props.podId) {
      return
    }

    void loadCards({ silent: true })
  },
)

watch(
  () => props.initialCardId,
  (nextCardId) => {
    pendingFocusCardId.value = normalizeCardId(nextCardId)
    if (pendingFocusCardId.value != null && cards.value.length) {
      void focusPendingCard()
    }
  },
  { immediate: true },
)

watch(cards, () => {
  if (pendingFocusCardId.value != null) {
    void focusPendingCard()
  }
})

onMounted(async () => {
  if (globalThis.window !== undefined) {
    globalThis.window.addEventListener('popstate', handlePreviewPopState)
  }

  await loadSelectedDefinitions()
})

onUnmounted(() => {
  if (highlightResetTimeout) {
    clearTimeout(highlightResetTimeout)
  }
  if (globalThis.window !== undefined) {
    globalThis.window.removeEventListener('popstate', handlePreviewPopState)
  }
})
</script>

<template>
  <div class="modal-overlay" @click="handleOverlayClick">
    <div class="modal-panel">
      <div class="modal-title">Cartas Inter-Nodo</div>
      <p v-if="podName" class="text-muted text-center mb-1" style="font-size: 0.85rem">
        Pod actual: {{ podName }}
      </p>

      <div v-if="actionsDisabled" class="warn-box" style="margin-bottom: 12px">
        {{ actionsDisabledReason }}
      </div>

      <div class="form-group">
        <label for="card-definition" class="form-label">Tipo de carta</label>
        <select
          id="card-definition"
          v-model.number="interPodCardDefinitionId"
          class="form-control"
          :disabled="loadingDefinitions || !selectedDefinitions.length || actionsDisabled"
        >
          <option :value="null" disabled>
            {{ loadingDefinitions ? 'Cargando cartas…' : 'Selecciona una carta' }}
          </option>
          <option v-for="definition in selectedDefinitions" :key="definition.id" :value="definition.id">
            {{ prettyCardName(definition.name) }}
          </option>
        </select>
      </div>

      <p v-if="!loadingDefinitions && !selectedDefinitions.length" class="text-muted" style="font-size: 0.85rem">
        Este evento no tiene cartas inter-nodo habilitadas.
      </p>

      <button
        class="btn btn-primary btn-block btn-lg"
        :disabled="creating || loadingDefinitions || !selectedDefinitions.length || actionsDisabled"
        @click="handleCreate"
      >
        {{ creating ? 'Enviando…' : 'Me ha aparecido esta carta' }}
      </button>

      <!-- Active cards -->
      <div v-if="!podId" class="text-muted text-center mt-2" style="font-size: 0.85rem">
        Cargando la información del pod actual…
      </div>

      <div class="mt-2" v-else-if="!loadingCards && cards.length">
        <div class="form-label">Cartas activas del pod</div>
        <div
          v-for="a in cards"
          :key="a.id"
          :ref="(element) => setCardElement(a.id, element)"
          class="interpod-card-item"
          :class="{ 'interpod-card-item--highlighted': highlightedCardId === a.id }"
          :data-testid="`interpod-detail-card-${a.id}`"
        >
          <div class="interpod-card-header">
            <button
              v-if="getProtectedImageUrl(a.id)"
              type="button"
              class="interpod-card-thumb-button"
              :aria-label="`Ampliar imagen de ${prettyCardName(a.cardName)}`"
              @click="openImagePreview(a)"
            >
              <img
                :src="getProtectedImageUrl(a.id)"
                alt="Carta inter-nodo"
                class="interpod-card-thumb"
                @error="handleImageLoadError"
              />
            </button>
            <div class="interpod-card-meta">
              <span
                class="alert-type"
                :class="a.cardCategory === 'ESBIRRO' ? 'alert-type--mangog' : 'alert-type--door'"
                style="font-size: 0.95rem"
              >
                {{ prettyCardName(a.cardName) }}
              </span>
              <span class="text-muted" style="font-size: 0.8rem">{{ a.cardCategory }} · Mesa: {{ a.sourceGroupIdentifier }}</span>
              <div style="font-size: 0.95rem; margin-top: 2px">
                <strong>{{ cardMetricLabel(a) }} {{ cardCurrentValue(a) }} / {{ cardMaxValue(a) }}</strong>
                <span class="text-muted" style="margin-left: 8px">{{ prettyCardName(resolveInterPodCardStatus(a)) }}</span>
              </div>
              <span class="text-muted" style="font-size: 0.72rem">{{ formatTime(a.createdAt) }}</span>
            </div>
          </div>

          <div class="interpod-card-actions">
            <input
              v-model.number="amountByCard[a.id]"
              class="form-control"
              type="number"
              min="1"
              :placeholder="cardActionPlaceholder(a)"
              style="width: 90px"
            />
            <button class="btn btn-secondary btn-sm" :disabled="actionsDisabled || mutatingCardId === a.id" @click="handleCardProgress(a)">
              {{ cardActionLabel(a) }}
            </button>
          </div>
        </div>
      </div>
      <div v-else-if="!loadingCards" class="text-muted text-center mt-2" style="font-size: 0.85rem">
        No hay cartas inter-nodo en tu pod.
      </div>

      <button class="btn btn-secondary btn-block mt-2" @click="emit('close')">Cerrar</button>
    </div>

    <div v-if="previewCard" class="image-preview-overlay" @click.self="closeImagePreview">
      <div class="image-preview-shell" @click.stop>
        <div class="image-preview-toolbar">
          <div>
            <strong>{{ previewCard.name }}</strong>
            <div class="image-preview-hint">Usa dos dedos para ampliar y mover la imagen.</div>
          </div>
          <button type="button" class="image-preview-close" @click="closeImagePreview">Cerrar</button>
        </div>

        <div
          class="image-preview-stage"
          @wheel="handlePreviewWheel"
          @touchstart="handlePreviewTouchStart"
          @touchmove="handlePreviewTouchMove"
          @touchend="handlePreviewTouchEnd"
          @touchcancel="handlePreviewTouchEnd"
        >
          <img
            :src="previewCard.url"
            :alt="previewCard.name"
            class="image-preview-img"
            :style="previewTransformStyle"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.interpod-card-item {
  background: var(--card-bg, #23252b);
  border: 1px solid var(--border-color, #333);
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}
.interpod-card-item--highlighted {
  border-color: rgba(212, 175, 55, 0.95);
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.28);
  transform: translateY(-1px);
}
.interpod-card-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 8px;
}
.interpod-card-thumb-button {
  flex-shrink: 0;
  background: transparent;
  border: 0;
  border-radius: 8px;
  cursor: zoom-in;
  padding: 0;
}
.interpod-card-thumb {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 8px;
}
.interpod-card-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.interpod-card-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.image-preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 1400;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(8, 10, 15, 0.92);
}
.image-preview-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: min(100%, 960px);
  height: min(100%, 100vh - 32px);
}
.image-preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #fff;
}
.image-preview-hint {
  font-size: 0.8rem;
  color: rgba(255, 255, 255, 0.8);
  margin-top: 4px;
}
.image-preview-close {
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 999px;
  padding: 10px 14px;
  background: rgba(12, 18, 28, 0.92);
  color: #f9fafb;
  font-weight: 600;
  cursor: pointer;
}
.image-preview-stage {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.04);
  touch-action: none;
}
.image-preview-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  user-select: none;
  -webkit-user-drag: none;
  transform-origin: center center;
}
</style>
