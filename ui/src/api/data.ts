import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { apiFetch } from './client'

export type Row = Record<string, unknown> & { id: number; row_version?: number; $issues?: string[] }

export interface Page {
  items: Row[]
  page: number
  size: number
  total: number
}

export interface ActionResult {
  kind: 'text' | 'download' | 'message' | 'refresh'
  title?: string
  content?: string
  file?: string
}

export interface Option {
  id: number
  label: string
}

export const DATA_KEY = ['data'] as const

const base = (name: string) => `/api/data/${encodeURIComponent(name)}`

/** `query` is the API query string (page, size, sort, q, f.*) — the same string the page URL carries. */
export function useRecords(name: string, query: string) {
  return useQuery({
    queryKey: [...DATA_KEY, name, query],
    queryFn: () => apiFetch<Page>(`${base(name)}${query ? `?${query}` : ''}`),
    placeholderData: keepPreviousData,
  })
}

export function useSaveRecord(name: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id?: number; body: Record<string, unknown> }) =>
      id === undefined
        ? apiFetch<Row>(base(name), { method: 'POST', body })
        : apiFetch<Row>(`${base(name)}/${id}`, { method: 'PATCH', body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...DATA_KEY, name] }),
  })
}

export function useDeleteRecord(name: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiFetch<void>(`${base(name)}/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...DATA_KEY, name] }),
  })
}

export function useRunAction(name: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ action, id, ids }: { action: string; id?: number; ids?: number[] }) =>
      id !== undefined
        ? apiFetch<ActionResult>(`${base(name)}/${id}/actions/${encodeURIComponent(action)}`, { method: 'POST' })
        : apiFetch<ActionResult>(`${base(name)}/actions/${encodeURIComponent(action)}`, { method: 'POST', body: { ids: ids ?? [] } }),
    onSuccess: (r) => {
      if (r.kind === 'refresh') qc.invalidateQueries({ queryKey: [...DATA_KEY, name] })
    },
  })
}

export function fetchOptions(name: string, field: string, q: string) {
  return apiFetch<Option[]>(`${base(name)}/fields/${encodeURIComponent(field)}/options?q=${encodeURIComponent(q)}`)
}

export function outputUrl(name: string, file: string) {
  return `${base(name)}/action-output/${encodeURIComponent(file)}`
}
