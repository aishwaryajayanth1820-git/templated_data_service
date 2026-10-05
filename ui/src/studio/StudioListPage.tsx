import { Alert, Badge, Button, FileButton, Group, Modal, Paper, Radio, Stack, Table, Text, TextInput, Title } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useState } from 'react'
import { useNavigate } from 'react-router'

import { useTemplateMutations, useTemplates } from '../api/admin'
import { ApiError } from '../api/client'
import type { ManageType, TemplateDoc } from '../grammar/types'
import { MANAGE_COLORS, MANAGE_TYPES } from './constants'
import { StatusBadge } from './StatusBadge'

/** Schema Studio home: all templates with their status. */
export function StudioListPage() {
  const templates = useTemplates()
  const { create, importDoc } = useTemplateMutations()
  const navigate = useNavigate()
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState({ name: '', label: '', manageType: 'MANAGE_VIEW' as ManageType })
  const [error, setError] = useState<string | null>(null)

  const submit = () => {
    const doc: TemplateDoc = {
      grammar: 'tds/v1', name: form.name, label: form.label || form.name, manageType: form.manageType,
      fields: [{ name: 'id', type: 'id', ui: { placement: 'hidden' } }],
      access: { admin: ['read', 'create', 'update', 'delete', 'run:*'], viewer: form.manageType === 'DATA_SOURCE' ? [] : ['read'] },
      view: { pageSize: 25 },
    }
    create.mutate(doc, {
      onSuccess: (d) => navigate(`/admin/studio/${d.name}`),
      onError: (e) => setError(e instanceof ApiError ? Object.values(e.errors)[0] ?? e.message : String(e)),
    })
  }

  const onImport = async (file: File | null) => {
    if (!file) return
    try {
      const doc = JSON.parse(await file.text()) as TemplateDoc
      importDoc.mutate(doc, {
        onSuccess: (d) => {
          notifications.show({ message: `Imported ${d.name} as draft`, color: 'teal' })
          navigate(`/admin/studio/${d.name}`)
        },
        onError: (e) => notifications.show({ title: 'Import failed', message: e.message, color: 'red' }),
      })
    } catch (e) {
      notifications.show({ title: 'Import failed', message: `Not valid JSON: ${(e as Error).message}`, color: 'red' })
    }
  }

  return (
    <Stack maw={1100}>
      <Group justify="space-between">
        <div>
          <Title order={2}>Schema Studio</Title>
          <Text c="dimmed" size="sm">Define tables, their UI, access and actions. Publishing creates or migrates the table, with no restart.</Text>
        </div>
        <Group>
          <FileButton onChange={onImport} accept="application/json,.json">
            {(props) => <Button variant="default" {...props}>Import JSON</Button>}
          </FileButton>
          <Button onClick={() => { setCreating(true); setError(null) }}>+ New template</Button>
        </Group>
      </Group>
      <Paper withBorder radius="md">
        <Table highlightOnHover verticalSpacing="sm">
          <Table.Thead>
            <Table.Tr><Table.Th>Template</Table.Th><Table.Th>Manage type</Table.Th><Table.Th>Status</Table.Th><Table.Th>Validation</Table.Th><Table.Th>Updated</Table.Th></Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {templates.data?.map((t) => {
              const errors = t.issues.filter((i) => i.severity === 'ERROR').length
              const warnings = t.issues.length - errors
              return (
                <Table.Tr key={t.name} style={{ cursor: 'pointer' }} onClick={() => navigate(`/admin/studio/${t.name}`)}>
                  <Table.Td><Text fw={600}>{t.label ?? t.name}</Text><Text size="xs" c="dimmed" ff="monospace">{t.name}</Text></Table.Td>
                  <Table.Td><Badge variant="light" color={MANAGE_COLORS[t.manageType]}>{t.manageType.replace('_', ' ')}</Badge></Table.Td>
                  <Table.Td><StatusBadge t={t} /></Table.Td>
                  <Table.Td>
                    {errors > 0 ? <Badge color="red" variant="light">{errors} error{errors > 1 ? 's' : ''}</Badge>
                      : warnings > 0 ? <Badge color="yellow" variant="light">{warnings} warning{warnings > 1 ? 's' : ''}</Badge>
                        : <Badge color="teal" variant="light">valid</Badge>}
                  </Table.Td>
                  <Table.Td><Text size="xs" c="dimmed">{new Date(t.updatedAt).toLocaleString()} · {t.updatedBy}</Text></Table.Td>
                </Table.Tr>
              )
            })}
          </Table.Tbody>
        </Table>
      </Paper>

      <Modal opened={creating} onClose={() => setCreating(false)} title="New template" centered>
        <Stack>
          <TextInput label="Table name" description="snake_case; cannot change after the first publish" placeholder="jackpot_limits"
            value={form.name} onChange={(e) => setForm({ ...form, name: e.currentTarget.value })} />
          <TextInput label="Label" placeholder="Jackpot Limits" value={form.label} onChange={(e) => setForm({ ...form, label: e.currentTarget.value })} />
          <Radio.Group label="Manage type" value={form.manageType} onChange={(v) => setForm({ ...form, manageType: v as ManageType })}>
            <Stack gap={6} mt={6}>
              {(Object.keys(MANAGE_TYPES) as ManageType[]).map((k) => (
                <Radio key={k} value={k} label={<><b>{k}</b> <Text span size="sm" c="dimmed">{MANAGE_TYPES[k]}</Text></>} />
              ))}
            </Stack>
          </Radio.Group>
          {error && <Alert color="red" variant="light">{error}</Alert>}
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setCreating(false)}>Cancel</Button>
            <Button onClick={submit} loading={create.isPending} disabled={!form.name}>Create draft</Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  )
}
