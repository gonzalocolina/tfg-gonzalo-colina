import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { useProtectedImageUrls } from '../useProtectedImageUrls'

const originalCreateObjectURL = URL.createObjectURL
const originalRevokeObjectURL = URL.revokeObjectURL
const originalFetch = globalThis.fetch

function mountComposable(sessionStorageKey = 'auth-player') {
  let composableApi

  const wrapper = mount(defineComponent({
    setup() {
      composableApi = useProtectedImageUrls(sessionStorageKey)
      return () => h('div')
    },
  }))

  return { wrapper, composableApi }
}

describe('useProtectedImageUrls', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    sessionStorage.setItem('auth-player', JSON.stringify({ accessToken: 'tok-123' }))
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      blob: () => Promise.resolve(new Blob(['image'], { type: 'image/png' })),
    })
    URL.createObjectURL = vi.fn(() => 'blob:secure-image')
    URL.revokeObjectURL = vi.fn()
  })

  it('loads protected images with the bearer token and exposes a blob url', async () => {
    const { composableApi } = mountComposable()

    await composableApi.syncImageUrls([{ id: 7, url: '/api/events/1/inter-pod-cards/7/image' }])

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/events/1/inter-pod-cards/7/image', {
      headers: {
        Authorization: 'Bearer tok-123',
      },
    })
    expect(composableApi.getProtectedImageUrl(7)).toBe('blob:secure-image')
  })

  it('revokes stale blob urls when images disappear from the tracked collection', async () => {
    const { composableApi } = mountComposable()

    await composableApi.syncImageUrls([{ id: 7, url: '/api/events/1/inter-pod-cards/7/image' }])
    await composableApi.syncImageUrls([])

    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:secure-image')
    expect(composableApi.getProtectedImageUrl(7)).toBe(null)
  })
})
