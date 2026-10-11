import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import type { TemplateDoc } from '../grammar/types'
import { apiFetch } from './client'

// ---- Roles & users -------------------------------------------------------------------------

export interface RoleView {
  name: string
  description?: string
  builtin: boolean
  users: number
  templates: string[]
}

export interface UserView {
  username: string
  displayName?: string
  enabled: boolean
  mustChangePassword: boolean
  roles: string[]
  createdAt: string
}

const ROLES = ['admin', 'roles'] as const
const USERS = ['admin', 'users'] as const

export function useRoles() {
  return useQuery({ queryKey: ROLES, queryFn: () => apiFetch<RoleView[]>('/api/admin/roles') })
}

export function useRoleMutations() {
  const qc = useQueryClient()
  const done = () => qc.invalidateQueries({ queryKey: ROLES })
  return {
    create: useMutation({
      mutationFn: (r: { name: string; description?: string }) => apiFetch<RoleView>('/api/admin/roles', { method: 'POST', body: r }),
      onSuccess: done,
    }),
    update: useMutation({
      mutationFn: (r: { name: string; description?: string }) =>
        apiFetch<RoleView>(`/api/admin/roles/${r.name}`, { method: 'PUT', body: r }),
      onSuccess: done,
    }),
    remove: useMutation({
      mutationFn: (name: string) => apiFetch<void>(`/api/admin/roles/${name}`, { method: 'DELETE' }),
      onSuccess: done,
    }),
  }
}

export function useUsers() {
  return useQuery({ queryKey: USERS, queryFn: () => apiFetch<UserView[]>('/api/admin/users') })
}

export function useUserMutations() {
  const qc = useQueryClient()
  const done = () => qc.invalidateQueries({ queryKey: USERS })
  return {
    create: useMutation({
      mutationFn: (u: { username: string; displayName?: string; password: string; roles: string[] }) =>
        apiFetch<UserView>('/api/admin/users', { method: 'POST', body: u }),
      onSuccess: done,
    }),
    update: useMutation({
      mutationFn: (u: { username: string; displayName?: string; enabled: boolean; roles: string[] }) =>
        apiFetch<UserView>(`/api/admin/users/${encodeURIComponent(u.username)}`, { method: 'PUT', body: u }),
      onSuccess: done,
    }),
    resetPassword: useMutation({
      mutationFn: (u: { username: string; password: string }) =>
        apiFetch<void>(`/api/admin/users/${encodeURIComponent(u.username)}/password`, { method: 'POST', body: { password: u.password } }),
      onSuccess: done,
    }),
  }
}

// ---- Templates (Schema Studio) ---------------------------------------------------------------

export interface Issue {
  severity: 'ERROR' | 'WARNING'
  code: string
  message: string
  path: string
}

export interface DraftView {
  name: string
  label?: string
  manageType: string
  json: TemplateDoc
  checksum: string
  publishedVersion?: number
  status: 'DRAFT' | 'PUBLISHED' | 'CHANGED'
  issues: Issue[]
  updatedAt: string
  updatedBy: string
}

export interface PlanStep {
  cls: 'METADATA' | 'SAFE' | 'CHECKED' | 'DESTRUCTIVE' | 'BLOCKED'
  description: string
}

export interface PlanView {
  name: string
  fromVersion?: number
  steps: PlanStep[]
  preChecks: { description: string; violations: number; error?: string }[]
  rebuild: boolean
  statements: string[]
  ddl: Record<string, string>
  issues: Issue[]
  publishable: boolean
  blockers: string[]
  needsConfirmation: boolean
}

export interface VersionView {
  version: number
  publishedAt: string
  publishedBy: string
  appliedSql?: string
}

const TEMPLATES = ['admin', 'templates'] as const

export function useTemplates() {
  return useQuery({ queryKey: TEMPLATES, queryFn: () => apiFetch<DraftView[]>('/api/admin/templates') })
}

