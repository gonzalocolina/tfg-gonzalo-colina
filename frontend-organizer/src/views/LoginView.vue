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
const showPassword = ref(false)
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
      <h1>Marvel Champions - Organizador</h1>
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
          <div class="password-control">
            <input
              v-if="showPassword"
              id="password"
              v-model="password"
              type="text"
              class="form-control"
              required
              autocomplete="off"
            />
            <input
              v-else
              id="password"
              v-model="password"
              type="password"
              class="form-control"
              required
              autocomplete="current-password"
            />
            <button
              type="button"
              class="password-toggle"
              :aria-label="showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'"
              :aria-pressed="showPassword"
              :title="showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'"
              @click="showPassword = !showPassword"
            >
              <svg
                v-if="showPassword"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M3 3l18 18" />
                <path d="M10.58 10.58A2 2 0 0012 14a2 2 0 001.42-.58" />
                <path d="M9.88 5.09A9.77 9.77 0 0112 4.8c4.66 0 8.45 2.91 9.68 7.2a10.8 10.8 0 01-4.23 5.77" />
                <path d="M6.61 6.62A10.75 10.75 0 002.32 12a10.79 10.79 0 004.97 6.2A9.74 9.74 0 0012 19.2c.82 0 1.61-.1 2.37-.29" />
              </svg>
              <svg
                v-else
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M2.32 12a10.79 10.79 0 0119.36 0 10.79 10.79 0 01-19.36 0z" />
                <circle cx="12" cy="12" r="3" />
              </svg>
            </button>
          </div>
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
