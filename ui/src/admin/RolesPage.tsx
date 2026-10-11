import { Alert, Badge, Button, Group, Modal, Paper, Stack, Table, Text, TextInput, Title } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useState } from 'react'

import { type RoleView, useRoleMutations, useRoles } from '../api/admin'
import { ApiError } from '../api/client'

/** Roles are managed separately from templates; templates reference them by name (ADR-0008). */
export function RolesPage() {
  const roles = useRoles()
  const { create, update, remove } = useRoleMutations()
  const [editing, setEditing] = useState<(Partial<RoleView> & { isNew: boolean }) | null>(null)
  const [error, setError] = useState<string | null>(null)

  const save = () => {
    if (!editing) return
    const m = editing.isNew ? create : update
    m.mutate({ name: editing.name ?? '', description: editing.description }, {
      onSuccess: () => {
        notifications.show({ message: `Role ${editing.name} saved`, color: 'teal' })
        setEditing(null)
        setError(null)
      },
      onError: (e) => setError(e instanceof ApiError ? Object.values(e.errors)[0] ?? e.message : String(e)),
    })
  }

  return (
    <Stack maw={960}>
      <Group justify="space-between">
        <div>
          <Title order={2}>Roles</Title>
          <Text c="dimmed" size="sm">Templates grant operations to roles; assign roles to users on the Users page. Every user has viewer.</Text>
        </div>
        <Button onClick={() => { setEditing({ isNew: true }); setError(null) }}>+ New role</Button>
      </Group>
      {remove.error && <Alert color="red" withCloseButton onClose={() => remove.reset()}>{remove.error.message}</Alert>}
      <Paper withBorder radius="md">
        <Table verticalSpacing="sm">
          <Table.Thead>
            <Table.Tr><Table.Th>Role</Table.Th><Table.Th>Description</Table.Th><Table.Th>Users</Table.Th><Table.Th>Used by templates</Table.Th><Table.Th /></Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {roles.data?.map((r) => (
              <Table.Tr key={r.name}>
                <Table.Td><Group gap={6}><Text ff="monospace" fw={600}>{r.name}</Text>{r.builtin && <Badge size="xs" variant="light">built-in</Badge>}</Group></Table.Td>
                <Table.Td><Text size="sm">{r.description}</Text></Table.Td>
                <Table.Td>{r.name === 'viewer' ? <Text size="sm" c="dimmed">everyone</Text> : r.users}</Table.Td>
                <Table.Td><Group gap={4}>{r.templates.map((t) => <Badge key={t} size="sm" variant="outline">{t}</Badge>)}</Group></Table.Td>
                <Table.Td>
                  <Group gap={6} justify="flex-end" wrap="nowrap">
                    <Button size="compact-xs" variant="default" onClick={() => { setEditing({ ...r, isNew: false }); setError(null) }}>Edit</Button>
                    {!r.builtin && (
                      <Button size="compact-xs" variant="subtle" color="red" disabled={r.users > 0 || r.templates.length > 0}
                        title={r.users > 0 || r.templates.length > 0 ? 'Remove it from users and templates first' : undefined}
                        onClick={() => remove.mutate(r.name)}>Delete</Button>
                    )}
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Paper>

      <Modal opened={!!editing} onClose={() => setEditing(null)} title={editing?.isNew ? 'New role' : `Edit role ${editing?.name}`} centered>
        <Stack>
          <TextInput label="Name" description="lower_snake_case, e.g. operator_lead" disabled={!editing?.isNew}
            value={editing?.name ?? ''} onChange={(e) => editing && setEditing({ ...editing, name: e.currentTarget.value })} />
          <TextInput label="Description" value={editing?.description ?? ''}
            onChange={(e) => editing && setEditing({ ...editing, description: e.currentTarget.value })} />
          {error && <Alert color="red" variant="light">{error}</Alert>}
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setEditing(null)}>Cancel</Button>
            <Button onClick={save} loading={create.isPending || update.isPending}>Save</Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  )
}
