import { localDateKey } from './streak.js'

export function calendarDays(month) {
  const first = new Date(month.getFullYear(), month.getMonth(), 1, 12)
  const start = new Date(first)
  start.setDate(1 - first.getDay())
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start)
    date.setDate(start.getDate() + index)
    return { date, key: localDateKey(date), inMonth: date.getMonth() === month.getMonth() }
  })
}

export function applicationEvents(applications) {
  const events = new Map()
  for (const application of applications) {
    for (const [field, type] of [['dateApplied', 'applied'], ['interviewDate', 'interview']]) {
      const date = application[field]
      if (!date) continue
      if (!events.has(date)) events.set(date, [])
      events.get(date).push({ application, type, key: `${application.id}-${type}` })
    }
  }
  for (const entries of events.values()) {
    entries.sort((a, b) => a.type.localeCompare(b.type)
      || a.application.companyName.localeCompare(b.application.companyName))
  }
  return events
}
