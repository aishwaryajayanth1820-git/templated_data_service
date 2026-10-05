import { describe, expect, it } from 'vitest'

import type { TemplateDoc } from '../grammar/types'
import { dropRefs, effectiveAccess, fieldOps, renameRefs, setSub } from './edit'

const doc = (): TemplateDoc => ({
  grammar: 'tds/v1',
  name: 'alerts',
  manageType: 'VIEW',
  fields: [
    { name: 'id', type: 'id' },
    { name: 'alert_site', type: 'text', access: { viewer: ['read'] } },
    { name: 'alert_ticket', type: 'text', requiredWhen: { present: [{ var: 'alert_site' }] } },
  ],
  indexes: [{ name: 'ix_site', fields: ['alert_site'] }],
  rules: [{ id: 'r', kind: 'requires', if: 'alert_ticket', then: ['alert_site'] }],
  view: { titleField: 'alert_site', search: ['alert_site', 'alert_ticket'], filters: ['alert_site'], defaultSort: [{ field: 'alert_site' }] },
})

describe('studio edit helpers', () => {
  it('renames every reference to a field', () => {
    const t = doc()
    renameRefs(t, 'alert_site', 'site_code')
    expect(t.indexes![0].fields).toEqual(['site_code'])
    expect(t.rules![0].then).toEqual(['site_code'])
    expect(t.view).toEqual({ titleField: 'site_code', search: ['site_code', 'alert_ticket'], filters: ['site_code'], defaultSort: [{ field: 'site_code' }] })
    expect(t.fields[2].requiredWhen).toEqual({ present: [{ var: 'site_code' }] })
  })

  it('drops a deleted field from lists and removes emptied ones', () => {
    const t = doc()
    dropRefs(t, 'alert_site')
    expect(t.indexes).toBeUndefined()
    expect(t.rules![0].then).toEqual([])
    expect(t.view).toEqual({ search: ['alert_ticket'] })
  })

  it('setSub removes empty sub-objects', () => {
    const f: Record<string, unknown> = { ui: { placement: 'column' } }
    setSub(f, 'ui', 'placement', undefined)
    expect(f).toEqual({})
  })

  it('computes effective access with defaults and field narrowing', () => {
    const t = doc()
    expect(effectiveAccess(t).viewer).toEqual(['read'])
    expect(fieldOps(t, t.fields[1], 'viewer')).toEqual(['read'])
    expect(fieldOps(t, t.fields[1], 'admin')).toEqual(['read', 'create', 'update'])
    expect(fieldOps(t, t.fields[1], 'operator')).toEqual([])
  })
})
