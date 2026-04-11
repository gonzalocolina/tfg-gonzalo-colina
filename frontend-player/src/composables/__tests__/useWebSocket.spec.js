import { describe, expect, it } from 'vitest'
import { mapNotificationLabel, resolveNotificationType } from '../useWebSocket'

describe('useWebSocket notification helpers', () => {
  it('maps inter-pod notifications to user-friendly labels', () => {
    expect(mapNotificationLabel('INTER_POD_CARD_CREATED')).toBe('Se ha creado una carta inter-pod en tu pod.')
    expect(mapNotificationLabel('INTER_POD_CARD_STATUS_UPDATED')).toBe(
      'Se ha actualizado el estado de una carta inter-pod de tu pod.',
    )
  })

  it('marks inter-pod notifications as informational', () => {
    expect(resolveNotificationType('INTER_POD_CARD_DAMAGED')).toBe('info')
    expect(resolveNotificationType('EVENT_FINISHED_PLAYERS_VICTORY')).toBe('success')
    expect(mapNotificationLabel('CUSTOM_EVENT')).toBe('CUSTOM_EVENT')
  })
})
