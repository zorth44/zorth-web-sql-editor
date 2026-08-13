import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface NotificationItem {
  id: string
  kind: 'success' | 'error' | 'info'
  message: string
}
export const useNotificationsStore = defineStore('notifications', () => {
  const items = ref<NotificationItem[]>([])
  function push(kind: NotificationItem['kind'], message: string): void {
    const id = crypto.randomUUID()
    items.value.push({ id, kind, message })
    window.setTimeout(() => remove(id), 4500)
  }
  function remove(id: string): void {
    items.value = items.value.filter((item) => item.id !== id)
  }
  return { items, push, remove }
})
