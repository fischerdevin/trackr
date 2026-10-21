import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { ExternalLink, MapPin } from 'lucide-react'

const formatDate = value => value ? new Date(`${value}T12:00:00`).toLocaleDateString('en-US', {
  month: 'short', day: 'numeric', year: 'numeric',
}) : 'Not set'

export default function CalendarEvent({ event, onSelect, onEdit }) {
  const { application, type } = event
  const [open, setOpen] = useState(false)
  const [position, setPosition] = useState(null)
  const trigger = useRef(null)
  const popup = useRef(null)
  const timer = useRef(null)
  const id = useId()
  let jobLink = null
  try {
    const url = new URL(application.jobUrl)
    if (['https:', 'http:'].includes(url.protocol)) jobLink = url.href
  } catch { /* Jobs without a valid posting URL simply omit the link. */ }

  function show() {
    window.clearTimeout(timer.current)
    setOpen(true)
  }
  function hideSoon() {
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setOpen(false), 180)
  }
  function blur(event) {
    if (event.relatedTarget === trigger.current || popup.current?.contains(event.relatedTarget)) return
    hideSoon()
  }

  useEffect(() => () => window.clearTimeout(timer.current), [])
  useLayoutEffect(() => {
    if (!open) { setPosition(null); return }
    const anchor = trigger.current.getBoundingClientRect()
    const bounds = popup.current.getBoundingClientRect()
    setPosition({
      left: Math.max(12, Math.min(anchor.left, window.innerWidth - bounds.width - 12)),
      top: anchor.bottom + 8 + bounds.height <= window.innerHeight - 12
        ? anchor.bottom + 8 : Math.max(12, anchor.top - bounds.height - 8),
    })
    const dismiss = (event) => {
      if (!popup.current?.contains(event.target)) setOpen(false)
    }
    window.addEventListener('scroll', dismiss, true)
    window.addEventListener('resize', dismiss)
    return () => {
      window.removeEventListener('scroll', dismiss, true)
      window.removeEventListener('resize', dismiss)
    }
  }, [open])

  function keyDown(event) {
    if (event.key === 'Escape') {
      window.clearTimeout(timer.current)
      trigger.current.focus()
      setOpen(false)
    } else if (event.key === 'ArrowDown' && open) {
      event.preventDefault()
      popup.current?.querySelector('a, button')?.focus()
    }
  }

  return <>
    <button ref={trigger} className={`calendar-event ${type}-event`}
      aria-label={`${type === 'interview' ? 'Interview' : 'Applied'}: ${application.companyName}, ${application.jobTitle}`}
      aria-expanded={open} aria-controls={open ? id : undefined}
      onMouseEnter={show} onMouseLeave={hideSoon} onFocus={show} onBlur={blur} onKeyDown={keyDown}
      onClick={click => { click.stopPropagation(); setOpen(false); onSelect() }}>
      <span>{type === 'interview' ? 'Interview' : 'Applied'}</span><strong>{application.companyName}</strong>
    </button>
    {open && createPortal(<div ref={popup} id={id} className="calendar-hover-card" role="region" aria-label="Job details"
      style={{ left: position?.left ?? 0, top: position?.top ?? 0, visibility: position ? 'visible' : 'hidden' }}
      onMouseEnter={show} onMouseLeave={hideSoon} onFocus={show} onBlur={blur} onKeyDown={keyDown}
      onClick={click => click.stopPropagation()}>
      <span className={`calendar-event-label ${type}-event`}>{type === 'interview' ? 'Interview' : 'Applied'}</span>
      <strong className="calendar-hover-company">{application.companyName}</strong>
      <p className="calendar-hover-role">{application.jobTitle}</p>
      {application.location && <p className="calendar-hover-location"><MapPin size={13} aria-hidden="true" />{application.location}</p>}
      <dl><div><dt>Applied</dt><dd>{formatDate(application.dateApplied)}</dd></div><div><dt>Interview</dt><dd>{formatDate(application.interviewDate)}</dd></div></dl>
      {jobLink ? <a href={jobLink} target="_blank" rel="noreferrer">View job posting <ExternalLink size={13} aria-hidden="true" /></a>
        : <p className="calendar-hover-no-link">No job link added</p>}
      <button className="calendar-hover-edit" onClick={() => { setOpen(false); onEdit(application) }}>Edit application</button>
    </div>, document.body)}
  </>
}
