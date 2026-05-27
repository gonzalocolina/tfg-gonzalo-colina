import { ref, onUnmounted } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useGameStateStore } from '@/stores/gameState'
import { useNotificationsStore } from '@/stores/notifications'
import { formatLogActionType } from '@/utils/displayLabels'

const DEFAULT_NOTIFICATION_LABEL = 'Se ha producido una actualización de la partida.'
const REALTIME_NOTIFICATION_TIMEOUT = 8000
const FINAL_RESULT_NOTIFICATION_TIMEOUT = 12000

function normalizeNotificationCode(notification) {
  return typeof notification === 'string' ? notification.trim() : ''
}

function resolveNotificationType(notification) {
  const code = normalizeNotificationCode(notification)
  if (!code) return 'info'
  if (code.startsWith('INTER_POD_CARD_')) return 'info'
  if (code.includes('VICTORY')) return 'success'
  if (code.includes('DEFEAT')) return 'error'
  return 'warning'
}

function resolveNotificationLabel(notification, labels) {
  const code = normalizeNotificationCode(notification)
  if (!code) {
    return DEFAULT_NOTIFICATION_LABEL
  }

  if (labels[code]) {
    return labels[code]
  }

  const formattedLabel = formatLogActionType(code)
  if (formattedLabel && formattedLabel !== '-' && formattedLabel !== code) {
    return formattedLabel
  }

  return DEFAULT_NOTIFICATION_LABEL
}

function isFinalResultNotification(notification) {
  return normalizeNotificationCode(notification).startsWith('EVENT_FINISHED_')
}

function resolveNotificationTimeout(notification) {
  return isFinalResultNotification(notification)
    ? FINAL_RESULT_NOTIFICATION_TIMEOUT
    : REALTIME_NOTIFICATION_TIMEOUT
}

function addToastNotifications(notifications, notifStore, labels, payload = null) {
  const targetPodId = payload?.targetPodId ?? null

  for (const rawNotification of notifications || []) {
    const notification = normalizeNotificationCode(rawNotification)
    if (!notification || isFinalResultNotification(notification)) {
      continue
    }

    if (notifStore.consumeSuppressedRealtimeNotification(notification, { targetPodId })) {
      continue
    }

    notifStore.add(
      resolveNotificationLabel(notification, labels),
      resolveNotificationType(notification),
      resolveNotificationTimeout(notification),
    )
  }
}

export function useWebSocket(eventId) {
  const connected = ref(false)
  const realtimeEvent = ref(null)
  let client = null

  const NOTIFICATION_LABELS = {
    VILLAIN_HP_50_PERCENT: '¡Villano al 50% de HP!',
    VILLAIN_HP_25_PERCENT: '¡Villano al 25% de HP! ¡Casi derrotado!',
    THREAT_50_PERCENT: '¡Amenaza al 50%!',
    THREAT_75_PERCENT: '¡Amenaza al 75%! ¡Peligro!',
    THREAT_100_PERCENT: '¡Amenaza al 100%! Derrota por amenaza.',
    INTER_POD_CARD_CREATED: 'Ha aparecido una carta inter-nodo.',
    INTER_POD_CARD_DAMAGED: 'Se ha aplicado daño a una carta inter-nodo.',
    INTER_POD_CARD_THREAT_UPDATED: 'Se ha actualizado la amenaza de una carta inter-nodo.',
    INTER_POD_CARD_THREAT_REDUCED: 'Se ha actualizado la amenaza de una carta inter-nodo.',
    INTER_POD_CARD_STATUS_UPDATED: 'Se ha actualizado el estado de una carta inter-nodo.',
    INTER_POD_CARD_DELETED: 'Se ha eliminado una carta inter-nodo.',
    TIMER_30_MINUTES: 'Quedan 30 minutos',
    TIMER_15_MINUTES: 'Quedan 15 minutos',
    TIMER_5_MINUTES: '¡Solo quedan 5 minutos!',
    EVENT_FINISHED_PLAYERS_VICTORY: '¡Victoria! ¡El villano ha sido derrotado!',
    EVENT_FINISHED_THREAT_DEFEAT: 'Derrota - La amenaza ha alcanzado el límite',
    EVENT_FINISHED_TIMEOUT_DEFEAT: 'Derrota - Se agotó el tiempo',
  }

  function rememberRealtimeEvent(payload) {
    realtimeEvent.value = {
      operation: payload?.operation || null,
      notifications: Array.isArray(payload?.notifications) ? [...payload.notifications] : [],
      targetPodId: payload?.targetPodId ?? null,
      emittedAt: payload?.emittedAt || Date.now(),
    }
  }

  function connect() {
    const gameStore = useGameStateStore()
    const notifStore = useNotificationsStore()

    client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 3000,
      onConnect: () => {
        connected.value = true

        client.subscribe(`/topic/events/${eventId}/game-state`, (msg) => {
          const payload = JSON.parse(msg.body)
          gameStore.applySnapshot(payload.snapshot)
          rememberRealtimeEvent(payload)
        })

        client.subscribe(`/topic/events/${eventId}/notifications`, (msg) => {
          const payload = JSON.parse(msg.body)
          if (payload.notifications?.length) {
            addToastNotifications(payload.notifications, notifStore, NOTIFICATION_LABELS, payload)
          } else if (payload.message) {
            notifStore.info(payload.message)
          }
        })

        client.subscribe(`/topic/events/${eventId}/game-finished`, (msg) => {
          const payload = JSON.parse(msg.body)
          gameStore.applySnapshot(payload.snapshot)
          const notificationCode = payload.snapshot?.eventResult
            ? `EVENT_FINISHED_${payload.snapshot.eventResult}`
            : null

          if (
            notificationCode
            && notifStore.consumeSuppressedRealtimeNotification(notificationCode, { targetPodId: payload?.targetPodId ?? null })
          ) {
            return
          }

          if (notificationCode) {
            notifStore.add(
              resolveNotificationLabel(notificationCode, NOTIFICATION_LABELS),
              resolveNotificationType(notificationCode),
              resolveNotificationTimeout(notificationCode),
            )
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
