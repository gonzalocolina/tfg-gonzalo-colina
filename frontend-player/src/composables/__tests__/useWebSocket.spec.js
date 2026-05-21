import { describe, expect, it } from 'vitest'
import { isRealtimeEventRelevantToPod, mapNotificationLabel, resolveNotificationType } from '../useWebSocket'

describe('useWebSocket notification helpers', () => {
  it('maps inter-pod notifications to user-friendly labels', () => {
    expect(mapNotificationLabel('INTER_POD_CARD_CREATED')).toBe('Se ha creado una carta inter-nodo en tu pod.')
    expect(mapNotificationLabel('INTER_POD_CARD_STATUS_UPDATED')).toBe(
      'Se ha actualizado el estado de una carta inter-nodo de tu pod.',
    )
  })

  it('marks inter-pod notifications as informational', () => {
    expect(resolveNotificationType('INTER_POD_CARD_DAMAGED')).toBe('info')
    expect(resolveNotificationType('EVENT_FINISHED_PLAYERS_VICTORY')).toBe('success')
    expect(mapNotificationLabel('CUSTOM_EVENT')).toBe('CUSTOM_EVENT')
  })

  it('matches pod-scoped realtime events only for the target pod', () => {
    expect(isRealtimeEventRelevantToPod({ targetPodId: 7 }, 7)).toBe(true)
    expect(isRealtimeEventRelevantToPod({ targetPodId: 7 }, 8)).toBe(false)
    expect(isRealtimeEventRelevantToPod({ targetPodId: 7 }, null)).toBe(false)
    expect(isRealtimeEventRelevantToPod({ operation: 'EVENT_STRUCTURE_UPDATED' }, null)).toBe(true)
  })
})
