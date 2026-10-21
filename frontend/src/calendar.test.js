import test from 'node:test'
import assert from 'node:assert/strict'
import { applicationEvents, calendarDays } from './calendarDates.js'

test('calendar contains six complete weeks with correctly aligned dates', () => {
  const days = calendarDays(new Date(2026, 9, 20))
  assert.equal(days.length, 42)
  assert.equal(days[0].key, '2026-09-27')
  assert.equal(days.at(-1).key, '2026-11-07')
  assert.equal(days.filter(day => day.inMonth).length, 31)
  assert.equal(new Set(days.map(day => day.key)).size, 42)
})

test('calendar handles leap years, year boundaries and daylight saving time', () => {
  assert.equal(calendarDays(new Date(2024, 1, 1)).filter(day => day.inMonth).length, 29)
  assert.equal(calendarDays(new Date(2027, 0, 1))[0].key, '2026-12-27')
  const days = calendarDays(new Date(2026, 2, 1))
  assert.equal(new Set(days.map(day => day.key)).size, 42)
  assert.ok(days.some(day => day.key === '2026-03-08'))
  assert.ok(days.some(day => day.key === '2026-03-09'))
})

test('application and interview dates are distinct events for the same job', () => {
  const job = { id: 1, companyName: 'Acme', jobTitle: 'Intern', dateApplied: '2026-10-20', interviewDate: '2026-10-22' }
  const events = applicationEvents([job])
  assert.equal(events.get('2026-10-20')[0].type, 'applied')
  assert.equal(events.get('2026-10-22')[0].type, 'interview')
  assert.equal(events.get('2026-10-22')[0].application, job)
})

test('retains every event on a shared date and ignores unset dates', () => {
  const events = applicationEvents([
    { id: 1, companyName: 'Acme', dateApplied: '2026-10-20', interviewDate: '2026-10-20' },
    { id: 2, companyName: 'Beta', dateApplied: '2026-10-20', interviewDate: null },
    { id: 3, companyName: 'Saved', dateApplied: '', interviewDate: null },
  ])
  assert.equal(events.size, 1)
  assert.equal(events.get('2026-10-20').length, 3)
  assert.equal(new Set(events.get('2026-10-20').map(event => event.key)).size, 3)
})
