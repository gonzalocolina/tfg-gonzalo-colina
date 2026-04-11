import { defineStore } from 'pinia'
import { ref } from 'vue'

let nextId = 0

export const useNotificationsStore = defineStore('notifications', () => {
  const items = ref([])

  function add(message, type = 'info', timeout = 5000) {
    const id = ++nextId
    items.value.push({ id, message, type })
    if (timeout > 0) {
      setTimeout(() => remove(id), timeout)
    }
  }

  function remove(id) {
    items.value = items.value.filter((n) => n.id !== id)
  }

  function info(msg) {
    add(msg, 'info')
  }
  function success(msg) {
    add(msg, 'success')
  }
  function warning(msg) {
    add(msg, 'warning')
  }
  function error(msg) {
    add(msg, 'error', 8000)
  }

  return { items, add, remove, info, success, warning, error }
})
