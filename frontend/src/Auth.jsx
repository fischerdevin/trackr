import { useEffect, useState } from 'react'
import { api } from './api'

export default function Auth({ onLogin, onPasswordReset, initialMessage = '' }) {
  const [resetToken, setResetToken] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('reset-password'))
  const [mode, setMode] = useState(resetToken !== null ? 'reset' : 'login')
  const register = mode === 'register'
  const forgot = mode === 'forgot'
  const reset = mode === 'reset'
  const [googleEnabled, setGoogleEnabled] = useState(false)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [message, setMessage] = useState(initialMessage)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(new URLSearchParams(window.location.search).has('authError')
    ? 'Google sign-in failed. If you already have an account with this email, use your password.' : '')

  useEffect(() => {
    api('/api/auth/config').then(config => setGoogleEnabled(config.googleEnabled)).catch(() => {})
  }, [])

  useEffect(() => {
    const openResetLink = () => {
      const token = new URLSearchParams(window.location.hash.slice(1)).get('reset-password')
      if (token !== null) {
        setResetToken(token)
        setMode('reset')
        setError('')
        setMessage('')
        setPassword('')
        setConfirmation('')
      }
    }
    window.addEventListener('hashchange', openResetLink)
    return () => window.removeEventListener('hashchange', openResetLink)
  }, [])

  async function submit(event) {
    event.preventDefault()
    if (reset && password !== confirmation) {
      setError('Passwords do not match.')
      return
    }
    setBusy(true)
    setError('')
    setMessage('')
    try {
      if (forgot) {
        const response = await api('/api/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) })
        setMessage(response.message)
        return
      }
      if (reset) {
        const response = await api('/api/auth/reset-password', {
          method: 'POST', body: JSON.stringify({ token: resetToken, password }),
        })
        window.history.replaceState(null, '', '/')
        setPassword('')
        setConfirmation('')
        setMode('login')
        setMessage(response.message)
        onPasswordReset?.()
        return
      }
      if (register) {
        await api('/api/auth/register', { method: 'POST', body: JSON.stringify({ name, email, password }) })
        setMode('login')
      }
      await api('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ username: email, password }).toString(),
      })
      const user = await api('/api/auth/me')
      window.history.replaceState(null, '', '/')
      onLogin(user)
    } catch (err) {
      setError(err.message)
    } finally { setBusy(false) }
  }

  function switchMode(next) {
    setMode(next)
    setError('')
    setMessage('')
    setPassword('')
    setConfirmation('')
    if (resetToken !== null) {
      window.history.replaceState(null, '', '/')
      onPasswordReset?.()
    }
  }

  return <main className="auth-page">
    <section className="auth-card">
      <div className="brand"><img className="brand-logo" src="/trackr-logo.png" alt="" width="48" height="36" />trackr</div>
      <p className="eyebrow">YOUR NEXT OPPORTUNITY STARTS HERE</p>
      <h1>{forgot ? 'Forgot your password?' : reset ? 'Choose a new password' : register ? 'Create your account' : 'Welcome back'}</h1>
      <p className="subheading">{forgot ? 'Enter your email and we’ll send you a reset link. For Google accounts, continue with Google.' : reset ? 'Enter and confirm your new password to get back to your job search.' : 'Sign in to manage your internship applications.'}</p>
      {error && <p role="alert" className="error-banner">{error}</p>}
      {message && <p role="status" className="auth-notice">{message}</p>}
      <form className="auth-form" onSubmit={submit}>
        {register && <label>Name<input autoComplete="name" required maxLength={100} value={name} onChange={e => setName(e.target.value)} /></label>}
        {!reset && <label>Email<input type="email" autoComplete="username" required maxLength={254} value={email} onChange={e => setEmail(e.target.value)} /></label>}
        {!forgot && <label>{reset ? 'New password' : 'Password'}<input type="password" autoComplete={register || reset ? 'new-password' : 'current-password'} required minLength={register || reset ? 12 : undefined} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} /></label>}
        {reset && <label>Confirm new password<input type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={confirmation} onChange={e => setConfirmation(e.target.value)} /></label>}
        {(register || reset) && <small>Use at least 12 characters (up to 72 UTF-8 bytes).</small>}
        {mode === 'login' && <button type="button" className="forgot-password-link" disabled={busy} onClick={() => switchMode('forgot')}>Forgot password?</button>}
        <button disabled={busy} className="primary-button">{busy ? 'Please wait…' : forgot ? 'Send reset link' : reset ? 'Reset password' : register ? 'Create account' : 'Sign in'}</button>
      </form>
      {googleEnabled && !reset && <a className="secondary-button google-button" href="/oauth2/authorization/google">Continue with Google</a>}
      {reset && <button className="auth-switch" disabled={busy} onClick={() => switchMode('forgot')}>Need a new reset link?</button>}
      <button className="auth-switch" disabled={busy} onClick={() => switchMode(mode === 'login' ? 'register' : 'login')}>
        {forgot || reset ? 'Back to sign in' : register ? 'Already have an account? Sign in' : 'New to Trackr? Create an account'}
      </button>
    </section>
  </main>
}
