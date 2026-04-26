import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { adminLogin, coOrganizerLogin, register } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const saved = sessionStorage.getItem('auth')
  const initial = saved ? JSON.parse(saved) : {}

  const accessToken = ref(initial.accessToken || '')
  const role = ref(initial.role || '')
  const eventId = ref(initial.eventId || null)
  const groupId = ref(initial.groupId || null)
  const username = ref(initial.username || '')

  const isAuthenticated = computed(() => !!accessToken.value)
  const isOrganizer = computed(() => role.value === 'ORGANIZER')
  const isCoOrganizer = computed(() => role.value === 'CO_ORGANIZER')

  function persist() {
    sessionStorage.setItem(
      'auth',
      JSON.stringify({
        accessToken: accessToken.value,
        role: role.value,
        eventId: eventId.value,
        groupId: groupId.value,
        username: username.value,
      }),
    )
  }

  function setSession(data) {
    accessToken.value = data.accessToken
    role.value = data.role
    eventId.value = data.eventId
    groupId.value = data.groupId
    username.value = data.username
    persist()
  }

  async function registerAndLogin(user, email, pass) {
    const { data } = await register(user, email, pass)
    setSession(data)
    return data
  }

  async function loginAdmin(user, pass) {
    const { data } = await adminLogin(user, pass)
    setSession(data)
    return data
  }

  async function loginCoOrg(evtId, user, pass) {
    const { data } = await coOrganizerLogin(evtId, user, pass)
    setSession(data)
    return data
  }

  function logout() {
    accessToken.value = ''
    role.value = ''
    eventId.value = null
    groupId.value = null
    username.value = ''
    sessionStorage.removeItem('auth')
  }

  return {
    accessToken,
    role,
    eventId,
    groupId,
    username,
    isAuthenticated,
    isOrganizer,
    isCoOrganizer,
    loginAdmin,
    loginCoOrg,
    registerAndLogin,
    logout,
    setSession,
  }
})
