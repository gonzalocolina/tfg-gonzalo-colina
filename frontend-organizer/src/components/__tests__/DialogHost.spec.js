import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import DialogHost from '../DialogHost.vue'
import { useDialogs } from '@/composables/useDialogs'

const dialogs = useDialogs()

function findButtonByText(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text() === text)
}

describe('DialogHost', () => {
  afterEach(() => {
    dialogs.resetDialogs()
    document.body.innerHTML = ''
  })

  it('renders a confirmation dialog and resolves it when the user confirms', async () => {
    const wrapper = mount(DialogHost, {
      global: {
        stubs: {
          teleport: true,
        },
      },
    })

    const pendingConfirmation = dialogs.confirm({
      title: 'Eliminar evento',
      message: 'Se eliminará del panel definitivamente.',
      confirmLabel: 'Eliminar',
      tone: 'danger',
    })

    await flushPromises()

    expect(wrapper.text()).toContain('Eliminar evento')
    expect(wrapper.text()).toContain('Se eliminará del panel definitivamente.')

    const confirmButton = findButtonByText(wrapper, 'Eliminar')
    expect(confirmButton).toBeTruthy()

    await confirmButton.trigger('click')

    await expect(pendingConfirmation).resolves.toBe(true)
    wrapper.unmount()
  })

  it('queues dialogs and advances to the next one after dismissing the first', async () => {
    const wrapper = mount(DialogHost, {
      global: {
        stubs: {
          teleport: true,
        },
      },
    })

    const firstConfirmation = dialogs.confirm({
      title: 'Primero',
      message: 'Uno',
    })
    const secondConfirmation = dialogs.confirm({
      title: 'Segundo',
      message: 'Dos',
    })

    await flushPromises()
    expect(wrapper.text()).toContain('Primero')

    const cancelButton = findButtonByText(wrapper, 'Cancelar')
    expect(cancelButton).toBeTruthy()
    await cancelButton.trigger('click')

    await expect(firstConfirmation).resolves.toBe(false)

    await flushPromises()
    expect(wrapper.text()).toContain('Segundo')

    const confirmButton = findButtonByText(wrapper, 'Confirmar')
    expect(confirmButton).toBeTruthy()
    await confirmButton.trigger('click')

    await expect(secondConfirmation).resolves.toBe(true)
    wrapper.unmount()
  })
})