export function useTemplate(name: string | undefined) {
  return useQuery({
    queryKey: [...TEMPLATES, name],
    queryFn: () => apiFetch<DraftView>(`/api/admin/templates/${name}`),
    enabled: !!name,
  })
}

export function usePlan(name: string, enabled: boolean) {
  return useQuery({
    queryKey: [...TEMPLATES, name, 'plan'],
    queryFn: () => apiFetch<PlanView>(`/api/admin/templates/${name}/plan`),
    enabled,
    gcTime: 0,
  })
}

export function useVersions(name: string) {
  return useQuery({
    queryKey: [...TEMPLATES, name, 'versions'],
    queryFn: () => apiFetch<VersionView[]>(`/api/admin/templates/${name}/versions`),
  })
}

export function validateTemplate(doc: TemplateDoc) {
  return apiFetch<Issue[]>('/api/admin/templates/validate', { method: 'POST', body: doc })
}

export function useTemplateMutations() {
  const qc = useQueryClient()
  const done = () => qc.invalidateQueries({ queryKey: TEMPLATES })
  return {
    create: useMutation({
      mutationFn: (doc: TemplateDoc) => apiFetch<DraftView>('/api/admin/templates', { method: 'POST', body: doc }),
      onSuccess: done,
    }),
    importDoc: useMutation({
      mutationFn: (doc: TemplateDoc) => apiFetch<DraftView>('/api/admin/templates/import', { method: 'POST', body: doc }),
      onSuccess: done,
    }),
    save: useMutation({
      mutationFn: ({ doc, checksum }: { doc: TemplateDoc; checksum: string }) =>
        apiFetch<DraftView>(`/api/admin/templates/${doc.name}`, { method: 'PUT', body: doc, headers: { 'If-Match': checksum } }),
      onSuccess: (d) => {
        qc.setQueryData([...TEMPLATES, d.name], d)
        qc.invalidateQueries({ queryKey: TEMPLATES, exact: true })
      },
    }),
    publish: useMutation({
      mutationFn: ({ name, checksum, confirm }: { name: string; checksum: string; confirm?: string }) =>
        apiFetch<{ name: string; version: number }>(`/api/admin/templates/${name}/publish`, {
          method: 'POST',
          body: { expectedChecksum: checksum, confirm },
        }),
      onSuccess: () => {
        done()
        qc.invalidateQueries({ queryKey: ['meta'] })
        qc.invalidateQueries({ queryKey: ['data'] })
      },
    }),
    remove: useMutation({
      mutationFn: (name: string) => apiFetch<void>(`/api/admin/templates/${name}`, { method: 'DELETE' }),
      onSuccess: done,
    }),
  }
}

// ---- Scripts ----------------------------------------------------------------------------------

export interface ScriptInfo {
  path: string
  status: 'OK' | 'ERROR'
  error?: string
  functions: string[]
  content?: string
}

const SCRIPTS = ['admin', 'scripts'] as const

export function useScripts() {
  return useQuery({ queryKey: SCRIPTS, queryFn: () => apiFetch<ScriptInfo[]>('/api/admin/scripts') })
}

export function useScript(path: string | undefined) {
  return useQuery({
    queryKey: [...SCRIPTS, path],
    queryFn: () => apiFetch<ScriptInfo>(`/api/admin/scripts/content?path=${encodeURIComponent(path!)}`),
    enabled: !!path,
    retry: false,
  })
}

export function useSaveScript() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ path, content }: { path: string; content: string }) =>
      apiFetch<ScriptInfo>(`/api/admin/scripts/content?path=${encodeURIComponent(path)}`, { method: 'PUT', body: { content } }),
    onSuccess: (s) => {
      qc.setQueryData([...SCRIPTS, s.path], s)
      qc.invalidateQueries({ queryKey: SCRIPTS, exact: true })
      qc.invalidateQueries({ queryKey: TEMPLATES })
    },
  })
}
