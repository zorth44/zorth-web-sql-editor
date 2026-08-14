export const SIDEBAR_MIN_PX = 220
export const SIDEBAR_MAX_PX = 480
export const SIDEBAR_DEFAULT_PX = 280

const ROW_CHROME_PX = 72

function charWidth(char: string, fontSize: number): number {
  return /[\u1100-\uFFFD]/.test(char) ? fontSize : fontSize * 0.62
}

function textWidth(text: string, fontSize: number): number {
  let width = 0
  for (const char of text) width += charWidth(char, fontSize)
  return width
}

export function fitSidebarWidth(sources: { name: string; host: string; port: number }[]): number {
  if (!sources.length) return SIDEBAR_DEFAULT_PX
  let widest = 0
  for (const source of sources) {
    const row =
      ROW_CHROME_PX + textWidth(source.name, 12) + textWidth(`${source.host}:${source.port}`, 10)
    widest = Math.max(widest, row)
  }
  return Math.min(SIDEBAR_MAX_PX, Math.max(SIDEBAR_MIN_PX, Math.ceil(widest)))
}

export function pxToPanePercent(px: number, containerPx: number): number {
  if (containerPx <= 0) return 0
  return (px / containerPx) * 100
}
