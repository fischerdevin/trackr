import { useEffect, useId, useMemo, useRef, useState } from 'react'
import Auth from './Auth'
import Calendar from './Calendar.jsx'
import Settings from './Settings.jsx'
import { api } from './api'
import { applicationStreak, localDateKey } from './streak'
import {
  BriefcaseBusiness, CalendarDays, Check, ChevronDown, CirclePlus, ExternalLink,
  Flame, LayoutDashboard, ListFilter, LogOut, MapPin, Pencil, Search, Settings as SettingsIcon, Trash2, X,
} from 'lucide-react'

const STATUSES = ['SAVED', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN']

const emptyApplication = {
  companyName: '', jobTitle: '', status: 'SAVED', location: '', jobUrl: '',
  dateApplied: '', interviewDate: '',
}

const statusLabels = {
  SAVED: 'Saved', APPLIED: 'Applied', INTERVIEW: 'Interview', OFFER: 'Offer',
  REJECTED: 'Rejected', WITHDRAWN: 'Withdrawn',
}

function App() {
  const [resetFlow, setResetFlow] = useState(() => new URLSearchParams(window.location.hash.slice(1)).has('reset-password'))
  const [user, setUser] = useState(null)
  const [checking, setChecking] = useState(true)
  const [error, setError] = useState('')
  const [loggingOut, setLoggingOut] = useState(false)
  const [showSettings, setShowSettings] = useState(false)
  const [authMessage, setAuthMessage] = useState('')

  useEffect(() => {
    api('/api/auth/me').then(setUser).catch(() => {}).finally(() => setChecking(false))
    const expire = () => { setUser(null); setShowSettings(false) }
    const updateResetFlow = () => setResetFlow(new URLSearchParams(window.location.hash.slice(1)).has('reset-password'))
    window.addEventListener('session-expired', expire)
    window.addEventListener('hashchange', updateResetFlow)
    return () => {
      window.removeEventListener('session-expired', expire)
      window.removeEventListener('hashchange', updateResetFlow)
    }
  }, [])
  async function logout() {
    if (loggingOut) return
    setLoggingOut(true)
    try {
      await api('/api/auth/logout', { method: 'POST' })
      setUser(null)
      setShowSettings(false)
      setError('')
    } catch (err) { setError(err.message) }
    finally { setLoggingOut(false) }
  }
  const login = (profile) => { setUser(profile); setAuthMessage(''); setShowSettings(false) }
  const requireLogin = (message) => {
    setShowSettings(false)
    setUser(null)
    setError('')
    setAuthMessage(message)
  }
  const finishPasswordReset = () => { setUser(null); setResetFlow(false) }
  if (resetFlow) return <Auth onLogin={login} onPasswordReset={finishPasswordReset} />
  if (checking) return <div className="empty-state">Loading your account…</div>
  if (!user) return <Auth onLogin={login} onPasswordReset={finishPasswordReset} initialMessage={authMessage} />
  return <>
    {error && <div role="alert" className="error-banner">{error}</div>}
    <Dashboard key={user.id} user={user} onLogout={logout} loggingOut={loggingOut} onSettings={() => setShowSettings(true)} />
    {showSettings && <Settings user={user} onUpdate={setUser} onRequireLogin={requireLogin} onClose={() => setShowSettings(false)} />}
  </>
}

function AccountMenu({ user, onLogout, loggingOut, onSettings }) {
  const [open, setOpen] = useState(false)
  const container = useRef(null)
  const trigger = useRef(null)
  const popupId = useId()

  useEffect(() => {
    if (!open) return
    const dismiss = (event) => {
      if (!container.current?.contains(event.target)) setOpen(false)
    }
    document.addEventListener('pointerdown', dismiss)
    return () => document.removeEventListener('pointerdown', dismiss)
  }, [open])

  return <div className="account-menu" ref={container}
    onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false) }}
    onKeyDown={(event) => {
      if (event.key === 'Escape') { setOpen(false); trigger.current?.focus() }
    }}>
    <button className="profile-trigger" type="button" ref={trigger}
      aria-label={`Account options for ${user.name}`} aria-expanded={open} aria-controls={popupId}
      onClick={() => setOpen(!open)}>
      <span className="avatar">{user.name.slice(0, 2).toUpperCase()}</span>
      <span className="profile-details"><strong>{user.name}</strong><span>Job seeker</span></span>
      <ChevronDown size={16} aria-hidden="true" className={open ? 'profile-chevron open' : 'profile-chevron'} />
    </button>
    {open && <div className="profile-popup" id={popupId}>
      <span className="profile-email">{user.email}</span>
      <button type="button" className="settings-menu-item" disabled={loggingOut} onClick={() => {
        trigger.current?.focus()
        setOpen(false)
        onSettings()
      }}>
        <SettingsIcon size={16} aria-hidden="true" />Settings
      </button>
      <button type="button" onClick={onLogout} disabled={loggingOut}>
        <LogOut size={16} aria-hidden="true" />{loggingOut ? 'Signing out…' : 'Sign out'}
      </button>
    </div>}
  </div>
}

