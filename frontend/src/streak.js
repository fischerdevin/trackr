const DAY_MS = 86_400_000

export function localDateKey(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

// UTC day numbers measure calendar days without daylight-saving time gaps.
export function applicationStreak(applications, today = localDateKey()) {
  const todayNumber = Date.parse(today) / DAY_MS
  const days = new Set(applications
    .filter(application => application.status !== 'SAVED' && application.dateApplied)
    .map(application => Date.parse(application.dateApplied) / DAY_MS)
    .filter(day => Number.isInteger(day) && day <= todayNumber))
  let day = days.has(todayNumber) ? todayNumber : todayNumber - 1
  let length = 0
  while (days.has(day)) {
    length++
    day--
  }
  return length >= 2 ? length : 0
}
