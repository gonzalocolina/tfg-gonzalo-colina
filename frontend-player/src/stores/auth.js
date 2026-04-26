import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { groupLogin } from '@/api/auth'

const STORAGE_KEY = 'auth-player'

export const useAuthStore = defineStore('auth', () => {
  const saved = sessionStorage.getItem(STORAGE_KEY)
  const initial = saved ? JSON.parse(saved) : {}

  const accessToken = ref(initial.accessToken || '')
  const role = ref(initial.role || '')
  const eventId = ref(initial.eventId || null)
  const groupId = ref(initial.groupId || null)
  const groupLabel = ref(initial.groupLabel || '')
  const code = ref(initial.code || '')

  const isAuthenticated = computed(() => !!accessToken.value)
  const isRepresentative = computed(() => role.value === 'GROUP_REPRESENTATIVE')
  const isObserver = computed(() => role.value === 'GROUP_OBSERVER')

  function persist() {
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        accessToken: accessToken.value,
        role: role.value,
        eventId: eventId.value,
        groupId: groupId.value,
        groupLabel: groupLabel.value,
        code: code.value,
      }),
    )
  }

  function setSession(data, loginCode) {
    accessToken.value = data.accessToken
    role.value = data.role
    eventId.value = data.eventId
    groupId.value = data.groupId
    groupLabel.value = data.groupLabel
    code.value = data.groupCode || loginCode
    persist()
  }

  async function login(loginCode, groupIdentifier = '', playerCount = null) {
    const { data } = await groupLogin(loginCode, groupIdentifier, playerCount)
    setSession(data, loginCode)
    return data
  }

  function logout() {
    accessToken.value = ''
    role.value = ''
    eventId.value = null
    groupId.value = null
    groupLabel.value = ''
    code.value = ''
    sessionStorage.removeItem(STORAGE_KEY)
  }

  return {
    accessToken,
    role,
    eventId,
    groupId,
    groupLabel,
    code,
    isAuthenticated,
    isRepresentative,
    isObserver,
    login,
    logout,
  }
})