function Dashboard({ user, onLogout, loggingOut, onSettings }) {
  const applicationsSection = useRef(null)
  const calendarSection = useRef(null)
  const [activeSection, setActiveSection] = useState('dashboard')
  const [applications, setApplications] = useState([])
  const [today, setToday] = useState(localDateKey)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(emptyApplication)

  const loadApplications = async () => {
    setLoading(true)
    try {
      setError('')
      const data = await api('/api/applications')
      setApplications(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadApplications() }, [])

  useEffect(() => {
    const refreshDate = () => setToday(localDateKey())
    const timer = window.setInterval(refreshDate, 60_000)
    window.addEventListener('focus', refreshDate)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', refreshDate)
    }
  }, [])

  const streak = applicationStreak(applications, today)

  const navigateTo = (section) => {
    setActiveSection(section)
    const behavior = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth'
    if (section === 'calendar') {
      calendarSection.current?.scrollIntoView({ behavior, block: 'start' })
    } else if (section === 'applications') {
      applicationsSection.current?.scrollIntoView({ behavior, block: 'start' })
    } else {
      window.scrollTo({ top: 0, behavior })
    }
  }

  const filteredApplications = useMemo(() => applications.filter((application) => {
    const matchesStatus = statusFilter === 'ALL' || application.status === statusFilter
    const haystack = `${application.companyName} ${application.jobTitle} ${application.location || ''}`.toLowerCase()
    return matchesStatus && haystack.includes(query.toLowerCase())
  }), [applications, query, statusFilter])

  const counts = applications.reduce((result, application) => {
    result[application.status] = (result[application.status] || 0) + 1
    return result
  }, {})

  const openNew = () => {
    setEditing(null)
    setForm(emptyApplication)
    setShowForm(true)
  }

  const openEdit = (application) => {
    setEditing(application.id)
    setForm({ ...emptyApplication, ...application })
    setShowForm(true)
  }

  const closeForm = () => { setShowForm(false); setEditing(null) }

  const handleSubmit = async (event) => {
    event.preventDefault()
    try {
      setError('')
      const saved = await api(editing ? `/api/applications/${editing}` : '/api/applications', {
        method: editing ? 'PUT' : 'POST',
        body: JSON.stringify({
          ...form,
          dateApplied: !editing && form.status === 'APPLIED' && !form.dateApplied
            ? localDateKey() : form.dateApplied || null,
          interviewDate: form.interviewDate || null,
        }),
      })
      setApplications((current) => editing
        ? current.map((item) => item.id === editing ? saved : item)
        : [saved, ...current])
      closeForm()
    } catch (err) { setError(err.message) }
  }

  const handleDelete = async (id) => {
    if (!window.confirm('Delete this application?')) return
    try {
      await api(`/api/applications/${id}`, { method: 'DELETE' })
      setApplications((current) => current.filter((item) => item.id !== id))
    } catch (err) { setError(err.message) }
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand"><img className="brand-logo" src="/trackr-logo.png" alt="" width="48" height="36" /><span>trackr</span></div>
        <nav className="main-nav">
          <button className={`nav-item${activeSection === 'dashboard' ? ' active' : ''}`} aria-current={activeSection === 'dashboard' ? 'location' : undefined} onClick={() => navigateTo('dashboard')}><LayoutDashboard size={18} /> Dashboard</button>
          <button className={`nav-item${activeSection === 'applications' ? ' active' : ''}`} aria-current={activeSection === 'applications' ? 'location' : undefined} onClick={() => navigateTo('applications')}><BriefcaseBusiness size={18} /> Applications <span className="nav-count">{applications.length}</span></button>
          <button className={`nav-item${activeSection === 'calendar' ? ' active' : ''}`} aria-current={activeSection === 'calendar' ? 'location' : undefined} onClick={() => navigateTo('calendar')}><CalendarDays size={18} /> Calendar</button>
        </nav>
        <div className="sidebar-bottom">
          <div className="tip-card"><span className="tip-icon">✦</span><p><strong>Keep it moving</strong><br />Small progress every day adds up.</p></div>
          <AccountMenu user={user} onLogout={onLogout} loggingOut={loggingOut} onSettings={onSettings} />
        </div>
      </aside>

      <div className="mobile-account-menu"><AccountMenu user={user} onLogout={onLogout} loggingOut={loggingOut} onSettings={onSettings} /></div>

      <main className="content">
        <header className="topbar"><span className="workspace-title">Your search at a glance</span><div className="top-actions"><span className="date-label"><CalendarDays size={16} /> {new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}</span></div></header>
        <nav className="mobile-section-nav" aria-label="Sections">
          {['dashboard', 'applications', 'calendar'].map(section => <button key={section} className={`nav-item${activeSection === section ? ' active' : ''}`} aria-current={activeSection === section ? 'location' : undefined} onClick={() => navigateTo(section)}>{section === 'dashboard' ? <LayoutDashboard size={16} /> : section === 'applications' ? <BriefcaseBusiness size={16} /> : <CalendarDays size={16} />}{section.charAt(0).toUpperCase() + section.slice(1)}</button>)}
        </nav>
        <div className="page-heading"><div><h1>Application dashboard</h1><p className="subheading">Keep an eye on your opportunities and stay one step ahead.</p></div><div className="progress-note"><span className="progress-ring">{applications.length}</span><div><div className="momentum-heading"><strong>{applications.length ? 'You’re building momentum' : 'Start your pipeline'}</strong>{streak > 0 && <span className="streak-badge" title="Consecutive application days. Apply today to keep your streak going."><Flame size={15} aria-hidden="true" />{streak}-day streak</span>}</div><small>{applications.length ? 'applications tracked' : 'Add your first application'}</small></div></div></div>

        {error && <div className="error-banner">{error}<button onClick={() => setError('')}><X size={16} /></button></div>}
        <section className="stats-grid">
          <StatCard label="Total applications" value={applications.length} detail="All your opportunities" tone="blue" />
          <StatCard label="In progress" value={(counts.APPLIED || 0) + (counts.INTERVIEW || 0)} detail="Applied + interviews" tone="sky" />
          <StatCard label="Offers" value={counts.OFFER || 0} detail="Great news" tone="ruby" />
          <StatCard label="Interviews" value={counts.INTERVIEW || 0} detail="Keep preparing" tone="slate" />
        </section>

        <section className="applications-section" ref={applicationsSection}>
          <div className="section-header"><div><h2>Applications</h2><p>{filteredApplications.length} {filteredApplications.length === 1 ? 'opportunity' : 'opportunities'} in view</p></div><button className="secondary-button" onClick={openNew}><CirclePlus size={17} /> New application</button></div>
          <div className="toolbar"><div className="search-box"><Search size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search companies, roles, locations..." /></div><div className="filter-wrap"><ListFilter size={16} /><select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="ALL">All statuses</option>{STATUSES.map((status) => <option key={status} value={status}>{statusLabels[status]}</option>)}</select></div></div>
          <div className="table-wrap">
            {loading ? <div className="empty-state"><div className="spinner" /> Loading applications...</div> : filteredApplications.length === 0 ? <div className="empty-state"><div className="empty-icon"><BriefcaseBusiness size={24} /></div><h3>{applications.length ? 'No matching applications' : 'Your pipeline is waiting'}</h3><p>{applications.length ? 'Try adjusting your search or filters.' : 'Add your first application to start tracking your job search.'}</p>{!applications.length && <button className="primary-button" onClick={openNew}><CirclePlus size={17} /> Add application</button>}</div> : <table><thead><tr><th>Company / role</th><th>Status</th><th>Location</th><th>Date applied</th><th></th></tr></thead><tbody>{filteredApplications.map((application) => <ApplicationRow key={application.id} application={application} onEdit={openEdit} onDelete={handleDelete} />)}</tbody></table>}
          </div>
        </section>
        <section className="calendar-section" ref={calendarSection}>
          <Calendar applications={applications} today={today} loading={loading} onEdit={openEdit} />
        </section>
      </main>
      {showForm && <ApplicationModal form={form} setForm={setForm} editing={editing} onSubmit={handleSubmit} onClose={closeForm} />}
    </div>
  )
}

