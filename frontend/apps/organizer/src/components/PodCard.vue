<script setup>
import { ref, watch } from 'vue'
import { deletePod, updatePod } from '@/api/setup'
import { useDialogs } from '@/composables/useDialogs'
import GroupItem from '@/components/GroupItem.vue'
import { useNotificationsStore } from '@/stores/notifications'

const props = defineProps({
  pod: { type: Object, required: true },
  eventId: { type: [Number, String], required: true },
  dropActive: { type: Boolean, default: false },
  activeDragGroupId: { type: [Number, String, null], default: null },
  movingGroupId: { type: [Number, String, null], default: null },
  setupLocked: { type: Boolean, default: false },
  setupEditable: { type: Boolean, default: true },
})

const emit = defineEmits(['changed', 'group-drag-start', 'group-drag-end', 'drag-over-column', 'drop-group'])

const dialogs = useDialogs()
const notif = useNotificationsStore()
const editingName = ref(false)
const savingName = ref(false)
const deletingPod = ref(false)
const draftName = ref(props.pod.name)

watch(
  () => props.pod.name,
  (nextName) => {
    if (!editingName.value) {
      draftName.value = nextName
    }
  },
)

function startEdit() {
  if (props.setupLocked) return
  draftName.value = props.pod.name
  editingName.value = true
}

function cancelEdit() {
  draftName.value = props.pod.name
  editingName.value = false
}

async function submitRename() {
  const nextName = draftName.value.trim()
  if (!nextName) {
    notif.error('El nombre del pod no puede estar vacío.')
    return
  }
  if (nextName === props.pod.name) {
    cancelEdit()
    return
  }

  savingName.value = true
  try {
    await updatePod(props.eventId, props.pod.id, { name: nextName })
    notif.success('Pod renombrado')
    editingName.value = false
    emit('changed')
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al renombrar el pod')
  } finally {
    savingName.value = false
  }
}

async function handleDelete() {
  const warning = props.pod.totalGroups
    ? ' Sus mesas pasarán a pendientes de asignación.'
    : ''

  const confirmed = await dialogs.confirm({
    title: 'Eliminar pod',
    message: `¿Quieres eliminar ${props.pod.name}?${warning}`,
    confirmLabel: 'Eliminar',
    tone: 'danger',
  })

  if (!confirmed) return

  deletingPod.value = true
  try {
    await deletePod(props.eventId, props.pod.id)
    notif.success('Pod eliminado')
    emit('changed')
  } catch (err) {
    notif.error(err.response?.data?.message || 'Error al eliminar el pod')
  } finally {
    deletingPod.value = false
  }
}

function handleDragOver() {
  if (props.setupLocked) return
  emit('drag-over-column', props.pod)
}

function handleDrop() {
  if (props.setupLocked) return
  emit('drop-group', props.pod)
}
</script>

<template>
  <section
    class="pod-column"
    :class="{ 'pod-column--drop-active': dropActive }"
    :data-testid="`pod-column-${pod.id}`"
    @dragover.prevent="handleDragOver"
    @dragenter.prevent="handleDragOver"
    @drop.prevent="handleDrop"
  >
    <div class="pod-column__header">
      <div class="pod-column__title-block">
        <div v-if="editingName" class="flex gap-1" style="align-items: center; flex-wrap: wrap">
          <input
            v-model="draftName"
            class="form-control"
            maxlength="80"
            style="min-width: 200px"
            @keyup.enter="submitRename"
            @keyup.esc="cancelEdit"
          />
          <button type="button" class="btn btn-primary btn-sm" :disabled="savingName" @click="submitRename">
            {{ savingName ? 'Guardando…' : 'Guardar' }}
          </button>
          <button type="button" class="btn btn-secondary btn-sm" :disabled="savingName" @click="cancelEdit">
            Cancelar
          </button>
        </div>
        <button
          v-else
          type="button"
          class="pod-column__name-button"
          :disabled="setupLocked"
          @click="startEdit"
        >
          <h3>{{ pod.name }}</h3>
        </button>
        <span class="text-muted pod-column__stats">
          {{ pod.totalGroups }} mesas · {{ pod.totalPlayers }} jugadores
        </span>
      </div>

      <button
        v-if="setupEditable"
        type="button"
        class="btn btn-danger btn-sm"
        :disabled="deletingPod"
        @click="handleDelete"
      >
        {{ deletingPod ? 'Eliminando…' : 'Eliminar pod' }}
      </button>
    </div>

    <div v-if="pod.warnings?.length" class="mb-1">
      <div v-for="(w, i) in pod.warnings" :key="i" class="warn-box">
        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align:middle;margin-right:4px"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>{{ w }}
      </div>
    </div>

    <div v-if="pod.groups?.length" class="pod-column__groups">
      <GroupItem
        v-for="g in pod.groups"
        :key="g.id"
        :group="g"
        :event-id="eventId"
        :is-dragging="activeDragGroupId === g.id"
        :moving="movingGroupId === g.id"
        :setup-locked="setupLocked"
        :deletable="false"
        @changed="emit('changed')"
        @drag-start="emit('group-drag-start', $event)"
        @drag-end="emit('group-drag-end', $event)"
      />
    </div>
    <div v-else class="pod-column__empty" :class="{ 'pod-column__empty--active': dropActive }">
      <span class="text-muted">Sin mesas aún. Suelta una aquí para asignarla.</span>
    </div>
  </section>
</template>

<style scoped>
.pod-column {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
  padding: 18px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: var(--c-surface);
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.pod-column--drop-active {
  border-color: color-mix(in srgb, var(--c-primary) 80%, var(--c-border));
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--c-primary) 30%, transparent), 0 14px 24px rgba(0, 0, 0, 0.14);
  transform: translateY(-2px);
}

.pod-column__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.pod-column__title-block {
  flex: 1;
  min-width: 0;
}

.pod-column__name-button {
  background: none;
  border: none;
  padding: 0;
  font: inherit;
  color: var(--c-text);
  cursor: pointer;
  text-align: left;
  margin-right: 15px;
}

.pod-column__name-button h3 {
  margin: 0;
}

.pod-column__stats {
  display: inline-block;
  margin-top: 4px;
}

.pod-column__groups {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pod-column__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  padding: 18px;
  border: 1px dashed var(--c-border);
  border-radius: var(--radius);
  background: color-mix(in srgb, var(--c-surface-alt) 85%, transparent);
  text-align: center;
}

.pod-column__empty--active {
  border-color: var(--c-primary);
  background: color-mix(in srgb, var(--c-primary) 10%, var(--c-surface));
}
</style>
