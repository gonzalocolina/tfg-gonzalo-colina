import { ref, onUnmounted } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import { useGameStateStore } from '@/stores/gameState'
import { useNotificationsStore } from '@/stores/notifications'

function resolveNotificationType(notification) {
  if (notification.includes('VICTORY')) return 'success'
  if (notification.includes('DEFEAT')) return 'error'
  return 'warning'
}

export function useWebSocket(eventId) {
  const connected = ref(false)
  let client = null

  const NOTIFICATION_LABELS = {
    VILLAIN_HP_50_PERCENT: '¡Villano al 50% de HP!',
    VILLAIN_HP_25_PERCENT: '¡Villano al 25% de HP! ¡Casi derrotado!',
    THREAT_50_PERCENT: '¡Amenaza al 50%!',
    THREAT_75_PERCENT: '¡Amenaza al 75%! ¡Peligro!',
    THREAT_100_PERCENT: '¡Amenaza al 100%! Derrota por amenaza.',
    TIMER_30_MINUTES: 'Quedan 30 minutos',
    TIMER_15_MINUTES: 'Quedan 15 minutos',
    TIMER_5_MINUTES: '¡Solo quedan 5 minutos!',
    EVENT_FINISHED_PLAYERS_VICTORY: '¡Victoria! ¡El villano ha sido derrotado!',
    EVENT_FINISHED_THREAT_DEFEAT: 'Derrota — La amenaza ha alcanzado el límite',
    EVENT_FINISHED_TIMEOUT_DEFEAT: 'Derrota — Se agotó el tiempo',
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

          if (payload.notifications?.length) {
            for (const n of payload.notifications) {
              const label = NOTIFICATION_LABELS[n] || n
              const type = resolveNotificationType(n)
              notifStore.add(label, type, 8000)
            }
          }
        })

        client.subscribe(`/topic/events/${eventId}/notifications`, (msg) => {
          const payload = JSON.parse(msg.body)
          if (payload.notifications?.length) {
            for (const n of payload.notifications) {
              const label = NOTIFICATION_LABELS[n] || n
              const type = resolveNotificationType(n)
              notifStore.add(label, type, 8000)
            }
          } else if (payload.message) {
            notifStore.info(payload.message)
          }
        })

        client.subscribe(`/topic/events/${eventId}/game-finished`, (msg) => {
          const payload = JSON.parse(msg.body)
          gameStore.applySnapshot(payload.snapshot)
          const label = NOTIFICATION_LABELS[`EVENT_FINISHED_${payload.snapshot?.eventResult}`]
          if (label) notifStore.add(label, 'warning', 0)
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

  return { connected, connect, disconnect }
}
