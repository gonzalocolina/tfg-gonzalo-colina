<script setup>
import { computed, ref, watch } from 'vue'
import { updateGroup } from '@/api/setup'
import { useNotificationsStore } from '@/stores/notifications'

const props = defineProps({
  group: { type: Object, required: true },
  eventId: { type: [Number, String], required: true },
  isDragging: { type: Boolean, default: false },
  moving: { type: Boolean, default: false },
})

const emit = defineEmits(['changed', 'drag-start', 'drag-end'])

const notif = useNotificationsStore()
const editing = ref(false)
const saving = ref(false)
const draftIdentifier = ref(props.group.identifier)
const draftPlayerCount = ref(props.group.playerCount)

const playerCountLabel = computed(() => {
  const count = props.group.playerCount ?? 0
  return `${count} ${count === 1 ? 'jugador' : 'jugadores'}`
})

watch(
  () => [props.group.identifier, props.group.playerCount],
  ([nextIdentifier, nextPlayerCount]) => {
    if (!editing.value) {
      draftIdentifier.value = nextIdentifier
      draftPlayerCount.value = nextPlayerCount
    }
  },
)

function startEdit() {
  if (props.moving) return
  draftIdentifier.value = props.group.identifier
  draftPlayerCount.value = props.group.playerCount
  editing.value = true
}

function cancelEdit() {
  draftIdentifier.value = props.group.identifier
  draftPlayerCount.value = props.group.playerCount
  editing.value = false
}

async function submitUpdate() {
  const nextIdentifier = draftIdentifier.value.trim()
  const nextPlayerCount = Number(draftPlayerCount.value)

  if (!nextIdentifier) {
    notif.error('El nombre de la mesa no puede estar vacío.')
    return
  }

  if (!Number.isInteger(nextPlayerCount) || nextPlayerCount < 1 || nextPlayerCount > 4) {
    notif.error('El número de jugadores debe estar entre 1 y 4.')
    return
  }

  if (nextIdentifier === props.group.identifier && nextPlayerCount === props.group.playerCount) {
    cancelEdit()
    return
  }

  saving.value = true
  try {
    await updateGroup(props.eventId, props.group.id, {
      identifier: nextIdentifier,
      playerCount: nextPlayerCount,
    })
    notif.success('Mesa actualizada')
    editing.value = false
    emit('changed')
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al actualizar la mesa')
  } finally {
    saving.value = false
  }
}

function handleDragStart(event) {
  if (editing.value || saving.value || props.moving) {
    event.preventDefault()
    return
  }
  event.dataTransfer?.setData('text/plain', String(props.group.id))
  event.dataTransfer?.setData('application/x-group-id', String(props.group.id))
  event.dataTransfer?.setData('application/x-group-pod-id', props.group.podId == null ? '' : String(props.group.podId))
  event.dataTransfer?.setData('application/x-group-name', props.group.identifier)
  event.dataTransfer?.setData('application/x-group-player-count', String(props.group.playerCount ?? 0))
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
  emit('drag-start', props.group)
}

function handleDragEnd() {
  emit('drag-end', props.group)
}
</script>

<template>
  <article
    class="group-card"
    :class="{
      'group-card--dragging': isDragging,
      'group-card--moving': moving,
      'group-card--editing': editing,
    }"
    :data-testid="`group-item-${group.id}`"
    :draggable="!editing && !saving && !moving"
    @dragstart="handleDragStart"
    @dragend="handleDragEnd"
  >
    <template v-if="editing">
      <div class="group-card__edit-grid">
        <div class="form-group" style="margin-bottom: 0">
          <label :for="`group-identifier-${group.id}`">Mesa</label>
          <input
            :id="`group-identifier-${group.id}`"
            v-model="draftIdentifier"
            class="form-control"
            maxlength="40"
            @keyup.enter="submitUpdate"
            @keyup.esc="cancelEdit"
          />
        </div>
        <div class="form-group" style="margin-bottom: 0">
          <label :for="`group-player-count-${group.id}`">Jugadores</label>
          <input
            :id="`group-player-count-${group.id}`"
            v-model.number="draftPlayerCount"
            type="number"
            min="1"
            max="4"
            class="form-control"
            @keyup.enter="submitUpdate"
            @keyup.esc="cancelEdit"
          />
        </div>
      </div>
      <div class="group-card__actions">
        <button type="button" class="btn btn-primary btn-sm" :disabled="saving" @click="submitUpdate">
          {{ saving ? 'Guardando…' : 'Guardar' }}
        </button>
        <button type="button" class="btn btn-secondary btn-sm" :disabled="saving" @click="cancelEdit">
          Cancelar
        </button>
      </div>
    </template>

    <template v-else>
      <div class="group-card__top">
        <strong class="group-card__name">{{ group.identifier }}</strong>
        <button
          type="button"
          class="group-card__edit-button"
          :data-testid="`group-edit-button-${group.id}`"
          :disabled="moving"
          :aria-label="`Editar ${group.identifier}`"
          :title="`Editar ${group.identifier}`"
          @click.stop="startEdit"
        >
          <svg viewBox="0 0 24 24" class="group-card__edit-icon" aria-hidden="true">
            <path
              d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25Zm2.92 2.33H5v-.92l8.06-8.06.92.92L5.92 19.58ZM20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82Z"
              fill="currentColor"
            />
          </svg>
        </button>
      </div>

      <div class="group-card__meta">
        <span class="group-card__players-pill">{{ playerCountLabel }}</span>
        <span class="group-card__hint">Pulsa el lápiz para editar · arrastra para mover</span>
      </div>
    </template>
  </article>
</template>

<style scoped>
.group-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: var(--c-surface-alt);
  cursor: grab;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease, opacity 0.15s ease;
}

.group-card:hover {
  border-color: color-mix(in srgb, var(--c-primary) 55%, var(--c-border));
  box-shadow: 0 10px 18px rgba(0, 0, 0, 0.12);
}

.group-card--dragging {
  opacity: 0.45;
  transform: scale(0.98);
}

.group-card--moving {
  opacity: 0.55;
  pointer-events: none;
}

.group-card--editing {
  cursor: default;
}

.group-card--editing:hover {
  border-color: var(--c-border);
  box-shadow: none;
}

.group-card__top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.group-card__name {
  flex: 1;
  color: var(--c-text);
  font-size: 1rem;
}

.group-card__edit-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid color-mix(in srgb, var(--c-primary) 45%, var(--c-border));
  border-radius: 10px;
  background: color-mix(in srgb, var(--c-primary) 10%, var(--c-surface));
  color: var(--c-primary);
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}

.group-card__edit-button:hover:not(:disabled) {
  background: color-mix(in srgb, var(--c-primary) 18%, var(--c-surface));
  border-color: color-mix(in srgb, var(--c-primary) 70%, var(--c-border));
  transform: translateY(-1px);
}

.group-card__edit-button:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--c-primary) 70%, white);
  outline-offset: 2px;
}

.group-card__edit-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.group-card__edit-icon {
  width: 16px;
  height: 16px;
}

.group-card__meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.group-card__players-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--c-info) 14%, var(--c-surface));
  color: var(--c-text);
  font-size: 0.8rem;
  font-weight: 600;
}

.group-card__hint {
  font-size: 0.78rem;
  color: var(--c-text-muted);
}

.group-card__edit-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px;
  gap: 10px;
}

.group-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 640px) {
  .group-card__edit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
