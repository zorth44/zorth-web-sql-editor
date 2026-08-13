<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
const props = withDefaults(
  defineProps<{ open: boolean; title: string; busy?: boolean; confirmDisabled?: boolean }>(),
  { busy: false, confirmDisabled: false },
)
const emit = defineEmits<{ close: []; confirm: [] }>()
const panel = ref<HTMLElement | null>(null)
let previous: HTMLElement | null = null
watch(
  () => props.open,
  async (open) => {
    if (open) {
      previous = document.activeElement as HTMLElement
      await nextTick()
      panel.value?.querySelector<HTMLElement>('input,button')?.focus()
    } else previous?.focus()
  },
)
onBeforeUnmount(() => previous?.focus())
function keydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && !props.busy) emit('close')
  if (event.key !== 'Tab' || !panel.value) return
  const items = [
    ...panel.value.querySelectorAll<HTMLElement>(
      'button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href]',
    ),
  ]
  const first = items[0],
    last = items.at(-1)
  if (!first || !last) return
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
</script>
<template>
  <Teleport to="body"
    ><div
      v-if="open"
      class="fixed inset-0 z-40 grid place-items-center bg-slate-950/45 p-6"
      @keydown="keydown"
    >
      <section
        ref="panel"
        class="w-full max-w-md rounded-xl bg-white p-6 shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
      >
        <h2 id="dialog-title" class="text-lg font-semibold">{{ title }}</h2>
        <div class="mt-4"><slot /></div>
        <div class="mt-6 flex justify-end gap-3">
          <button class="btn" type="button" :disabled="busy" @click="$emit('close')">取消</button
          ><button
            class="btn-danger"
            type="button"
            :disabled="busy || confirmDisabled"
            @click="$emit('confirm')"
          >
            {{ busy ? '处理中…' : '确认' }}
          </button>
        </div>
      </section>
    </div></Teleport
  >
</template>
