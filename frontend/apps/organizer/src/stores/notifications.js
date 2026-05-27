import { defineStore } from 'pinia'
import { ref } from 'vue'

let nextId = 0

const ALLOWED_TYPES = new Set(['info', 'success', 'warning', 'error'])
const DEFAULT_TIMEOUTS = Object.freeze({
  info: 5000,
  success: 5000,
  warning: 6500,
  error: 8000,
})
const DEFAULT_MESSAGES = Object.freeze({
  info: 'Se ha producido una actualización.',
  success: 'Operación completada correctamente.',
  warning: 'Atención: revisa el último cambio.',
  error: 'Ha ocurrido un error inesperado.',
})
const REALTIME_SUPPRESSION_TTL = 4000

function normalizeType(type) {
  return ALLOWED_TYPES.has(type) ? type : 'info'
}

function normalizeTargetPodId(targetPodId) {
  if (targetPodId == null || targetPodId === '') {
    return null
  }

  return String(targetPodId)
}

function normalizeMessage(message, type) {
  if (typeof message === 'string' && message.trim()) {
    return message.trim()
  }

  if (message && typeof message === 'object') {
    const nestedMessage = typeof message.message === 'string' ? message.message.trim() : ''
    if (nestedMessage) {
      return nestedMessage
    }
  }

  if (message != null) {
    const serializedMessage = String(message).trim()
    if (serializedMessage && serializedMessage !== '[object Object]') {
      return serializedMessage
    }
  }

  return DEFAULT_MESSAGES[type]
}

function resolveTimeout(timeout, type) {
  if (Number.isFinite(timeout)) {
    return timeout > 0 ? Math.trunc(timeout) : 0
  }

  return DEFAULT_TIMEOUTS[type]
}

export const useNotificationsStore = defineStore('notifications', () => {
  const items = ref([])
  const timers = new Map()
  const suppressedRealtimeNotifications = []

  function clearTimer(id) {
    const timer = timers.get(id)
    if (!timer) {
      return
    }

    clearTimeout(timer)
    timers.delete(id)
  }

  function pruneSuppressedRealtimeNotifications(now = Date.now()) {
    for (let index = suppressedRealtimeNotifications.length - 1; index >= 0; index -= 1) {
      if (suppressedRealtimeNotifications[index].expiresAt <= now) {
        suppressedRealtimeNotifications.splice(index, 1)
      }
    }
  }

  function add(message, type = 'info', timeout = undefined) {
    const normalizedType = normalizeType(type)
    const normalizedMessage = normalizeMessage(message, normalizedType)
    const resolvedTimeout = resolveTimeout(timeout, normalizedType)
    const id = ++nextId

    items.value.push({ id, message: normalizedMessage, type: normalizedType })

    if (resolvedTimeout > 0) {
      const timer = setTimeout(() => remove(id), resolvedTimeout)
      timers.set(id, timer)
    }

    return id
  }

  function remove(id) {
    clearTimer(id)
    items.value = items.value.filter((n) => n.id !== id)
  }

  function suppressRealtimeNotification(code, { targetPodId = null, ttl = REALTIME_SUPPRESSION_TTL } = {}) {
    const normalizedCode = typeof code === 'string' ? code.trim() : ''
    if (!normalizedCode) {
      return
    }

    pruneSuppressedRealtimeNotifications()
    suppressedRealtimeNotifications.push({
      code: normalizedCode,
      targetPodId: normalizeTargetPodId(targetPodId),
      expiresAt: Date.now() + (Number.isFinite(ttl) && ttl > 0 ? Math.trunc(ttl) : REALTIME_SUPPRESSION_TTL),
    })
  }

  function consumeSuppressedRealtimeNotification(code, { targetPodId = null } = {}) {
    const normalizedCode = typeof code === 'string' ? code.trim() : ''
    if (!normalizedCode) {
      return false
    }

    pruneSuppressedRealtimeNotifications()
    const normalizedTargetPodId = normalizeTargetPodId(targetPodId)
    const suppressedIndex = suppressedRealtimeNotifications.findIndex((entry) =>
      entry.code === normalizedCode && (entry.targetPodId === null || entry.targetPodId === normalizedTargetPodId),
    )

    if (suppressedIndex === -1) {
      return false
    }

    suppressedRealtimeNotifications.splice(suppressedIndex, 1)
    return true
  }

  function clear() {
    for (const item of items.value) {
      clearTimer(item.id)
    }

    items.value = []
  }

  function info(msg, timeout = undefined) {
    add(msg, 'info', timeout)
  }
  function success(msg, timeout = undefined) {
    add(msg, 'success', timeout)
  }
  function warning(msg, timeout = undefined) {
    add(msg, 'warning', timeout)
  }
  function error(msg, timeout = undefined) {
    add(msg, 'error', timeout)
  }

  return {
    items,
    add,
    remove,
    clear,
    info,
    success,
    warning,
    error,
    suppressRealtimeNotification,
    consumeSuppressedRealtimeNotification,
  }
})
