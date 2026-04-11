<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'

const router = useRouter()
const auth = useAuthStore()
const notif = useNotificationsStore()

const form = ref({
  username: '',
  displayName: '',
  email: '',
  password: '',
})
const loading = ref(false)
const errorMsg = ref('')

async function handleRegister() {
  errorMsg.value = ''
  loading.value = true
  try {
    await auth.registerAndLogin(
      form.value.username,
      form.value.displayName,
      form.value.email,
      form.value.password,
    )
    notif.success('Cuenta creada correctamente')
    router.push({ name: 'my-events' })
  } catch (err) {
    errorMsg.value =
      err.response?.data?.message ||
      err.response?.data?.details?.join(', ') ||
      'Error al crear la cuenta'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-centered">
    <div class="form-card" style="max-width: 480px">
      <h1>Crear cuenta</h1>
      <form @submit.prevent="handleRegister">
        <div class="form-row">
          <div class="form-group">
            <label for="username">Usuario</label>
            <input
              id="username"
              v-model="form.username"
              type="text"
              class="form-control"
              required
              maxlength="60"
              autocomplete="username"
            />
          </div>
          <div class="form-group">
            <label for="displayName">Nombre a mostrar</label>
            <input
              id="displayName"
              v-model="form.displayName"
              class="form-control"
              required
              maxlength="120"
            />
          </div>
        </div>
        <div class="form-group">
          <label for="email">Email (opcional)</label>
          <input
            id="email"
            v-model="form.email"
            type="email"
            class="form-control"
            maxlength="120"
          />
        </div>
        <div class="form-group">
          <label for="password">Contraseña</label>
          <input
            id="password"
            v-model="form.password"
            type="password"
            class="form-control"
            required
            minlength="8"
            maxlength="120"
            autocomplete="new-password"
          />
        </div>

        <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>
        <div class="form-actions">
          <button class="btn btn-primary" :disabled="loading" style="flex: 1">
            {{ loading ? 'Creando…' : 'Crear cuenta' }}
          </button>
        </div>
        <p class="mt-2 text-center text-muted" style="font-size: 0.85rem">
          ¿Ya tienes cuenta?
          <router-link to="/login">Iniciar sesión</router-link>
        </p>
      </form>
    </div>
  </div>
</template>
