<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'

const router = useRouter()
const auth = useAuthStore()
const notif = useNotificationsStore()

const code = ref('')
const groupIdentifier = ref('')
const playerCount = ref(1)
const loading = ref(false)

async function handleJoin() {
  if (!code.value.trim()) return
  loading.value = true
  try {
    await auth.login(code.value.trim(), groupIdentifier.value.trim(), playerCount.value)
    router.push({ name: 'game' })
  } catch (err) {
    notif.error(err.response?.data?.message || 'Código de acceso inválido')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="join-page">
    <div class="join-card">
      <h1 class="join-title">Marvel Champions</h1>
      <p class="join-subtitle">Unirse a la partida</p>

      <form @submit.prevent="handleJoin">
        <div class="form-group">
          <label for="code" class="form-label">Código de acceso</label>
          <input
            id="code"
            v-model="code"
            class="form-control"
            type="text"
            placeholder="Introduce el código del evento o de la mesa"
            autocomplete="off"
            maxlength="16"
            required
          />
        </div>

        <div class="form-group">
          <label for="group-identifier" class="form-label">Identificador de mesa (opcional)</label>
          <input
            id="group-identifier"
            v-model="groupIdentifier"
            class="form-control"
            type="text"
            placeholder="Ej. Mesa 1"
            autocomplete="organization"
            maxlength="40"
          />
        </div>

        <div class="form-group">
          <label for="player-count" class="form-label">Número de jugadores en tu mesa</label>
          <input
            id="player-count"
            v-model.number="playerCount"
            class="form-control"
            type="number"
            min="1"
            max="4"
            required
          />
        </div>

        <button class="btn btn-primary btn-block btn-lg" :disabled="loading || !code.trim()">
          {{ loading ? 'Conectando…' : 'Entrar' }}
        </button>
      </form>

      <p class="text-muted mt-2 join-help">
        Usa el código del evento para registrar tu mesa antes de que empiece la partida. Si la partida ya ha comenzado,
        solo podrás volver a entrar con el identificador de una mesa ya registrada.
      </p>
    </div>
  </div>
</template>
