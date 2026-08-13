let username = ''
let password = ''

export function retainCredentials(nextUsername: string, nextPassword: string): void {
  username = nextUsername
  password = nextPassword
}
export function consumeCredentials(): { username: string; password: string } | null {
  return username && password ? { username, password } : null
}
export function clearCredentials(): void {
  username = ''
  password = ''
}
