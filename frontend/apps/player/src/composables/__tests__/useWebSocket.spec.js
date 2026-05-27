import { describe, expect, it } from 'vitest'
import {
  isFinalResultNotification,
  isRealtimeEventRelevantToPod,
  mapNotificationLabel,
  resolveNotificationTimeout,
  resolveNotificationType,
} from '../useWebSocket'

describe('useWebSocket notification helpers', () => {
  it('maps inter-pod notifications to user-friendly labels', () => {
    expect(mapNotificationLabel('INTER_POD_CARD_CREATED')).toBe('Se ha creado una carta inter-nodo en tu pod.')
    expect(mapNotificationLabel('INTER_POD_CARD_STATUS_UPDATED')).toBe('Se ha actualizado el estado de una carta inter-nodo de tu pod.')
    expect(mapNotificationLabel('INTER_POD_CARD_THREAT_REDUCED')).toBe('Se ha actualizado la amenaza de una carta inter-nodo de tu pod.')
  })

  it('resolves severity and timing without exposing raw codes', () => {
    expect(resolveNotificationType('INTER_POD_CARD_DAMAGED')).toBe('info')
    expect(resolveNotificationType('EVENT_FINISHED_PLAYERS_VICTORY')).toBe('success')
    expect(resolveNotificationTimeout('EVENT_FINISHED_PLAYERS_VICTORY')).toBe(12000)
    expect(isFinalResultNotification('EVENT_FINISHED_PLAYERS_VICTORY')).toBe(true)
    expect(mapNotificationLabel('CUSTOM_EVENT')).toBe('Se ha producido una actualización de la partida.')
  })

  it('matches pod-scoped realtime events only for the target pod', () => {
    expect(isRealtimeEventRelevantToPod({ targetPodId: 7 }, 7)).toBe(true)
    expect(isRealtimeEventRelevantToPod({ targetPodId: 7 }, 8)).toBe(false)
    expect(isRealtimeEventRelevantToPod({ targetPodId: 7 }, null)).toBe(false)
    expect(isRealtimeEventRelevantToPod({ operation: 'EVENT_STRUCTURE_UPDATED' }, null)).toBe(true)
  })
})
