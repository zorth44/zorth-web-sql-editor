import { marked, Renderer } from 'marked'
import type { Tokens } from 'marked'

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function safeHref(href: string): string | null {
  const trimmed = href.trim()
  if (/^https?:\/\//i.test(trimmed) || /^mailto:/i.test(trimmed)) return trimmed
  return null
}

class CopilotRenderer extends Renderer {
  html(): string {
    return ''
  }
  image(): string {
    return ''
  }
  link({ href, title, tokens }: Tokens.Link): string {
    const body = this.parser.parseInline(tokens)
    const safe = safeHref(href)
    if (!safe) return body
    const titleAttr = title ? ` title="${escapeHtml(title)}"` : ''
    return `<a href="${escapeHtml(safe)}" target="_blank" rel="noreferrer noopener"${titleAttr}>${body}</a>`
  }
}

const renderer = new CopilotRenderer()

export function renderAssistantMarkdown(markdown: string): string {
  return marked.parse(markdown, {
    async: false,
    gfm: true,
    breaks: true,
    renderer,
  }) as string
}
