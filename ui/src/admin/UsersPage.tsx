import { Alert, Badge, Button, Group, Modal, MultiSelect, Paper, PasswordInput, Stack, Switch, Table, Text, TextInput, Title } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useState } from 'react'

import { useMe } from '../api/auth'
import { type UserView, useRoles, useUserMutations, useUsers } from '../api/admin'
import { ApiError } from '../api/client'

interface Form {
  isNew: boolean
  username: string
  displayName: string
  enabled: boolean
  roles: string[]
  password: string
}

const fromUser = (u: UserView): Form => ({
  isNew: false, username: u.username, displayName: u.displayName ?? '', enabled: u.enabled,
  roles: u.roles.filter((r) => r !== 'viewer'), password: '',
})

/** Users and their roles (requirement: admin adds roles and assigns them to users). */
export function UsersPage() {
  const { data: me } = useMe()
  const users = useUsers()
  const roles = useRoles()
  const { create, update, resetPassword } = useUserMutations()
  const [form, setForm] = useState<Form | null>(null)
  const [resetFor, setResetFor] = useState<string | null>(null)
  const [tempPassword, setTempPassword] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [general, setGeneral] = useState<string | null>(null)

  const roleOptions = (roles.data ?? []).filter((r) => r.name !== 'viewer').map((r) => r.name)
  const fail = (e: unknown) => {
    if (e instanceof ApiError) {
      setErrors(e.errors)
      setGeneral(Object.keys(e.errors).length ? null : e.message)
    } else setGeneral(String(e))
  }

  const save = () => {
    if (!form) return
    setErrors({})
    setGeneral(null)
    const done = () => {
      notifications.show({ message: `User ${form.username} saved`, color: 'teal' })
      setForm(null)
    }
    if (form.isNew) {
      create.mutate({ username: form.username, displayName: form.displayName || undefined, password: form.password, roles: form.roles },
        { onSuccess: done, onError: fail })
    } else {
      update.mutate({ username: form.username, displayName: form.displayName || undefined, enabled: form.enabled, roles: form.roles },
        { onSuccess: done, onError: fail })
    }
  }

  return (
    <Stack maw={1040}>
      <Group justify="space-between">
        <div>
          <Title order={2}>Users</Title>
          <Text c="dimmed" size="sm">New users and password resets get a temporary password they must change at first sign-in.</Text>
        </div>
        <Button onClick={() => { setForm({ isNew: true, username: '', displayName: '', enabled: true, roles: [], password: '' }); setErrors({}); setGeneral(null) }}>
          + New user
        </Button>
      </Group>
      <Paper withBorder radius="md">
        <Table verticalSpacing="sm">
          <Table.Thead>
            <Table.Tr><Table.Th>User</Table.Th><Table.Th>Roles</Table.Th><Table.Th>Status</Table.Th><Table.Th /></Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {users.data?.map((u) => (
              <Table.Tr key={u.username}>
                <Table.Td>
                  <Text fw={600} size="sm">{u.displayName || u.username}</Text>
                  <Text size="xs" c="dimmed" ff="monospace">{u.username}</Text>
                </Table.Td>
                <Table.Td><Group gap={4}>{u.roles.map((r) => <Badge key={r} size="sm" variant={r === 'viewer' ? 'outline' : 'light'}>{r}</Badge>)}</Group></Table.Td>
                <Table.Td>
                  <Group gap={4}>
                    <Badge size="sm" color={u.enabled ? 'teal' : 'gray'} variant="light">{u.enabled ? 'active' : 'disabled'}</Badge>
                    {u.mustChangePassword && <Badge size="sm" color="orange" variant="light">must change password</Badge>}
                  </Group>
                </Table.Td>
                <Table.Td>
                  <Group gap={6} justify="flex-end" wrap="nowrap">
                    <Button size="compact-xs" variant="default" onClick={() => { setForm(fromUser(u)); setErrors({}); setGeneral(null) }}>Edit</Button>
                    <Button size="compact-xs" variant="subtle" onClick={() => { setResetFor(u.username); setTempPassword(''); setErrors({}); setGeneral(null) }}>
                      Reset password
                    </Button>
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Paper>

      <Modal opened={!!form} onClose={() => setForm(null)} title={form?.isNew ? 'New user' : `Edit ${form?.username}`} centered>
        {form && (
          <Stack>
            <TextInput label="Username" disabled={!form.isNew} value={form.username} error={errors.username}
              onChange={(e) => setForm({ ...form, username: e.currentTarget.value })} />
            <TextInput label="Display name" value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.currentTarget.value })} />
            {form.isNew && (
              <PasswordInput label="Temporary password" description="At least 10 characters; the user changes it at first sign-in"
                value={form.password} error={errors.password} onChange={(e) => setForm({ ...form, password: e.currentTarget.value })} />
            )}
            <MultiSelect label="Roles" description="viewer is always included" data={roleOptions} value={form.roles}
              onChange={(v) => setForm({ ...form, roles: v })} />
            {!form.isNew && (
              <Switch label="Active" checked={form.enabled} disabled={form.username === me?.username}
                onChange={(e) => setForm({ ...form, enabled: e.currentTarget.checked })} />
            )}
            {general && <Alert color="red" variant="light">{general}</Alert>}
            <Group justify="flex-end">
              <Button variant="default" onClick={() => setForm(null)}>Cancel</Button>
              <Button onClick={save} loading={create.isPending || update.isPending}>Save</Button>
            </Group>
          </Stack>
        )}
      </Modal>

      <Modal opened={!!resetFor} onClose={() => setResetFor(null)} title={`Reset password for ${resetFor}`} centered>
        <Stack>
          <PasswordInput label="Temporary password" description="At least 10 characters" value={tempPassword} error={errors.password}
            onChange={(e) => setTempPassword(e.currentTarget.value)} />
          {general && <Alert color="red" variant="light">{general}</Alert>}
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setResetFor(null)}>Cancel</Button>
            <Button loading={resetPassword.isPending}
              onClick={() => resetFor && resetPassword.mutate({ username: resetFor, password: tempPassword }, {
                onSuccess: () => {
                  notifications.show({ message: `Password reset for ${resetFor}`, color: 'teal' })
                  setResetFor(null)
                },
                onError: fail,
              })}>Reset</Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  )
}
