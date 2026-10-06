import { Alert, Badge, Button, Center, Group, Loader, Modal, Pagination, Paper, Stack, Text, TextInput, Title } from '@mantine/core'
import { useDebouncedCallback } from '@mantine/hooks'
import { notifications } from '@mantine/notifications'
import { useState } from 'react'
import { useParams, useSearchParams } from 'react-router'

import { useMe } from '../api/auth'
import { ApiError } from '../api/client'
import { type ActionResult, outputUrl, type Row, useDeleteRecord, useRecords, useRunAction } from '../api/data'
import { useTemplateMeta } from '../api/meta'
import { test } from '../grammar/jsonlogic'
import type { ActionDef } from '../grammar/types'
import { ActionResultModal } from './ActionResultModal'
import { DataGrid } from './DataGrid'
import { FilterBar } from './FilterBar'
import { RecordDrawer } from './RecordDrawer'

/**
 * The generic page for any published table (/t/:name, and /admin/data/:name for DATA_SOURCE).
 * URL query = API query (page, size, sort, q, f.*), so views are bookmarkable.
 */
export function TemplatePage({ adminBrowser = false }: { adminBrowser?: boolean }) {
  const { name = '' } = useParams()
  const { data: me } = useMe()
  const meta = useTemplateMeta(name)
  const [params, setParams] = useSearchParams()
  const records = useRecords(name, params.toString())
  const remove = useDeleteRecord(name)
  const run = useRunAction(name)
  const [editing, setEditing] = useState<Row | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Row | null>(null)
  const [result, setResult] = useState<ActionResult | null>(null)
  const [selected, setSelected] = useState<number[]>([])
  const [search, setSearch] = useState(params.get('q') ?? '')

  const setParam = (key: string, value: string | null, resetPage = true) => {
    const next = new URLSearchParams(params)
    if (value === null || value === '') next.delete(key)
    else next.set(key, value)
    if (resetPage) next.delete('page')
    setParams(next, { replace: true })
  }
  const debouncedSearch = useDebouncedCallback((v: string) => setParam('q', v || null), 300)

  if (meta.isPending) return <Center p="xl"><Loader /></Center>
  if (meta.error) {
    return <Alert color="red" title="Cannot open this table">{meta.error.message}</Alert>
  }
  const m = meta.data
  const manage = m.manageType === 'MANAGE_VIEW' || (adminBrowser && m.manageType === 'DATA_SOURCE')
  const canCreate = manage && m.permissions.create && m.fields.some((f) => f.ops.includes('create'))
  const canEdit = manage && m.permissions.update
  const canDelete = manage && m.permissions.delete

  const sortParam = params.get('sort') ?? (m.view.defaultSort?.[0] ? `${m.view.defaultSort[0].field},${m.view.defaultSort[0].dir ?? 'asc'}` : null)
  const sort = sortParam ? { field: sortParam.split(',')[0], dir: (sortParam.split(',')[1] === 'desc' ? 'desc' : 'asc') as 'asc' | 'desc' } : null
  const onSort = (field: string) =>
    setParam('sort', sort?.field === field ? `${field},${sort.dir === 'asc' ? 'desc' : 'asc'}` : `${field},asc`, false)

  const filterFields = (m.view.filters ?? []).map((n) => m.fields.find((f) => f.name === n)).filter((f) => !!f)
  const filterValues = Object.fromEntries([...params.entries()].filter(([k]) => k.startsWith('f.')))
  const clearFilters = () => {
    const next = new URLSearchParams(params)
    for (const k of [...next.keys()]) if (k.startsWith('f.')) next.delete(k)
    next.delete('page')
    setParams(next, { replace: true })
  }

  const rowActions = m.actions.filter((a) => a.placement === 'row')
  const bulkActions = m.actions.filter((a) => a.placement !== 'row')
  const selectable = bulkActions.some((a) => a.placement === 'selection')
  const ctxFor = (r: Row) => ({ ...r, $op: 'update', $user: { username: me?.username, roles: me?.roles ?? [] } })

  const execute = (a: ActionDef, row?: Row) => {
    if (a.confirm && !window.confirm(a.confirm)) return
    run.mutate(
      { action: a.name, id: row?.id, ids: a.placement === 'selection' ? selected : undefined },
      {
        onSuccess: (r) => {
          if (r.kind === 'text') setResult(r)
          else if (r.kind === 'download' && r.file) window.location.href = outputUrl(name, r.file)
          else notifications.show({ message: r.content ?? `${a.label} done`, color: 'teal' })
        },
        onError: (e) => notifications.show({ title: `${a.label} failed`, message: e.message, color: 'red' }),
      },
    )
  }

  const page = records.data
  const pages = page ? Math.max(1, Math.ceil(page.total / page.size)) : 1

  return (
    <Stack gap="md">
      <Group justify="space-between" align="flex-start" wrap="wrap">
        <div>
          <Group gap="xs">
            <Title order={2}>{m.label}</Title>
            {adminBrowser && <Badge variant="light" color="gray">admin data browser</Badge>}
          </Group>
          {m.description && <Text c="dimmed" size="sm" maw={720}>{m.description}</Text>}
        </div>
        <Group gap="xs">
          {bulkActions.map((a) => (
            <Button key={a.name} variant="default" loading={run.isPending && run.variables?.action === a.name}
              disabled={a.placement === 'selection' && selected.length === 0} onClick={() => execute(a)}>
              {a.label}{a.placement === 'selection' && selected.length > 0 ? ` (${selected.length})` : ''}
            </Button>
          ))}
          {canCreate && <Button onClick={() => setEditing('new')}>+ New</Button>}
        </Group>
      </Group>

      <Paper withBorder radius="md">
        <Stack gap="sm" p="sm">
          <Group justify="space-between" wrap="wrap" gap="xs">
            <FilterBar table={name} fields={filterFields} values={filterValues} onChange={(k, v) => setParam(k, v)} onClear={clearFilters} />
            <TextInput size="xs" w={240} placeholder="Search…" aria-label="Search" value={search}
              onChange={(e) => {
                setSearch(e.currentTarget.value)
                debouncedSearch(e.currentTarget.value)
              }} />
          </Group>
          {m.manageType === 'VIEW' && (
            <Text size="xs" c="dimmed">Read-only view. Rows arrive through the API or directly in the database.</Text>
          )}
        </Stack>
        {records.error ? (
          <Alert color="red" m="sm">{records.error.message}</Alert>
        ) : !page ? (
          <Center p="xl"><Loader /></Center>
        ) : (
          <DataGrid meta={m} rows={page.items} sort={sort} onSort={onSort} selectable={selectable} selected={selected}
            onSelect={setSelected}
            rowActions={(r) => (
              <>
                {rowActions.filter((a) => a.visibleWhen === undefined || test(a.visibleWhen, ctxFor(r))).map((a) => (
                  <Button key={a.name} size="compact-xs" variant="light"
                    loading={run.isPending && run.variables?.id === r.id && run.variables?.action === a.name}
                    onClick={() => execute(a, r)}>{a.label}</Button>
                ))}
                {canEdit && <Button size="compact-xs" variant="default" onClick={() => setEditing(r)}>Edit</Button>}
                {canDelete && <Button size="compact-xs" variant="subtle" color="red" onClick={() => setDeleting(r)}>Delete</Button>}
              </>
            )} />
        )}
        <Group justify="space-between" p="sm">
          <Text size="sm" c="dimmed">{page ? `${page.total} row${page.total === 1 ? '' : 's'}` : ''}</Text>
          <Pagination size="sm" total={pages} value={(page?.page ?? 0) + 1}
            onChange={(p) => setParam('page', p > 1 ? String(p - 1) : null, false)} />
        </Group>
      </Paper>

      {editing && (
        <RecordDrawer meta={m} row={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)}
          onSaved={() => {
            notifications.show({ message: editing === 'new' ? 'Row created' : 'Row saved', color: 'teal' })
            setEditing(null)
          }} />
      )}

      <Modal opened={!!deleting} onClose={() => setDeleting(null)} title="Delete row?" centered>
        <Text size="sm">
          Delete {deleting ? String(deleting[m.view.titleField ?? 'id'] ?? `#${deleting.id}`) : ''} from {m.label}? This cannot be undone.
        </Text>
        {remove.error && <Alert color="red" mt="sm">{remove.error instanceof ApiError ? remove.error.message : String(remove.error)}</Alert>}
        <Group justify="flex-end" mt="md">
          <Button variant="default" onClick={() => setDeleting(null)}>Cancel</Button>
          <Button color="red" loading={remove.isPending}
            onClick={() => deleting && remove.mutate(deleting.id, {
              onSuccess: () => {
                notifications.show({ message: 'Row deleted', color: 'teal' })
                setDeleting(null)
                remove.reset()
              },
            })}>Delete</Button>
        </Group>
      </Modal>

      <ActionResultModal table={name} result={result} onClose={() => setResult(null)} />
    </Stack>
  )
}
