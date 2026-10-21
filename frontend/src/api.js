export async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...options.headers }
  if (options.method && !['GET', 'HEAD'].includes(options.method)) {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' })
    if (!response.ok) throw new Error('Unable to secure your request. Refresh and try again.')
    const csrf = await response.json()
    headers[csrf.headerName] = csrf.token
  }
  const response = await fetch(path, { ...options, credentials: 'same-origin', headers })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    if (response.status === 401 && (!path.startsWith('/api/auth/') || ['/api/auth/me', '/api/auth/password'].includes(path))) {
      window.dispatchEvent(new Event('session-expired'))
    }
    throw new Error(error.message || 'Something went wrong. Please try again.')
  }
  return response.status === 204 ? null : response.json()
}
