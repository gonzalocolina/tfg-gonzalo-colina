import { createMemoryHistory, createRouter } from 'vue-router'

export function createTestRouter(routes) {
  return createRouter({
    history: createMemoryHistory(),
    routes,
  })
}
