import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import JoinView from '../JoinView.vue'

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/join', name: 'join', component: JoinView },
      { path: '/game', name: 'game', component: { template: '<div />' } },
    ],
  })
}

describe('JoinView', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
  })

  it('renders the join form', async () => {
    const router = makeRouter()
    await router.push('/join')
    await router.isReady()

    const wrapper = mount(JoinView, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    expect(wrapper.find('h1').text()).toContain('Marvel Champions')
    expect(wrapper.find('input#code').exists()).toBe(true)
    expect(wrapper.find('input#group-identifier').exists()).toBe(true)
    expect(wrapper.find('button').text()).toContain('Entrar')
  })

  it('disables button when the access code is missing', async () => {
    const router = makeRouter()
    await router.push('/join')
    await router.isReady()

    const wrapper = mount(JoinView, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    const btn = wrapper.find('button')
    expect(btn.attributes('disabled')).toBeDefined()

    await wrapper.find('#group-identifier').setValue('Mesa Uno')
    expect(btn.attributes('disabled')).toBeDefined()

    await wrapper.find('#code').setValue('EVT12345')
    expect(btn.attributes('disabled')).toBeUndefined()
  })
})
