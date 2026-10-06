import type { FieldDef } from '../grammar/types'
import { enumValues } from '../grammar/types'

/** tds enum colours → Mantine palette names. */
export const ENUM_COLORS: Record<string, string> = {
  grey: 'gray', red: 'red', orange: 'orange', yellow: 'yellow', green: 'green', blue: 'blue', purple: 'violet',
}

const pad = (n: number) => String(n).padStart(2, '0')

/** ISO-8601 UTC → viewer's local time using a `yyyy-MM-dd HH:mm` style pattern (grammar §9 ui.format). */
export function formatDateTime(iso: string, pattern = 'yyyy-MM-dd HH:mm'): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const tokens: Record<string, string> = {
    yyyy: String(d.getFullYear()), MM: pad(d.getMonth() + 1), dd: pad(d.getDate()),
    HH: pad(d.getHours()), mm: pad(d.getMinutes()), ss: pad(d.getSeconds()),
  }
  return pattern.replace(/yyyy|MM|dd|HH|mm|ss/g, (t) => tokens[t])
}

/** `0.00 's'` style number formats. */
export function formatNumber(value: number, format?: string): string {
  const m = /^0(?:\.(0+))?\s*(?:'([^']*)')?$/.exec(format ?? '')
  if (!m) return String(value)
  return value.toFixed(m[1] ? m[1].length : 0) + (m[2] ? ` ${m[2]}` : '')
}

/** Plain-text rendering of a value (used for titles, sorting hints and tooltips). */
export function textOf(f: FieldDef, value: unknown, row?: Record<string, unknown>): string {
  if (value === null || value === undefined || value === '') return ''
  switch (f.type) {
    case 'enum':
      return enumValues(f).find((e) => e.value === value)?.label ?? String(value)
    case 'ref':
      return String(row?.[`${f.name}$display`] ?? `#${value}`)
    case 'datetime':
      return formatDateTime(String(value), f.ui?.format)
    case 'boolean':
      return value ? 'Yes' : 'No'
    case 'json':
      return JSON.stringify(value)
    default:
      return typeof value === 'number' && f.ui?.format ? formatNumber(value, f.ui.format) : String(value)
  }
}

/** ISO UTC → value for `<input type="datetime-local">` in local time. */
export function toLocalInput(iso: unknown): string {
  if (typeof iso !== 'string' || !iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** `<input type="datetime-local">` value (local time) → ISO UTC. */
export function fromLocalInput(local: string): string | null {
  if (!local) return null
  const d = new Date(local)
  return Number.isNaN(d.getTime()) ? null : d.toISOString()
}
