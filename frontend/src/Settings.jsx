import { useEffect, useId, useRef, useState } from 'react'
import { Check, LockKeyhole, UserRound, X } from 'lucide-react'
import { api } from './api'

export default function Settings({ user, onUpdate, onRequireLogin, onClose }) {
  const dialog = useRef(null)
  const titleId = useId()
  const descriptionId = useId()
  const [section, setSection] = useState('profile')
  const [name, setName] = useState(user.name)
  const [email, setEmail] = useState(user.email)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const emailChanged = email.trim().toLowerCase() !== user.email
  const profileChanged = name.trim() !== user.name || emailChanged
  const editable = user.credentialsEditable === true
  const lockedMessage = user.provider === 'GOOGLE'
    ? 'Your email and password are managed by Google. You can still update your Trackr name here.'
    : 'This is a shared demo account. Its email and password are locked, but you can update the display name.'

  useEffect(() => {
    const element = dialog.current
    const previousFocus = document.activeElement
    const previousOverflow = document.body.style.overflow
    element.showModal()
    document.body.style.overflow = 'hidden'
    return () => {
      element.close()
      document.body.style.overflow = previousOverflow
      if (previousFocus?.isConnected) previousFocus.focus()
    }
  }, [])

  function switchSection(next) {
    setSection(next)
    setCurrentPassword('')
    setNewPassword('')
    setConfirmation('')
    setError('')
    setMessage('')
  }

  async function saveProfile(event) {
    event.preventDefault()
    if (busy || !profileChanged) return
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const result = await api('/api/auth/me', {
        method: 'PATCH',
        body: JSON.stringify({ name: name.trim(), email: email.trim(), ...(emailChanged ? { currentPassword } : {}) }),
      })
      setCurrentPassword('')
      if (result.requiresLogin) {
        onRequireLogin('Your email has been updated. Sign in with your new email and existing password.')
        return
      }
      onUpdate(result.user)
      setName(result.user.name)
      setEmail(result.user.email)
      setMessage('Your profile has been updated.')
    } catch (err) { setError(err.message) }
    finally { setBusy(false) }
  }

  async function savePassword(event) {
    event.preventDefault()
    if (busy) return
    setError('')
    setMessage('')
    if (newPassword !== confirmation) {
      setError('Passwords do not match.')
      return
    }
    if (new TextEncoder().encode(newPassword).length > 72) {
      setError('Password must be at most 72 UTF-8 bytes.')
      return
    }
    setBusy(true)
    try {
      const result = await api('/api/auth/password', {
        method: 'PUT', body: JSON.stringify({ currentPassword, newPassword }),
      })
      setCurrentPassword('')
      setNewPassword('')
      setConfirmation('')
      onRequireLogin(result.message)
    } catch (err) { setError(err.message) }
    finally { setBusy(false) }
  }

  return <dialog ref={dialog} className="settings-dialog" aria-labelledby={titleId} aria-describedby={descriptionId}
    onCancel={event => { event.preventDefault(); if (!busy) onClose() }}
    onClick={event => {
      if (event.target !== event.currentTarget || busy) return
      const bounds = event.currentTarget.getBoundingClientRect()
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) onClose()
    }}>
    <header className="settings-header">
      <div><p className="eyebrow">YOUR ACCOUNT</p><h2 id={titleId}>Settings</h2><p id={descriptionId}>Make Trackr feel like you.</p></div>
      <button className="close-button" type="button" aria-label="Close settings" disabled={busy} onClick={onClose}><X size={20} /></button>
    </header>
    <div className="settings-account"><span className="avatar">{user.name.slice(0, 2).toUpperCase()}</span><div><strong>{user.name}</strong><span>{user.email}</span></div><span className="settings-provider">{user.provider === 'GOOGLE' ? 'Google account' : editable ? 'Email account' : 'Demo account'}</span></div>
    <nav className="settings-nav" aria-label="Account settings">
      <button type="button" aria-pressed={section === 'profile'} disabled={busy} onClick={() => switchSection('profile')}><UserRound size={16} aria-hidden="true" />Profile</button>
      <button type="button" aria-pressed={section === 'security'} disabled={busy} onClick={() => switchSection('security')}><LockKeyhole size={16} aria-hidden="true" />Password & security</button>
    </nav>
    <div className="settings-body">
      {error && <p role="alert" className="error-banner">{error}</p>}
      {message && <p role="status" className="auth-notice">{message}</p>}
      {section === 'profile' ? <form className="settings-form" onSubmit={saveProfile}>
        <fieldset disabled={busy}>
          <label>Display name<input autoComplete="name" required maxLength={100} value={name} onChange={event => setName(event.target.value)} /></label>
          <label>Email address<input type="email" autoComplete="username" required maxLength={254} readOnly={!editable} aria-describedby={!editable ? 'settings-credentials-note' : undefined} value={email} onChange={event => setEmail(event.target.value)} /></label>
          {!editable && <p className="settings-note" id="settings-credentials-note"><LockKeyhole size={16} aria-hidden="true" />{lockedMessage}</p>}
          {emailChanged && editable && <>
            <label>Current password<input type="password" autoComplete="current-password" required maxLength={72} value={currentPassword} onChange={event => setCurrentPassword(event.target.value)} /></label>
            <p className="settings-help">Changing your email signs you out on all devices. Use an email address you can access.</p>
          </>}
        </fieldset>
        <footer className="settings-footer"><button type="button" className="secondary-button" disabled={busy} onClick={onClose}>Close</button><button type="submit" className="primary-button" disabled={busy || !profileChanged}><Check size={16} aria-hidden="true" />{busy ? 'Saving…' : 'Save changes'}</button></footer>
      </form> : <>
        <h3>Password & security</h3>
        {editable ? <form className="settings-form" onSubmit={savePassword}>
          <p className="settings-help">Choose a unique password. Updating it signs you out on all devices.</p>
          <fieldset disabled={busy}>
            <label>Current password<input type="password" autoComplete="current-password" required maxLength={72} value={currentPassword} onChange={event => setCurrentPassword(event.target.value)} /></label>
            <label>New password<input type="password" autoComplete="new-password" required minLength={12} maxLength={72} aria-describedby="settings-password-help" value={newPassword} onChange={event => setNewPassword(event.target.value)} /></label>
            <label>Confirm new password<input type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={confirmation} onChange={event => setConfirmation(event.target.value)} /></label>
            <p className="settings-help" id="settings-password-help">Use at least 12 characters (up to 72 UTF-8 bytes).</p>
          </fieldset>
          <footer className="settings-footer"><button type="button" className="secondary-button" disabled={busy} onClick={onClose}>Close</button><button type="submit" className="primary-button" disabled={busy}><LockKeyhole size={16} aria-hidden="true" />{busy ? 'Updating…' : 'Update password'}</button></footer>
        </form> : <p className="settings-note"><LockKeyhole size={16} aria-hidden="true" />{lockedMessage}</p>}
      </>}
    </div>
  </dialog>
}
