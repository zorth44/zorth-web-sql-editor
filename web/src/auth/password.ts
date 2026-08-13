const SUFFIX_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'

function utf8Base64(value: string): string {
  const bytes = new TextEncoder().encode(value)
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return btoa(binary)
}

export function randomSuffix(length = 12): string {
  const bytes = new Uint8Array(length)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (value) => SUFFIX_CHARS[value % SUFFIX_CHARS.length]).join('')
}

export function encodeLdapPassword(password: string): string {
  return `${utf8Base64(password)}${randomSuffix(12)}`
}
