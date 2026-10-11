import { Alert, Button, Drawer, Group, Stack, Text, Title } from '@mantine/core'
import { useMemo, useState } from 'react'

import { useMe } from '../api/auth'
import { ApiError } from '../api/client'
import { type Row, useSaveRecord } from '../api/data'
import type { FieldMeta, TemplateMeta } from '../api/meta'
import { present, test } from '../grammar/jsonlogic'
import { orderedFields } from '../grammar/types'
import { FieldWidget } from './FieldWidget'

interface Props {
  meta: TemplateMeta
  /** Row to edit; undefined to create. */
  row?: Row
  onClose: () => void
  onSaved: (row: Row) => void
}

function initialValues(meta: TemplateMeta, row: Row | undefined, username: string): Record<string, unknown> {
  if (row) return { ...row }
  const v: Record<string, unknown> = {}
  for (const f of meta.fields) {
    if (f.default === undefined || f.type === 'id') continue
    const d = f.default as { fn?: string } | unknown
    if (d && typeof d === 'object' && 'fn' in (d as object)) {
      const fn = (d as { fn: string }).fn
      v[f.name] = fn === 'now' ? new Date().toISOString() : fn === 'today' ? new Date().toISOString().slice(0, 10)
        : fn === 'currentUser' ? username : null
    } else {
      v[f.name] = d
    }
  }
  return v
}

/** Create / edit form generated from the template (MANAGE_VIEW, admin data browser). */
export function RecordDrawer({ meta, row, onClose, onSaved }: Props) {
  const { data: me } = useMe()
  const save = useSaveRecord(meta.name)
  const op = row ? 'update' : 'create'
  const [values, setValues] = useState<Record<string, unknown>>(() => initialValues(meta, row, me?.username ?? ''))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [general, setGeneral] = useState<string[]>([])
  const [conflict, setConflict] = useState(false)

  const ctx = useMemo(
    () => ({ ...values, $op: op, $user: { username: me?.username, roles: me?.roles ?? [] } }),
    [values, op, me],
  )

  const fields = orderedFields(meta.fields).filter((f) => f.type !== 'id' && (f.ops.includes(op) || (row && f.ops.includes('read'))))
  const visible = fields.filter((f) => f.ui?.visibleWhen === undefined || test(f.ui.visibleWhen, ctx))
  const groups: { name: string; fields: FieldMeta[] }[] = []
  for (const f of visible) {
    const name = f.ui?.group ?? ''
    let g = groups.find((x) => x.name === name)
    if (!g) groups.push((g = { name, fields: [] }))
    g.fields.push(f)
  }

  const isRequired = (f: FieldMeta) => !!f.required || (f.requiredWhen !== undefined && test(f.requiredWhen, ctx))
  const isReadOnly = (f: FieldMeta) => !f.ops.includes(op) || (f.ui?.readonlyWhen !== undefined && test(f.ui.readonlyWhen, ctx))

  const submit = () => {
    const clientErrors: Record<string, string> = {}
    for (const f of visible) {
      if (!isReadOnly(f) && isRequired(f) && !present(values[f.name])) clientErrors[f.name] = 'Required'
    }
    setErrors(clientErrors)
    setGeneral([])
    if (Object.keys(clientErrors).length) return

    const body: Record<string, unknown> = {}
    for (const f of fields) {
      if (!f.ops.includes(op)) continue
      if (row && JSON.stringify(values[f.name] ?? null) === JSON.stringify(row[f.name] ?? null)) continue
      body[f.name] = values[f.name] ?? null
    }
    if (row && meta.rowVersion) body.row_version = row.row_version
    save.mutate(
      { id: row?.id, body },
      {
        onSuccess: onSaved,
        onError: (e) => {
          if (!(e instanceof ApiError)) return setGeneral([String(e)])
          setErrors(e.errors)
          setGeneral(e.general.length ? e.general : Object.keys(e.errors).length ? [] : [e.message])
          setConflict(e.code === 'CONFLICT_VERSION')
        },
      },
    )
  }

  return (
    <Drawer opened onClose={onClose} position="right" size="md"
      title={<Title order={4}>{row ? `Edit ${meta.label}` : `New ${meta.label}`}</Title>}>
      <Stack>
        {groups.map((g) => (
          <Stack key={g.name || '_'} gap="sm">
            {g.name && <Text size="xs" fw={700} c="dimmed" tt="uppercase">{g.name}</Text>}
            {g.fields.map((f) => (
              <FieldWidget key={f.name} table={meta.name} field={f} value={values[f.name]}
                refLabel={row?.[`${f.name}$display`] as string | undefined}
                disabled={isReadOnly(f)} required={!isReadOnly(f) && isRequired(f)} error={errors[f.name]}
                onChange={(v) => setValues((old) => ({ ...old, [f.name]: v }))} />
            ))}
          </Stack>
        ))}
        {general.map((m) => (
          <Alert key={m} color="red" variant="light">{m}</Alert>
        ))}
        {conflict && (
          <Alert color="orange" variant="light" title="Changed by someone else">
            Close this form and open the row again to see the latest version.
          </Alert>
        )}
        <Group justify="flex-end" mt="sm">
          <Button variant="default" onClick={onClose}>Cancel</Button>
          <Button onClick={submit} loading={save.isPending}>{row ? 'Save' : 'Create'}</Button>
        </Group>
        {row && Object.keys(errors).some((k) => !fields.some((f) => f.name === k)) && (
          <Text size="sm" c="red">
            {Object.entries(errors).filter(([k]) => !fields.some((f) => f.name === k)).map(([k, v]) => `${k}: ${v}`).join('; ')}
          </Text>
        )}
        {fields.length === 0 && <Text c="dimmed">There are no fields you can edit in {meta.label}.</Text>}
      </Stack>
    </Drawer>
  )
}
