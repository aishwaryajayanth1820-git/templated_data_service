import type { FieldDef, FieldOp, TemplateDoc } from '../grammar/types'

/** Sets `obj[key]`, or deletes it for empty values (keeps saved JSON tidy). */
export function setOrDel<T extends object>(obj: T, key: string, value: unknown) {
  const o = obj as Record<string, unknown>
  if (value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)) delete o[key]
  else o[key] = value
}

/** Sets `obj[sub][key]`, removing `obj[sub]` when it becomes empty. */
export function setSub<T extends object>(obj: T, sub: string, key: string, value: unknown) {
  const o = obj as Record<string, Record<string, unknown> | undefined>
  const child = (o[sub] ??= {}) as Record<string, unknown>
  setOrDel(child, key, value)
  if (Object.keys(child).length === 0) delete o[sub]
}

/** Rewrites JsonLogic `var` references from one field name to another. */
function renameVars(rule: unknown, from: string, to: string): unknown {
  if (Array.isArray(rule)) return rule.map((r) => renameVars(r, from, to))
  if (rule && typeof rule === 'object') {
    const out: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(rule)) {
      if (k === 'var' && typeof v === 'string' && (v === from || v.startsWith(`${from}.`))) out[k] = to + v.slice(from.length)
      else if (k === 'var' && Array.isArray(v) && typeof v[0] === 'string' && v[0] === from) out[k] = [to, ...v.slice(1)]
      else out[k] = renameVars(v, from, to)
    }
    return out
  }
  return rule
}

/** Keeps every reference to a renamed field in step: indexes, rules, view, conditions. */
export function renameRefs(t: TemplateDoc, from: string, to: string) {
  const r = (n: string) => (n === from ? to : n)
  const rl = (x: unknown) => (x === undefined ? x : renameVars(x, from, to))
  t.indexes?.forEach((ix) => (ix.fields = ix.fields.map(r)))
  t.rules?.forEach((ru) => {
    if (ru.if) ru.if = r(ru.if)
    if (ru.then) ru.then = ru.then.map(r)
    if (ru.fields) ru.fields = ru.fields.map(r)
    if (ru.when !== undefined) ru.when = rl(ru.when)
    if (ru.assert !== undefined) ru.assert = rl(ru.assert)
  })
  const v = t.view
  if (v) {
    if (v.titleField) v.titleField = r(v.titleField)
    v.defaultSort?.forEach((s) => (s.field = r(s.field)))
    if (v.search) v.search = v.search.map(r)
    if (v.filters) v.filters = v.filters.map(r)
  }
  t.fields.forEach((f) => {
    if (f.requiredWhen !== undefined) f.requiredWhen = rl(f.requiredWhen)
    if (f.ui?.visibleWhen !== undefined) f.ui.visibleWhen = rl(f.ui.visibleWhen)
    if (f.ui?.readonlyWhen !== undefined) f.ui.readonlyWhen = rl(f.ui.readonlyWhen)
  })
  t.actions?.forEach((a) => {
    if (a.visibleWhen !== undefined) a.visibleWhen = rl(a.visibleWhen)
  })
}

/** Removes a deleted field from lists that reference it; `if`/expr references stay and surface as errors. */
export function dropRefs(t: TemplateDoc, name: string) {
  const keep = (n: string) => n !== name
  if (t.indexes) {
    t.indexes = t.indexes.map((ix) => ({ ...ix, fields: ix.fields.filter(keep) })).filter((ix) => ix.fields.length)
    if (!t.indexes.length) delete t.indexes
  }
  t.rules?.forEach((ru) => {
    if (ru.then) ru.then = ru.then.filter(keep)
    if (ru.fields) ru.fields = ru.fields.filter(keep)
  })
  const v = t.view
  if (v) {
    if (v.titleField === name) delete v.titleField
    if (v.defaultSort) {
      v.defaultSort = v.defaultSort.filter((s) => keep(s.field))
      if (!v.defaultSort.length) delete v.defaultSort
    }
    if (v.search) {
      v.search = v.search.filter(keep)
      if (!v.search.length) delete v.search
    }
    if (v.filters) {
      v.filters = v.filters.filter(keep)
      if (!v.filters.length) delete v.filters
    }
  }
}

export const TABLE_OPS = ['read', 'create', 'update', 'delete'] as const
export const FIELD_OPS: FieldOp[] = ['read', 'create', 'update']

/** Table access as written, or the grammar §6.1 defaults. */
export function effectiveAccess(t: TemplateDoc): Record<string, string[]> {
  return t.access ?? {
    admin: ['read', 'create', 'update', 'delete', 'run:*'],
    viewer: t.manageType === 'DATA_SOURCE' ? [] : ['read'],
  }
}

/** Effective field operations for one role (mirrors AccessEvaluator.fieldOps). */
export function fieldOps(t: TemplateDoc, f: FieldDef, role: string): FieldOp[] {
  if (role === 'admin') return f.type === 'id' ? ['read'] : [...FIELD_OPS]
  const table = effectiveAccess(t)[role] ?? []
  if (f.type === 'id') return table.includes('read') ? ['read'] : []
  const own = f.access?.[role] ?? FIELD_OPS
  return own.filter((op) => table.includes(op))
}

/** A field name that does not exist yet: `<prefix>_<n>`. */
export function freshName(existing: string[], prefix: string) {
  let n = 1
  while (existing.includes(`${prefix}_${n}`)) n++
  return `${prefix}_${n}`
}
