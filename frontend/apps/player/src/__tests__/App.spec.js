import { describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import App from '../App.vue'
import { createActivePinia } from '../../tests/utils/createActivePinia.js'
import { createTestRouter } from '../../tests/utils/createTestRouter.js'

const appRoutes = [
  { path: '/join', name: 'join', component: { template: '<div>Join</div>' } },
  { path: '/game', name: 'game', component: { template: '<div>Game</div>' } },
]

async function mountApp(initialPath = '/join') {
  const pinia = createActivePinia()
  const router = createTestRouter(appRoutes)

  await router.push(initialPath)
  await router.isReady()

  const wrapper = mount(App, {
    global: {
      plugins: [pinia, router],
    },
  })

  await flushPromises()
  return wrapper
}

describe('Player App', () => {
  it('renders the current route inside the shell and keeps the toast host mounted', async () => {
    const wrapper = await mountApp('/join')

    expect(wrapper.text()).toContain('Join')
    expect(wrapper.find('.app-shell').exists()).toBe(true)
    expect(wrapper.find('.toast-container').exists()).toBe(true)

    wrapper.unmount()
  })
})
