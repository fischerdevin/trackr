import test from 'node:test'
import assert from 'node:assert/strict'
import { applicationStreak, localDateKey } from './streak.js'

const applications = (...dates) => dates.map(dateApplied => ({ dateApplied, status: 'APPLIED' }))

test('starts only after two consecutive days, counting each day once', () => {
  assert.equal(applicationStreak([], '2026-10-20'), 0)
  assert.equal(applicationStreak(applications('2026-10-20', '2026-10-20'), '2026-10-20'), 0)
  assert.equal(applicationStreak(applications('2026-10-19', '2026-10-20', '2026-10-20'), '2026-10-20'), 2)
})

test('allows today to remain unfinished and resets after a missed full day', () => {
  const records = applications('2026-10-17', '2026-10-18', '2026-10-19')
  assert.equal(applicationStreak(records, '2026-10-20'), 3)
  assert.equal(applicationStreak(records, '2026-10-21'), 0)
  assert.equal(applicationStreak([...records, ...applications('2026-10-21')], '2026-10-21'), 0)
})

test('ignores future dates, missing dates, and saved postings', () => {
  assert.equal(applicationStreak([
    ...applications('2026-10-20', '2026-10-21'),
    { status: 'SAVED', dateApplied: '2026-10-19' },
    { status: 'APPLIED', dateApplied: null },
  ], '2026-10-20'), 0)
})

test('keeps previously applied days after status changes', () => {
  assert.equal(applicationStreak([
    { status: 'REJECTED', dateApplied: '2026-10-19' },
    { status: 'INTERVIEW', dateApplied: '2026-10-20' },
  ], '2026-10-20'), 2)
})

test('handles month, year, leap day, and daylight-saving boundaries', () => {
  for (const [previous, today] of [
    ['2026-09-30', '2026-10-01'], ['2025-12-31', '2026-01-01'],
    ['2024-02-28', '2024-02-29'], ['2026-03-08', '2026-03-09'],
  ]) assert.equal(applicationStreak(applications(previous, today), today), 2)
})

test('uses the local calendar date rather than the UTC date', () => {
  assert.equal(localDateKey(new Date(2026, 9, 20, 23, 59)), '2026-10-20')
})
