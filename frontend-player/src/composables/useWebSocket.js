import { ref, onUnmounted, unref, watch } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useGameStateStore } from '@/stores/gameState'
import { useNotificationsStore } from '@/stores/notifications'

export function resolveNotificationType(notification) {
  if (notification?.startsWith('INTER_POD_CARD_')) return 'info'
  if (notification.includes('VICTORY')) return 'success'
  if (notification.includes('DEFEAT')) return 'error'
  return 'warning'
}

export const NOTIFICATION_LABELS = {
  VILLAIN_HP_50_PERCENT: '¡Villano al 50% de HP!',
  VILLAIN_HP_25_PERCENT: '¡Villano al 25% de HP! ¡Casi derrotado!',
  THREAT_50_PERCENT: '¡Amenaza al 50%!',
  THREAT_75_PERCENT: '¡Amenaza al 75%! ¡Peligro!',
  THREAT_100_PERCENT: '¡Amenaza al 100%! Derrota por amenaza.',
  TIMER_30_MINUTES: 'Quedan 30 minutos',
  TIMER_15_MINUTES: 'Quedan 15 minutos',
  TIMER_5_MINUTES: '¡Solo quedan 5 minutos!',
  INTER_POD_CARD_CREATED: 'Se ha creado una carta inter-nodo en tu pod.',
  INTER_POD_CARD_DAMAGED: 'Una carta inter-nodo de tu pod ha recibido daño.',
  INTER_POD_CARD_THREAT_UPDATED: 'Se ha actualizado la amenaza de una carta inter-nodo de tu pod.',
  INTER_POD_CARD_STATUS_UPDATED: 'Se ha actualizado el estado de una carta inter-nodo de tu pod.',
  INTER_POD_CARD_DELETED: 'Se ha eliminado una carta inter-nodo de tu pod.',
  EVENT_FINISHED_PLAYERS_VICTORY: '¡Victoria! ¡El villano ha sido derrotado!',
  EVENT_FINISHED_THREAT_DEFEAT: 'Derrota - La amenaza ha alcanzado el límite',
  EVENT_FINISHED_TIMEOUT_DEFEAT: 'Derrota - Se agotó el tiempo',
}

export function mapNotificationLabel(notification) {
  return NOTIFICATION_LABELS[notification] || notification
}

function normalizePodId(podId) {
  if (podId == null || podId === '') return null
  return String(podId)
}

export function isRealtimeEventRelevantToPod(payload, podId) {
  const targetPodId = normalizePodId(payload?.targetPodId)
  if (!targetPodId) {
    return true
  }

  const currentPodId = normalizePodId(podId)
  return currentPodId === targetPodId
}

function pushRealtimeNotifications(payload, notifStore) {
  if (!payload.notifications?.length) return

  for (const notification of payload.notifications) {
    if (notification.startsWith('EVENT_FINISHED_')) {
      continue
    }

    notifStore.add(mapNotificationLabel(notification), resolveNotificationType(notification), 8000)
  }
}

export function useWebSocket(eventId, options = {}) {
  const connected = ref(false)
  const realtimeEvent = ref(null)
  const gameStore = useGameStateStore()
  const notifStore = useNotificationsStore()
  const podScopedRealtimeQueue = []
  const podScopeEnabled = Object.prototype.hasOwnProperty.call(options, 'podId')
  let client = null

  function rememberRealtimeEvent(payload) {
    realtimeEvent.value = {
      operation: payload?.operation || null,
      notifications: Array.isArray(payload?.notifications) ? [...payload.notifications] : [],
      targetPodId: payload?.targetPodId ?? null,
      emittedAt: payload?.emittedAt || Date.now(),
    }
  }

  function isPodScopeReady() {
    if (!podScopeEnabled) return true
    if (!Object.prototype.hasOwnProperty.call(options, 'podScopeReady')) return true
    return Boolean(unref(options.podScopeReady))
  }

  function applyRelevantRealtimePayload(payload) {
    pushRealtimeNotifications(payload, notifStore)
    rememberRealtimeEvent(payload)
  }

  function flushQueuedPodScopedRealtime() {
    if (!podScopeEnabled || !isPodScopeReady() || !podScopedRealtimeQueue.length) {
      return
    }

    const queuedPayloads = podScopedRealtimeQueue.splice(0, podScopedRealtimeQueue.length)
    const currentPodId = unref(options.podId)

    for (const payload of queuedPayloads) {
      if (isRealtimeEventRelevantToPod(payload, currentPodId)) {
        applyRelevantRealtimePayload(payload)
      }
    }
  }

  function handlePodScopedRealtimePayload(payload) {
    if (!payload) return

    if (!podScopeEnabled || payload?.targetPodId == null) {
      applyRelevantRealtimePayload(payload)
      return
    }

    if (!isPodScopeReady()) {
      podScopedRealtimeQueue.push(payload)
      return
    }

    if (isRealtimeEventRelevantToPod(payload, unref(options.podId))) {
      applyRelevantRealtimePayload(payload)
    }
  }

  if (podScopeEnabled) {
    watch(
      () => [unref(options.podScopeReady), unref(options.podId)],
      () => {
        flushQueuedPodScopedRealtime()
      },
      { immediate: true },
    )
  }

  function connect() {
    client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 3000,
      onConnect: () => {
        connected.value = true

        client.subscribe(`/topic/events/${eventId}/game-state`, (msg) => {
          const payload = JSON.parse(msg.body)
          gameStore.applySnapshot(payload.snapshot)
          handlePodScopedRealtimePayload(payload)
        })

        client.subscribe(`/topic/events/${eventId}/notifications`, (msg) => {
          const payload = JSON.parse(msg.body)
          if (payload.message) {
            notifStore.info(payload.message)
          }
        })

        client.subscribe(`/topic/events/${eventId}/game-finished`, (msg) => {
          const payload = JSON.parse(msg.body)
          gameStore.applySnapshot(payload.snapshot)
          const notificationKey = `EVENT_FINISHED_${payload.snapshot?.eventResult}`
          if (NOTIFICATION_LABELS[notificationKey]) {
            notifStore.add(mapNotificationLabel(notificationKey), 'warning', 0)
          }
        })
      },
      onDisconnect: () => {
        connected.value = false
      },
      onStompError: () => {
        connected.value = false
      },
    })

    client.activate()
  }

  function disconnect() {
    if (client) {
      client.deactivate()
      client = null
      connected.value = false
    }
  }

  onUnmounted(() => disconnect())

  return { connected, realtimeEvent, connect, disconnect }
}
