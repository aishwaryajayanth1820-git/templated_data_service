import { useQuery } from '@tanstack/react-query'

import type { ActionDef, FieldDef, FieldOp, ManageType, RuleDef, ViewDef } from '../grammar/types'
import { apiFetch } from './client'

export interface NavItem {
  name: string
  label: string
  description?: string
  manageType: ManageType
}

export interface Navigation {
  generation: number
  tables: NavItem[]
  dataSources: NavItem[]
}

export interface FieldMeta extends FieldDef {
  ops: FieldOp[]
}

/** What the signed-in user may see and do with one table (GET /api/meta/{name}). */
export interface TemplateMeta {
  name: string
  label: string
  description?: string
  manageType: ManageType
  version: number
  rowVersion: boolean
  fields: FieldMeta[]
  actions: ActionDef[]
  view: ViewDef
  rules: RuleDef[]
  permissions: { read: boolean; create: boolean; update: boolean; delete: boolean }
}

export const META_KEY = ['meta'] as const

export function useNav() {
  return useQuery({ queryKey: [...META_KEY, 'nav'], queryFn: () => apiFetch<Navigation>('/api/meta') })
}

export function useTemplateMeta(name: string | undefined) {
  return useQuery({
    queryKey: [...META_KEY, 'template', name],
    queryFn: () => apiFetch<TemplateMeta>(`/api/meta/${encodeURIComponent(name!)}`),
    enabled: !!name,
  })
}
