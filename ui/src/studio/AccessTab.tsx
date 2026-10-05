import { Alert, Badge, Checkbox, Paper, ScrollArea, Select, Stack, Table, Text, Title } from '@mantine/core'

import type { FieldOp } from '../grammar/types'
import { effectiveAccess, FIELD_OPS, fieldOps, TABLE_OPS } from './edit'
import { useStudio } from './StudioContext'

const PRESETS: { value: string; label: string }[] = [
  { value: 'inherit', label: 'inherit' }, { value: 'none', label: 'none' }, { value: 'read', label: 'read' },
  { value: 'read,create', label: 'read + create' }, { value: 'read,update', label: 'read + update' },
  { value: 'read,create,update', label: 'read + create + update' },
]

/** Table permissions per role, and per-field narrowing (grammar §6, ADR-0008). */
export function AccessTab() {
  const { doc, update, roles } = useStudio()
  const acc = effectiveAccess(doc)
  const cols = [...TABLE_OPS, 'run:*', ...(doc.actions ?? []).map((a) => `run:${a.name}`)]
  const others = roles.filter((r) => r !== 'admin')

  const toggle = (role: string, op: string) => update((t) => {
    t.access = structuredClone(effectiveAccess(t))
    const ops = new Set(t.access[role] ?? [])
    if (ops.has(op)) ops.delete(op)
    else ops.add(op)
    t.access[role] = [...ops]
  })
  const setField = (index: number, role: string, v: string) => update((t) => {
    const f = t.fields[index]
    if (v === 'inherit') {
      if (f.access) {
        delete f.access[role]
        if (!Object.keys(f.access).length) delete f.access
      }
      return
    }
    f.access = f.access ?? {}
    f.access[role] = v === 'none' ? [] : (v.split(',') as FieldOp[])
  })

  return (
    <Stack maw={1100}>
      {!doc.access && (
        <Alert variant="light">No access block yet, so defaults apply: admin gets everything; viewer gets read{doc.manageType === 'DATA_SOURCE' ? ' on nothing (DATA_SOURCE)' : ''}. Ticking a box writes an explicit block.</Alert>
      )}
      <Paper withBorder radius="md">
        <Title order={5} p="sm">Table permissions</Title>
        <ScrollArea>
          <Table verticalSpacing={6}>
            <Table.Thead>
              <Table.Tr><Table.Th>Role</Table.Th>{cols.map((c) => <Table.Th key={c} ta="center"><Text size="xs" ff="monospace">{c}</Text></Table.Th>)}</Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {roles.map((r) => (
                <Table.Tr key={r}>
                  <Table.Td>
                    <Text ff="monospace" fw={600} span>{r}</Text>{' '}
                    {r === 'admin' && <Badge size="xs" variant="light">built-in, all</Badge>}
                    {r === 'viewer' && <Badge size="xs" variant="light">every user</Badge>}
                  </Table.Td>
                  {cols.map((c) => (
                    <Table.Td key={c} ta="center">
                      <Checkbox aria-label={`${r} ${c}`} disabled={r === 'admin'} checked={r === 'admin' || (acc[r] ?? []).includes(c)}
                        onChange={() => toggle(r, c)} style={{ display: 'inline-block' }} />
                    </Table.Td>
                  ))}
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </ScrollArea>
      </Paper>
      <Paper withBorder radius="md">
        <Title order={5} p="sm">Field overrides <Text span size="sm" c="dimmed" fw={400}>can only narrow table permissions; admin is never narrowed</Text></Title>
        <ScrollArea>
          <Table verticalSpacing={6}>
            <Table.Thead>
              <Table.Tr><Table.Th>Field</Table.Th>{others.map((r) => <Table.Th key={r}><Text size="xs" ff="monospace">{r}</Text></Table.Th>)}</Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {doc.fields.map((f, i) => f.type === 'id' ? null : (
                <Table.Tr key={i}>
                  <Table.Td><Text size="sm" ff="monospace">{f.name}</Text></Table.Td>
                  {others.map((r) => {
                    const explicit = f.access?.[r]
                    const value = explicit === undefined ? 'inherit' : explicit.length ? FIELD_OPS.filter((o) => explicit.includes(o)).join(',') : 'none'
                    return (
                      <Table.Td key={r}>
                        <Select size="xs" w={170} data={PRESETS} value={value} allowDeselect={false} aria-label={`${f.name} ${r}`}
                          onChange={(v) => v && setField(i, r, v)} />
                        <Text size="xs" c="dimmed">effective: {fieldOps(doc, f, r).join(', ') || 'none'}</Text>
                      </Table.Td>
                    )
                  })}
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </ScrollArea>
      </Paper>
    </Stack>
  )
}
