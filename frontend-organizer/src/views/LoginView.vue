<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'

const router = useRouter()
const auth = useAuthStore()
const notif = useNotificationsStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const errorMsg = ref('')

async function handleLogin() {
  errorMsg.value = ''
  loading.value = true
  try {
    await auth.loginOrganizer(username.value, password.value)
    notif.success('Sesión iniciada')
    router.push({ name: 'my-events' })
  } catch (err) {
    errorMsg.value = err.response?.data?.message || 'Credenciales inválidas'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-centered">
    <div class="form-card">
      <h1>Marvel Champions - Organizer</h1>
      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label for="username">Usuario</label>
          <input
            id="username"
            v-model="username"
            type="text"
            class="form-control"
            required
            autocomplete="username"
          />
        </div>
        <div class="form-group">
          <label for="password">Contraseña</label>
          <input
            id="password"
            v-model="password"
            type="password"
            class="form-control"
            required
            autocomplete="current-password"
          />
        </div>
        <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>
        <div class="form-actions">
          <button class="btn btn-primary" :disabled="loading" style="flex: 1">
            {{ loading ? 'Entrando…' : 'Iniciar sesión' }}
          </button>
        </div>
        <p class="mt-2 text-center text-muted" style="font-size: 0.85rem">
          ¿No tienes cuenta?
          <router-link to="/register">Registrarse</router-link>
        </p>
      </form>
    </div>
  </div>
</template>
