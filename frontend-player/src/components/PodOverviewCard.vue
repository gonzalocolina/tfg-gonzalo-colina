<script setup>
const props = defineProps({
  pod: { type: Object, required: true },
  currentGroupId: { type: Number, default: null },
})

function isCurrentGroup(group) {
  return group.id === props.currentGroupId
}

function formatPlayerCount(count) {
  return `${count} ${count === 1 ? 'jugador' : 'jugadores'}`
}
</script>

<template>
  <section class="card pod-overview-card">
    <div class="pod-overview-card__header">
      <div>
        <h2 class="pod-overview-card__name">{{ pod.name }}</h2>
      </div>

      <div class="pod-overview-card__stats">
        {{ pod.totalGroups }} {{ pod.totalGroups === 1 ? 'mesa' : 'mesas' }} ·
        {{ pod.totalPlayers }} {{ pod.totalPlayers === 1 ? 'jugador' : 'jugadores' }}
      </div>
    </div>

    <div v-if="pod.warnings?.length" class="pod-overview-card__warnings">
      <div v-for="(warning, index) in pod.warnings" :key="index" class="pod-overview-card__warning">
        {{ warning }}
      </div>
    </div>

    <ul v-if="pod.groups?.length" class="pod-overview-card__groups">
      <li
        v-for="group in pod.groups"
        :key="group.id"
        class="pod-overview-card__group"
        :class="{ 'pod-overview-card__group--current': isCurrentGroup(group) }"
      >
        <div>
          <div class="pod-overview-card__group-name">{{ group.identifier }}</div>
          <div class="text-muted" style="font-size: 0.82rem">{{ formatPlayerCount(group.playerCount) }}</div>
        </div>

        <span v-if="isCurrentGroup(group)" class="badge badge-ready">Tu mesa</span>
      </li>
    </ul>

    <div v-else class="text-muted" style="font-size: 0.88rem">Sin mesas aún.</div>
  </section>
</template>
