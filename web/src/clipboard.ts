/**
 * Copies text to the clipboard and reports whether it got there.
 *
 * `navigator.clipboard` only exists in a secure context, so a deployment served over
 * plain HTTP on anything but localhost has no async clipboard at all; a frame embedded
 * without `clipboard-write` permission is refused for the same practical effect. The
 * legacy selection copy still works in both cases, so the async path falls through to
 * it instead of rejecting into a silent no-op.
 */
export async function copyText(text: string): Promise<boolean> {
  const clipboard: Clipboard | undefined = navigator.clipboard
  if (clipboard && typeof clipboard.writeText === 'function') {
    try {
      await clipboard.writeText(text)
      return true
    } catch {
      // Denied (insecure context, frame policy, unfocused document) — try the fallback.
    }
  }
  return copyWithSelection(text)
}

function copyWithSelection(text: string): boolean {
  const area = document.createElement('textarea')
  area.value = text
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.top = '0'
  area.style.left = '-9999px'
  const focused = document.activeElement
  document.body.appendChild(area)
  let copied = false
  try {
    area.select()
    area.setSelectionRange(0, area.value.length)
    copied = document.execCommand('copy')
  } catch {
    copied = false
  } finally {
    area.remove()
    if (focused instanceof HTMLElement && focused.isConnected) focused.focus()
  }
  return copied
}