function StatCard({ label, value, detail, tone }) { return <div className={`stat-card ${tone}`}><div className="stat-top"><span>{label}</span><span className="stat-dot" /></div><strong>{value}</strong><small>{detail}</small></div> }

function ApplicationRow({ application, onEdit, onDelete }) {
  return <tr><td><div className="company-cell"><div className="company-logo">{application.companyName?.charAt(0).toUpperCase()}</div><div><strong>{application.companyName}</strong><span>{application.jobTitle}{application.jobUrl && <a href={application.jobUrl} target="_blank" rel="noreferrer"><ExternalLink size={12} /></a>}</span></div></div></td><td><span className={`status-pill ${application.status.toLowerCase()}`}><span />{statusLabels[application.status] || application.status}</span></td><td><span className="muted-cell">{application.location || '—'}</span></td><td><span className="muted-cell">{application.dateApplied ? new Date(`${application.dateApplied}T00:00:00`).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : '—'}</span></td><td><div className="row-actions"><button title="Edit" onClick={() => onEdit(application)}><Pencil size={16} /></button><button title="Delete" onClick={() => onDelete(application.id)}><Trash2 size={16} /></button></div></td></tr>
}

function ApplicationModal({ form, setForm, editing, onSubmit, onClose }) {
  const update = (field) => (event) => {
    const value = event.target.value
    setForm((current) => ({
      ...current,
      [field]: value,
      ...(field === 'status' && value === 'APPLIED' && !current.dateApplied
        ? { dateApplied: localDateKey() } : {}),
    }))
  }
  return <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><div className="modal"><div className="modal-header"><div><p className="eyebrow">{editing ? 'UPDATE OPPORTUNITY' : 'NEW OPPORTUNITY'}</p><h2>{editing ? 'Edit application' : 'Add application'}</h2></div><button className="close-button" onClick={onClose}><X size={20} /></button></div><form onSubmit={onSubmit}><div className="form-grid"><label>Company name<input required value={form.companyName} onChange={update('companyName')} placeholder="e.g. Acme Inc." /></label><label>Job title<input required value={form.jobTitle} onChange={update('jobTitle')} placeholder="e.g. Product Designer" /></label><label>Status<select value={form.status} onChange={update('status')}>{STATUSES.map((status) => <option key={status} value={status}>{statusLabels[status]}</option>)}</select></label><label>Location<div className="input-icon"><MapPin size={15} /><input value={form.location} onChange={update('location')} placeholder="e.g. Remote" /></div></label><label>Date applied<input type="date" value={form.dateApplied || ''} onChange={update('dateApplied')} /></label><label>Interview date<input type="date" value={form.interviewDate || ''} onChange={update('interviewDate')} /></label><label className="full-width">Job URL<input type="url" value={form.jobUrl || ''} onChange={update('jobUrl')} placeholder="https://..." /></label></div><div className="modal-footer"><button type="button" className="secondary-button" onClick={onClose}>Cancel</button><button className="primary-button" type="submit"><Check size={17} /> {editing ? 'Save changes' : 'Add application'}</button></div></form></div></div>
}

export default App
