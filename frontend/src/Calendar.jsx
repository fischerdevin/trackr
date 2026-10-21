import { useMemo, useState } from 'react'
import { CalendarDays, ChevronLeft, ChevronRight, Pencil } from 'lucide-react'
import { applicationEvents, calendarDays } from './calendarDates.js'
import { localDateKey } from './streak.js'
import CalendarEvent from './CalendarEvent.jsx'

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const displayDate = (date) => date.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' })

export default function Calendar({ applications, today, loading, onEdit }) {
  const [month, setMonth] = useState(() => new Date(`${today}T12:00:00`))
  const [selected, setSelected] = useState(today)
  const events = useMemo(() => applicationEvents(applications), [applications])
  const days = calendarDays(month)
  const selectedEvents = events.get(selected) || []
  const monthEvents = days.filter(day => day.inMonth).flatMap(day => events.get(day.key) || [])
  const monthTitle = month.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })

  function changeMonth(offset) {
    const next = new Date(month.getFullYear(), month.getMonth() + offset, 1, 12)
    setMonth(next)
    setSelected(localDateKey(next))
  }

  function selectDay(day) {
    setSelected(day.key)
    if (!day.inMonth) setMonth(day.date)
  }

  return <>
    <div className="section-header"><div><h2>Calendar</h2><p>Your application and interview dates, all in one place.</p></div></div>
    {loading ? <div className="empty-state"><div className="spinner" />Loading calendar…</div> : <>
      <div className="calendar-layout">
      <div className="calendar-card">
        <div className="calendar-toolbar">
          <h3 aria-live="polite">{monthTitle}</h3>
          <div className="calendar-controls">
            <button className="secondary-button" onClick={() => { setMonth(new Date(`${today}T12:00:00`)); setSelected(today) }}>Today</button>
            <button className="calendar-arrow" aria-label="Previous month" onClick={() => changeMonth(-1)}><ChevronLeft size={18} /></button>
            <button className="calendar-arrow" aria-label="Next month" onClick={() => changeMonth(1)}><ChevronRight size={18} /></button>
          </div>
        </div>
        <div className="calendar-legend"><span><i className="calendar-dot applied-dot" />Applied</span><span><i className="calendar-dot interview-dot" />Interview</span><small>{monthEvents.length} {monthEvents.length === 1 ? 'event' : 'events'} this month</small></div>
        <div className="calendar-grid" aria-label={monthTitle}>
          {WEEKDAYS.map(day => <div key={day} className="calendar-weekday">{day}</div>)}
          {days.map(day => {
            const entries = events.get(day.key) || []
            return <div key={day.key} className={`calendar-day${day.inMonth ? '' : ' outside-month'}${selected === day.key ? ' selected-day' : ''}`}
              onClick={() => selectDay(day)}>
              <button className={`calendar-date${day.key === today ? ' is-today' : ''}`} aria-pressed={selected === day.key}
                aria-current={day.key === today ? 'date' : undefined}
                aria-label={`${displayDate(day.date)}, ${entries.length} ${entries.length === 1 ? 'event' : 'events'}`}>{day.date.getDate()}</button>
              <div className="calendar-day-events">
                {entries.slice(0, 2).map(event => <CalendarEvent key={event.key} event={event} onSelect={() => selectDay(day)} onEdit={onEdit} />)}
                {entries.length > 2 && <button className="calendar-more">+{entries.length - 2} more</button>}
              </div>
              {entries.length > 0 && <span className="calendar-mobile-count" aria-hidden="true">{entries.length}</span>}
            </div>
          })}
        </div>
      </div>
      <div className="calendar-agenda" aria-live="polite">
        <h3>{displayDate(new Date(`${selected}T12:00:00`))}</h3>
        {selectedEvents.length === 0 ? <p className="calendar-empty"><CalendarDays size={18} aria-hidden="true" />No applications or interviews on this date.</p>
          : <div className="calendar-agenda-list">{selectedEvents.map(event => <button className="calendar-agenda-item" key={event.key} onClick={() => onEdit(event.application)}>
            <span className={`calendar-event-label ${event.type}-event`}>{event.type === 'interview' ? 'Interview' : 'Applied'}</span>
            <span className="calendar-job"><strong>{event.application.companyName}</strong><span>{event.application.jobTitle}</span>{event.application.location && <small>{event.application.location}</small>}</span>
            <Pencil size={16} aria-hidden="true" />
          </button>)}</div>}
      </div>
      </div>
      <p className="calendar-help">Click anywhere on a day or one of its jobs to see that day’s details. Hover over a job for its posting link, or select a job in the details panel to edit.</p>
    </>}
  </>
}
