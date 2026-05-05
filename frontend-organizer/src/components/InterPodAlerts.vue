<script setup>
import { ref, onMounted } from 'vue'
import {
  reportInterPodCardDamage,
  updateInterPodCardStatus,
  deleteInterPodCard,
  getInterPodCards,
} from '@/api/interPod'
import { useProtectedImageUrls } from '@/composables/useProtectedImageUrls'
import { useNotificationsStore } from '@/stores/notifications'

const props = defineProps({
  eventId: { type: [String, Number], required: true },
  disabled: { type: Boolean, default: false },
})

const notif = useNotificationsStore()

const cards = ref([])
const loading = ref(true)

const damageByCard = ref({})
const statusByCard = ref({})
const mutatingCardId = ref(null)

const CARD_STATUSES = ['OCULTA', 'REVELADA', 'DERROTADA']
const { getProtectedImageUrl, syncImageUrls } = useProtectedImageUrls('auth')

function buildCardImageEntries(entries) {
  return entries
    .filter((card) => card?.id != null && card?.imageUrl)
    .map((card) => ({
      id: card.id,
      url: card.imageUrl,
    }))
}

async function load() {
  loading.value = true
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
    loading.value = false
  }
}

function prettyCardName(name) {
  if (!name) return '-'
  return name
    .replaceAll('_', ' ')
    .toLowerCase()
    .replaceAll(/(^|\s)\p{L}/gu, (letter) => letter.toUpperCase())
}

async function handleReportDamage(card) {
  const damage = Number(damageByCard.value[card.id] || 0)
  if (damage < 1) {
    notif.error('El daño debe ser mayor que 0.')
    return
  }

  mutatingCardId.value = card.id
  try {
    await reportInterPodCardDamage(
      props.eventId,
      card.id,
      { damage },
    )
    notif.success('Daño aplicado a la carta inter-pod')
    damageByCard.value[card.id] = ''
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
  if (!confirm(`¿Eliminar carta ${card.cardName} de la mesa ${card.sourceGroupIdentifier}?`)) return
  mutatingCardId.value = card.id
  try {
    await deleteInterPodCard(props.eventId, card.id)
    notif.success('Carta inter-pod eliminada')
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
</script>

<template>
  <div class="card">
    <div class="card-header">
      <h3>Cartas Inter-Pod</h3>
      <button v-if="!disabled" type="button" class="btn btn-secondary btn-sm" @click="load">
        Actualizar
      </button>
    </div>

    <div v-if="loading" class="text-muted">Cargando…</div>
    <div v-else-if="!cards.length" class="text-muted">No hay cartas inter-pod.</div>
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
                {{ card.cardCategory }}
              </span>
            </td>
            <td>{{ card.hpCurrent }} / {{ card.hpMax }}</td>
            <td>{{ card.status }}</td>
            <td>
              <a
                v-if="getProtectedImageUrl(card.id)"
                :href="getProtectedImageUrl(card.id)"
                target="_blank"
                rel="noopener noreferrer"
              >
                <img
                  :src="getProtectedImageUrl(card.id)"
                  alt="Carta inter-pod"
                  style="width: 48px; height: 48px; object-fit: cover; border-radius: 6px; border: 1px solid var(--c-border)"
                  @error="handleImageLoadError"
                />
              </a>
              <span v-else class="text-muted">-</span>
            </td>
            <td v-if="!disabled">
              <div class="flex gap-1" style="align-items: center; flex-wrap: wrap">
                <input
                  v-model.number="damageByCard[card.id]"
                  class="form-control"
                  type="number"
                  min="1"
                  placeholder="Daño"
                  style="width: 90px"
                />
                <button
                  type="button"
                  class="btn btn-secondary btn-sm"
                  :disabled="mutatingCardId === card.id"
                  @click="handleReportDamage(card)"
                >
                  Daño
                </button>

                <select v-model="statusByCard[card.id]" class="form-control" style="width: 130px">
                  <option v-for="s in CARD_STATUSES" :key="s" :value="s">{{ s }}</option>
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
