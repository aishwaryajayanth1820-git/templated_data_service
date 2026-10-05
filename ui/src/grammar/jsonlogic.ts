/**
 * JsonLogic subset (ADR-0003). Mirrors com.lnw.tds.grammar.JsonLogic; both are checked against
 * testdata/parity/jsonlogic.json. JavaScript operators are the reference semantics.
 */

export type Rule = unknown
export type Data = Record<string, unknown>

export const OPERATORS = new Set([
  'var', '==', '===', '!=', '!==', '!', '!!', 'and', 'or', 'if',
  '<', '<=', '>', '>=', 'in', '+', '-', '*', '/', '%', 'present',
])

export const truthy = (v: unknown): boolean => (Array.isArray(v) ? v.length > 0 : !!v)

export const present = (v: unknown): boolean =>
  v !== null && v !== undefined && !(typeof v === 'string' && v.trim() === '')

function isOperation(rule: unknown): rule is Record<string, unknown> {
  return rule !== null && typeof rule === 'object' && !Array.isArray(rule) && Object.keys(rule).length === 1
}

export function apply(rule: Rule, data: Data): unknown {
  if (Array.isArray(rule)) return rule.map((r) => apply(r, data))
  if (!isOperation(rule)) return rule
  const op = Object.keys(rule)[0]
  const raw = rule[op]
  const args: unknown[] = Array.isArray(raw) ? raw : [raw]
  if (op === 'if') {
    let i = 0
    for (; i < args.length - 1; i += 2) if (truthy(apply(args[i], data))) return apply(args[i + 1], data)
    return i < args.length ? apply(args[i], data) : null
  }
  if (op === 'and') {
    let v: unknown = true
    for (const a of args) {
      v = apply(a, data)
      if (!truthy(v)) return v
    }
    return v
  }
  if (op === 'or') {
    let v: unknown = false
    for (const a of args) {
      v = apply(a, data)
      if (truthy(v)) return v
    }
    return v
  }
  const v = args.map((a) => apply(a, data))
  /* eslint-disable eqeqeq */
  const [a, b] = v as [any, any] // eslint-disable-line @typescript-eslint/no-explicit-any
  switch (op) {
    case 'var': {
      if (a === '' || a === null || a === undefined) return data
      let cur: unknown = data
      for (const part of String(a).split('.')) {
        if (cur === null || cur === undefined || typeof cur !== 'object') return b ?? null
        cur = (cur as Record<string, unknown>)[part]
      }
      return cur === undefined || cur === null ? (b ?? null) : cur
    }
    case '==': return a == b
    case '===': return a === b
    case '!=': return a != b
    case '!==': return a !== b
    case '!': return !truthy(a)
    case '!!': return truthy(a)
    case '<': return v.length === 3 ? a < b && b < (v[2] as any) : a < b // eslint-disable-line @typescript-eslint/no-explicit-any
    case '<=': return v.length === 3 ? a <= b && b <= (v[2] as any) : a <= b // eslint-disable-line @typescript-eslint/no-explicit-any
    case '>': return a > b
    case '>=': return a >= b
    case 'in': return b !== null && b !== undefined && typeof b.includes === 'function' && b.includes(a)
    case '+': return v.reduce((s: number, x) => s + Number(x), 0)
    case '-': return v.length === 1 ? -a : a - b
    case '*': return v.reduce((s: number, x) => s * Number(x), 1)
    case '/': return a / b
    case '%': return a % b
    case 'present': return present(a)
    default: throw new Error(`Unsupported JsonLogic operator "${op}"`)
  }
}

/** Evaluates a condition; malformed rules count as false rather than breaking the form. */
export function test(rule: Rule | undefined, data: Data): boolean {
  if (rule === undefined || rule === null) return false
  try {
    return truthy(apply(rule, data))
  } catch {
    return false
  }
}
