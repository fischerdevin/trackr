import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'

// Best-effort checks for common credentials. Report locations, never their values.
const patterns = [
  ['Brevo SMTP key', /\bxsmtpsib-[A-Za-z0-9_-]{25,}/],
  ['Brevo API key', /\bxkeysib-[A-Za-z0-9_-]{25,}/],
  ['GitHub token', /\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})/],
  ['Google API key', /\bAIza[A-Za-z0-9_-]{30,}/],
  ['Google OAuth secret', /\bGOCSPX-[A-Za-z0-9_-]{20,}/],
  ['AWS access key', /\b(?:AKIA|ASIA)[A-Z0-9]{16}\b/],
  ['private key', /-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----/],
]
const secretAssignment = /^\s*(?:export\s+)?(?:spring\.(?:mail|datasource)\.password|spring\.security\.oauth2\.client\.registration\.\w+\.client-secret|app\.demo\.password|[A-Z][A-Z0-9_]*(?:PASSWORD|SECRET|TOKEN|API_KEY|PRIVATE_KEY)|password|api[_-]?key|client[_-]?secret)\s*[:=]\s*(.+)/
const placeholder = /^(?:\$\{|\$[A-Z_]|your[-_ ]|example|replace|placeholder|test[-_ ]|fake|changeme)/i
const privatePath = /(?:^|\/)(?:\.secrets\/|\.env(?:\.|$))|\.(?:pem|key)$|application-local[^/]*\.(?:properties|ya?ml)$/i
const safeTemplate = /(?:\.example(?:\.|$)|\.sample(?:\.|$))/i
const staged = process.argv.includes('--staged')
const args = staged ? ['ls-files', '--cached', '-z'] : ['ls-files', '--cached', '--others', '--exclude-standard', '-z']
const files = [...new Set(execFileSync('git', args, { encoding: 'utf8' }).split('\0').filter(Boolean))]
let failures = 0
let checked = 0
for (const file of files) {
  if (privatePath.test(file) && !safeTemplate.test(file)) {
    console.error(`${file}: private configuration must not be tracked`)
    failures++
    continue
  }
  let buffer
  try {
    buffer = staged ? execFileSync('git', ['show', `:${file}`], { maxBuffer: 20 * 1024 * 1024 }) : readFileSync(file)
  } catch {
    console.error(`${file}: could not inspect file; refusing to skip it silently`)
    failures++
    continue
  }
  if (buffer.includes(0)) continue
  checked++
  buffer.toString('utf8').split(/\r?\n/).forEach((line, index) => {
    const match = patterns.find(([, pattern]) => pattern.test(line))
    const assignment = line.match(secretAssignment)
    const value = assignment?.[1].trim().replace(/^['"]|['"],?$/g, '')
    const literal = value && value !== '""' && value !== "''" && !placeholder.test(value)
    if (match || literal) {
      console.error(`${file}:${index + 1}: possible ${match?.[0] || 'hardcoded credential'} (value redacted)`)
      failures++
    }
  })
}
if (failures) {
  console.error(`Secret check failed: ${failures} finding(s). Move credentials to ignored local configuration.`)
  process.exitCode = 1
} else console.log(`Secret check passed: ${checked} ${staged ? 'staged' : 'publishable working-tree'} text files inspected.`)
