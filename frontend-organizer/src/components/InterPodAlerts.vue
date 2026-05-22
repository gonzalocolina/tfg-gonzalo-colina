<script setup>
import { ref, onMounted, watch } from 'vue'
import {
  reportInterPodCardDamage,
  reportInterPodCardThreat,
  updateInterPodCardStatus,
  deleteInterPodCard,
  getInterPodCards,
} from '@/api/interPod'
import { useDialogs } from '@/composables/useDialogs'
import { useProtectedImageUrls } from '@/composables/useProtectedImageUrls'
import { useNotificationsStore } from '@/stores/notifications'
import { formatCardCategory, formatCardStatus } from '@/utils/displayLabels'

const props = defineProps({
  eventId: { type: [String, Number], required: true },
  disabled: { type: Boolean, default: false },
  realtimeEvent: { type: Object, default: null },
})

const dialogs = useDialogs()
const notif = useNotificationsStore()

const cards = ref([])
const loading = ref(true)

const amountByCard = ref({})
const statusByCard = ref({})
const mutatingCardId = ref(null)

const CARD_STATUSES = ['OCULTA', 'REVELADA', 'DERROTADA']
const INTER_POD_REALTIME_OPERATIONS = new Set([
  'INTER_POD_CARD_CREATED',
  'INTER_POD_CARD_DAMAGED',
  'INTER_POD_CARD_THREAT_UPDATED',
  'INTER_POD_CARD_STATUS_UPDATED',
  'INTER_POD_CARD_DELETED',
])
const { getProtectedImageUrl, syncImageUrls } = useProtectedImageUrls('auth')

function buildCardImageEntries(entries) {
  return entries
    .filter((card) => card?.id != null && card?.imageUrl)
    .map((card) => ({
      id: card.id,
      url: card.imageUrl,
    }))
}

