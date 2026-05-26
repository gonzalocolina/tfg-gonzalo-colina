import { createPinia, setActivePinia } from 'pinia'

export function createActivePinia() {
  const pinia = createPinia()
  setActivePinia(pinia)
  return pinia
}
