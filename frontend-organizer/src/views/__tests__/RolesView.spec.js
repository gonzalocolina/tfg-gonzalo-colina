import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import RolesView from '../RolesView.vue'

const { routerReplace } = vi.hoisted(() => ({
  routerReplace: vi.fn(),
}))

const { getEvent } = vi.hoisted(() => ({
  getEvent: vi.fn(),
}))

const {
  getCoOrganizerInvitations,
  getCoOrganizers,
  inviteCoOrganizer,
  revokeCoOrganizerInvitation,
} = vi.hoisted(() => ({
  getCoOrganizerInvitations: vi.fn(),
  getCoOrganizers: vi.fn(),
  inviteCoOrganizer: vi.fn(),
  revokeCoOrganizerInvitation: vi.fn(),
}))

const { notifSuccess, notifError } = vi.hoisted(() => ({
  notifSuccess: vi.fn(),
  notifError: vi.fn(),
}))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRoute: () => ({ params: { eventId: '42' } }),
    useRouter: () => ({ replace: routerReplace }),
  }
})

vi.mock('@/api/events', () => ({
  getEvent,
}))

vi.mock('@/api/roles', () => ({
  getCoOrganizerInvitations,
  getCoOrganizers,
  inviteCoOrganizer,
  revokeCoOrganizerInvitation,
}))

vi.mock('@/stores/notifications', () => ({
  useNotificationsStore: () => ({
    success: notifSuccess,
    error: notifError,
  }),
}))

function buildEvent(status) {
  return {
    id: 42,
    name: 'Evento demo',
    status,
    currentUserIsOwner: true,
  }
}

function buildCoOrganizer(overrides = {}) {
  return {
    userId: 7,
    username: 'coorg-demo',
    email: 'coorg@example.com',
    responsibilities: ['THREAT'],
    invitationId: 99,
    ...overrides,
  }
}

function findButtonByText(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text() === text)
}

describe('RolesView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('confirm', vi.fn(() => true))
    getCoOrganizerInvitations.mockResolvedValue({ data: [] })
    getCoOrganizers.mockResolvedValue({ data: [] })
    getEvent.mockResolvedValue({ data: buildEvent('DRAFT') })
    inviteCoOrganizer.mockResolvedValue({})
    revokeCoOrganizerInvitation.mockResolvedValue({})
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('allows revoking accepted co-organizers before the game starts', async () => {
    getCoOrganizers.mockResolvedValueOnce({ data: [buildCoOrganizer()] })

    const wrapper = mount(RolesView)
    await flushPromises()

    const revokeButton = findButtonByText(wrapper, 'Revocar acceso')
    expect(revokeButton).toBeTruthy()
    expect(revokeButton.attributes('disabled')).toBeUndefined()

    await revokeButton.trigger('click')
    await flushPromises()

    expect(revokeCoOrganizerInvitation).toHaveBeenCalledWith('42', 99)
    expect(notifSuccess).toHaveBeenCalledWith('Co-organizador revocado')
  })

  it('disables accepted-invitation revocation once the game has started', async () => {
    getEvent.mockResolvedValueOnce({ data: buildEvent('IN_PROGRESS') })
    getCoOrganizers.mockResolvedValueOnce({ data: [buildCoOrganizer()] })

    const wrapper = mount(RolesView)
    await flushPromises()

    const revokeButton = findButtonByText(wrapper, 'Revocar acceso')
    expect(revokeButton).toBeTruthy()
    expect(revokeButton.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('solo se pueden revocar antes de iniciar la partida')
  })
})
