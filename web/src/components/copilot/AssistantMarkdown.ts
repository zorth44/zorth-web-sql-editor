import { defineComponent, h, type VNodeChild } from 'vue'
import { renderAssistantMarkdown } from '@/sql-editor/copilot-markdown'

const tags = new Set([
  'p',
  'br',
  'strong',
  'em',
  'del',
  'code',
  'pre',
  'blockquote',
  'ul',
  'ol',
  'li',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'hr',
  'table',
  'thead',
  'tbody',
  'tr',
  'th',
  'td',
  'a',
])
function renderNode(node: Node): VNodeChild {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent || ''
  if (!(node instanceof Element)) return null
  const tag = node.tagName.toLowerCase()
  const children = Array.from(node.childNodes).map(renderNode)
  if (!tags.has(tag)) return children
  const attributes: Record<string, string> = {}
  const href = node.getAttribute('href') || ''
  if (tag === 'a' && /^(https?:\/\/|mailto:)/i.test(href)) {
    attributes.href = href
    attributes.target = '_blank'
    attributes.rel = 'noreferrer noopener'
  }
  return h(tag, attributes, children)
}
/** Render the existing Markdown subset with Vue nodes and an explicit tag/attribute allowlist. */
export default defineComponent({
  props: { text: { type: String, required: true } },
  setup(props) {
    return () => {
      const document = new DOMParser().parseFromString(
        renderAssistantMarkdown(props.text),
        'text/html',
      )
      return h('div', Array.from(document.body.childNodes).map(renderNode))
    }
  },
})
