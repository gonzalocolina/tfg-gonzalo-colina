import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import EventLogView from '@/views/EventLogView.vue'

const {
  getEventLogs,
  revertEventLog,
  getEventStructure,
  success,
  error,
} = vi.hoisted(() => ({
  getEventLogs: vi.fn(),
  revertEventLog: vi.fn(),
  getEventStructure: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { eventId: '42' },
  }),
}))

vi.mock('@/api/logs', () => ({
  getEventLogs,
  revertEventLog,
}))

vi.mock('@/api/setup', () => ({
  getEventStructure,
}))

vi.mock('@/stores/notifications', () => ({
  useNotificationsStore: () => ({
    success,
    error,
  }),
}))

describe('EventLogView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getEventStructure.mockResolvedValue({
      data: {
        pods: [
          {
            name: 'Pod Norte',
            groups: [
              {
                id: 10,
                identifier: 'Mesa Alfa',
              },
            ],
          },
        ],
        unassignedGroups: [],
      },
    })
  })

  it('allows reverting a reversible log entry and refreshes the list', async () => {
    getEventLogs
      .mockResolvedValueOnce({
        data: [
          {
            id: 101,
            timestamp: '2026-05-21T10:00:00Z',
            actor: 'rep-a',
            groupIdentifier: 'Mesa Alfa',
            actionType: 'VILLAIN_DAMAGE_REPORTED',
            revertedLogId: null,
            previousValue: '40',
            newValue: '35',
            notes: 'Daño reportado.',
          },
        ],
      })
      .mockResolvedValueOnce({
        data: [
          {
            id: 202,
            timestamp: '2026-05-21T10:01:00Z',
            actor: 'organizer',
            groupIdentifier: 'Mesa Alfa',
            actionType: 'VILLAIN_DAMAGE_REVERTED',
            revertedLogId: 101,
            previousValue: '35',
            newValue: '40',
            notes: 'Reversión del log.',
          },
          {
            id: 101,
            timestamp: '2026-05-21T10:00:00Z',
            actor: 'rep-a',
            groupIdentifier: 'Mesa Alfa',
            actionType: 'VILLAIN_DAMAGE_REPORTED',
            revertedLogId: null,
            previousValue: '40',
            newValue: '35',
            notes: 'Daño reportado.',
          },
        ],
      })
    revertEventLog.mockResolvedValue({ data: {} })

    const wrapper = mount(EventLogView)
    await flushPromises()

    await wrapper.get('[data-testid="revert-log-101"]').trigger('click')
    await flushPromises()

    expect(revertEventLog).toHaveBeenCalledWith('42', 101, {})
    expect(getEventLogs).toHaveBeenCalledTimes(2)
    expect(success).toHaveBeenCalledWith('Registro revertido correctamente')
    expect(wrapper.text()).toContain('Ya revertido')
  })
})