async function load({ silent = false } = {}) {
  if (!silent) {
    loading.value = true
  }

  try {
    const { data } = await getInterPodCards(props.eventId)
    cards.value = data || []
    const nextStatus = {}
    for (const card of cards.value) {
      nextStatus[card.id] = card.status
    }
    statusByCard.value = nextStatus
    await syncImageUrls(buildCardImageEntries(cards.value))
  } catch {
    /* ignore */
  } finally {
    if (!silent) {
      loading.value = false
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

function actionPlaceholder(card) {
  return isMinionCard(card) ? 'Daño' : 'Amenaza'
}

function actionLabel(card) {
  return isMinionCard(card) ? 'Daño' : 'Quitar amenaza'
}

function successMessage(card) {
  return isMinionCard(card)
    ? 'Daño aplicado a la carta inter-nodo'
    : 'Amenaza retirada del plan inter-nodo'
}

async function handleApplyProgress(card) {
  const amount = Number(amountByCard.value[card.id] || 0)
  if (amount < 1) {
    notif.error(isMinionCard(card) ? 'El daño debe ser mayor que 0.' : 'La amenaza debe ser mayor que 0.')
    return
  }

  mutatingCardId.value = card.id
  try {
    if (isMinionCard(card)) {
      await reportInterPodCardDamage(
        props.eventId,
        card.id,
        { damage: amount },
      )
    } else {
      await reportInterPodCardThreat(
        props.eventId,
        card.id,
        { threat: amount },
      )
    }
    notif.success(successMessage(card))
    amountByCard.value[card.id] = ''
    await load()
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error')
  } finally {
    mutatingCardId.value = null
  }
}

async function handleUpdateStatus(card) {
  const status = statusByCard.value[card.id]
  if (!status) {
    notif.error('Selecciona un estado válido.')
    return
  }

  mutatingCardId.value = card.id
  try {
    await updateInterPodCardStatus(
      props.eventId,
      card.id,
      { status },
    )
    notif.success('Estado de carta actualizado')
    await load()
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error')
  } finally {
    mutatingCardId.value = null
  }
}

async function handleDeleteCard(card) {
  const confirmed = await dialogs.confirm({
    title: 'Eliminar carta inter-nodo',
    message: `¿Quieres eliminar la carta ${card.cardName} de la mesa ${card.sourceGroupIdentifier}?`,
    confirmLabel: 'Eliminar',
    tone: 'danger',
  })

  if (!confirmed) return

  mutatingCardId.value = card.id
  try {
    await deleteInterPodCard(props.eventId, card.id)
    notif.success('Carta inter-nodo eliminada')
    await load()
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al eliminar carta')
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

function handleImageLoadError(event) {
  event.target.style.display = 'none'
}

onMounted(() => {
  load()
})

watch(
  () => props.realtimeEvent?.emittedAt,
  (nextEmittedAt) => {
    if (!nextEmittedAt) {
      return
    }

    if (INTER_POD_REALTIME_OPERATIONS.has(props.realtimeEvent?.operation)) {
      void load({ silent: true })
    }
  },
)
</script>

<template>
  <div class="card">
    <div class="card-header">
      <h3>Cartas Inter-Nodo</h3>
      <button v-if="!disabled" type="button" class="btn btn-secondary btn-sm" @click="load">
        Actualizar
      </button>
    </div>

    <div v-if="loading" class="text-muted">Cargando…</div>
    <div v-else-if="!cards.length" class="text-muted">No hay cartas inter-nodo.</div>
    <div v-else class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Hora</th>
            <th>Pod</th>
            <th>Mesa</th>
            <th>Carta</th>
            <th>Categoría</th>
            <th>HP</th>
            <th>Estado</th>
            <th>Imagen</th>
            <th v-if="!disabled">Acciones</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="card in cards" :key="card.id">
            <td>{{ formatTime(card.createdAt) }}</td>
            <td>{{ card.podName }}</td>
            <td>{{ card.sourceGroupIdentifier }}</td>
            <td>{{ prettyCardName(card.cardName) }}</td>
            <td>
              <span class="badge" :class="card.cardCategory === 'ESBIRRO' ? 'badge-danger' : 'badge-ready'">
                {{ formatCardCategory(card.cardCategory) }}
              </span>
            </td>
            <td>{{ cardMetricLabel(card) }} {{ cardCurrentValue(card) }} / {{ cardMaxValue(card) }}</td>
            <td>{{ formatCardStatus(card.status) }}</td>
            <td>
              <a
                v-if="getProtectedImageUrl(card.id)"
                :href="getProtectedImageUrl(card.id)"
                target="_blank"
                rel="noopener noreferrer"
              >
                <img
                  :src="getProtectedImageUrl(card.id)"
                  alt="Carta inter-nodo"
                  style="width: 48px; height: 48px; object-fit: cover; border-radius: 6px; border: 1px solid var(--c-border)"
                  @error="handleImageLoadError"
                />
              </a>
              <span v-else class="text-muted">-</span>
            </td>
            <td v-if="!disabled">
              <div class="flex gap-1" style="align-items: center; flex-wrap: wrap">
                <input
                  v-model.number="amountByCard[card.id]"
                  class="form-control"
                  type="number"
                  min="1"
                  :placeholder="actionPlaceholder(card)"
                  style="width: 90px"
                />
                <button
                  type="button"
                  class="btn btn-secondary btn-sm"
                  :disabled="mutatingCardId === card.id"
                  @click="handleApplyProgress(card)"
                >
                  {{ actionLabel(card) }}
                </button>

                <select v-model="statusByCard[card.id]" class="form-control" style="width: 130px">
                  <option v-for="s in CARD_STATUSES" :key="s" :value="s">{{ formatCardStatus(s) }}</option>
                </select>
                <button
                  type="button"
                  class="btn btn-secondary btn-sm"
                  :disabled="mutatingCardId === card.id"
                  @click="handleUpdateStatus(card)"
                >
                  Estado
                </button>

                <button
                  type="button"
                  class="btn btn-danger btn-sm"
                  :disabled="mutatingCardId === card.id"
                  @click="handleDeleteCard(card)"
                >
                  Eliminar
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
