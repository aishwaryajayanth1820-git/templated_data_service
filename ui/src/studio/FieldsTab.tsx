import {
  ActionIcon, Badge, Button, Checkbox, ColorSwatch, Grid, Group, NumberInput, Paper, ScrollArea, SegmentedControl, Select, Stack,
  Table, Text, TextInput, Title,
} from '@mantine/core'
import { useState } from 'react'

import type { EnumValueObj, FieldDef, FieldType, TemplateDoc } from '../grammar/types'
import { enumValues, FIELD_TYPES, isNumeric, isStringy, isTemporal, placementOf, TYPE_WIDGETS } from '../grammar/types'
import { dropRefs, fieldOps, freshName, renameRefs, setOrDel, setSub } from './edit'
import { LogicInput } from './LogicInput'
import { useStudio } from './StudioContext'

const COLORS = ['grey', 'red', 'orange', 'yellow', 'green', 'blue', 'purple']
const SWATCH: Record<string, string> = { grey: 'gray', purple: 'violet' }

function typeSummary(f: FieldDef) {
  if (f.type === 'string') return `string(${f.length ?? 255})`
  if (f.type === 'decimal') return `decimal(${f.precision ?? 18},${f.scale ?? 2})`
  if (f.type === 'enum') return `enum[${enumValues(f).length}]`
  if (f.type === 'ref') return `ref → ${f.ref?.target ?? '?'}`
  return f.type
}

export function FieldsTab({ selected, onSelect }: { selected: number; onSelect: (i: number) => void }) {
  const { doc, update, issues } = useStudio()
  const idx = Math.min(selected, doc.fields.length - 1)

  const move = (i: number, d: number) => {
    const j = i + d
    if (j < 0 || j >= doc.fields.length) return
    update((t) => {
      const a = t.fields
      ;[a[i], a[j]] = [a[j], a[i]]
    })
    onSelect(j)
  }
  const add = () => {
    const name = freshName(doc.fields.map((f) => f.name), 'new_field')
    update((t) => t.fields.push({ name, label: 'New field', type: 'string', length: 255, ui: { placement: 'column' } }))
    onSelect(doc.fields.length)
  }

  return (
    <Grid gap="md">
      <Grid.Col span={{ base: 12, lg: 6 }}>
        <Paper withBorder radius="md">
          <Group justify="space-between" p="sm">
            <Title order={5}>Fields <Badge variant="light" ml={4}>{doc.fields.length}</Badge></Title>
            <Button size="xs" onClick={add}>+ Add field</Button>
          </Group>
          <ScrollArea>
            <Table highlightOnHover verticalSpacing={6}>
              <Table.Thead>
                <Table.Tr><Table.Th w={64} /><Table.Th>Name</Table.Th><Table.Th>Type</Table.Th><Table.Th>Req</Table.Th><Table.Th>Uniq</Table.Th><Table.Th>Placement</Table.Th><Table.Th /></Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {doc.fields.map((f, i) => {
                  const fi = issues.filter((x) => x.path === `fields[${i}]` || x.path.startsWith(`fields[${i}].`))
                  const errs = fi.filter((x) => x.severity === 'ERROR').length
                  return (
                    <Table.Tr key={i} onClick={() => onSelect(i)} style={{ cursor: 'pointer' }}
                      bg={i === idx ? 'var(--mantine-primary-color-light)' : undefined}>
                      <Table.Td>
                        <Group gap={0} wrap="nowrap">
                          <ActionIcon size="sm" variant="subtle" aria-label="Move up" onClick={(e) => { e.stopPropagation(); move(i, -1) }}>↑</ActionIcon>
                          <ActionIcon size="sm" variant="subtle" aria-label="Move down" onClick={(e) => { e.stopPropagation(); move(i, 1) }}>↓</ActionIcon>
                        </Group>
                      </Table.Td>
                      <Table.Td>
                        <Text size="sm" ff="monospace" fw={600}>{f.name}</Text>
                        {f.label && <Text size="xs" c="dimmed">{f.label}</Text>}
                      </Table.Td>
                      <Table.Td><Text size="sm" ff="monospace">{typeSummary(f)}</Text></Table.Td>
                      <Table.Td>{f.type === 'id' ? <Text size="xs" c="dimmed">PK</Text> : f.required ? '✓' : f.requiredWhen ? <Badge size="xs" variant="light">when</Badge> : ''}</Table.Td>
                      <Table.Td>{f.unique ? '✓' : ''}</Table.Td>
                      <Table.Td><Badge size="sm" variant="outline" color="gray">{doc.manageType === 'DATA_SOURCE' ? 'n/a' : placementOf(f)}</Badge></Table.Td>
                      <Table.Td>{fi.length > 0 && <Badge size="sm" color={errs ? 'red' : 'yellow'} title={fi.map((x) => x.message).join('\n')}>{fi.length}</Badge>}</Table.Td>
                    </Table.Tr>
                  )
                })}
              </Table.Tbody>
            </Table>
          </ScrollArea>
        </Paper>
      </Grid.Col>
      <Grid.Col span={{ base: 12, lg: 6 }}>
        {doc.fields[idx] && (
          // Keyed by name too: the name input is an edit buffer that must reset after a rename commits.
          <FieldInspector key={`${idx}:${doc.fields[idx].name}`} index={idx} onDeleted={() => onSelect(Math.max(0, idx - 1))} />
        )}
      </Grid.Col>
    </Grid>
  )
}

function Section({ title, children, extra }: { title: string; children: React.ReactNode; extra?: React.ReactNode }) {
  return (
    <Stack gap="xs">
      <Group gap="xs"><Text size="xs" fw={700} c="dimmed" tt="uppercase">{title}</Text>{extra}</Group>
      {children}
    </Stack>
  )
}

function FieldInspector({ index, onDeleted }: { index: number; onDeleted: () => void }) {
  const { doc, update, published, templates, roles } = useStudio()
  const f = doc.fields[index]
  const set = (fn: (x: FieldDef, t: TemplateDoc) => void) => update((t) => fn(t.fields[index], t))
  const publishedNames = new Set((published?.fields ?? []).map((x) => x.name))
  const isPublished = publishedNames.has(f.name) || (!!f.renamedFrom && publishedNames.has(f.renamedFrom))
  const [name, setName] = useState(f.name)
  const c = f.constraints ?? {}
  const others = templates.filter((t) => t.name !== doc.name)
  const ds = doc.manageType === 'DATA_SOURCE'

  const commitName = () => set((x, t) => {
    const from = x.name
    if (!name || name === from) return
    if (!x.renamedFrom && publishedNames.has(from)) x.renamedFrom = from
    x.name = name
    if (x.renamedFrom === name) delete x.renamedFrom
    if (!t.fields.some((y, i) => i !== index && y.name === name)) renameRefs(t, from, name)
  })

  const setType = (type: FieldType) => set((x) => {
    for (const k of ['length', 'precision', 'scale', 'values', 'ref', 'default', 'constraints'] as const) delete x[k]
    x.type = type
    if (type === 'string') x.length = 255
    if (type === 'enum') x.values = ['VALUE_1']
    if (type === 'ref') {
      const target = others[0]
      const display = target?.json.fields.find((y) => y.type !== 'id')?.name ?? 'id'
      x.ref = { target: target?.name ?? '', display, onDelete: 'RESTRICT' }
    }
    if (x.ui?.widget && !TYPE_WIDGETS[type].includes(x.ui.widget)) setSub(x, 'ui', 'widget', undefined)
  })

  const remove = () => {
    update((t) => {
      const [gone] = t.fields.splice(index, 1)
      dropRefs(t, gone.name)
    })
    onDeleted()
  }

  if (f.type === 'id') {
    return (
      <Paper withBorder radius="md" p="md">
        <Stack>
          <Group><Title order={5} ff="monospace">{f.name}</Title><Badge variant="light">system primary key</Badge></Group>
          <Text size="sm" c="dimmed">Surrogate key: SQLite INTEGER PRIMARY KEY AUTOINCREMENT, PostgreSQL BIGINT IDENTITY. The system assigns it; it is read-only for everyone.</Text>
          <TextInput label="Name" ff="monospace" value={name} disabled={isPublished}
            onChange={(e) => setName(e.currentTarget.value)} onBlur={commitName} />
          <Select label="Placement in view grid" data={['hidden', 'column', 'detail']} value={placementOf(f)}
            onChange={(v) => set((x) => setSub(x, 'ui', 'placement', v ?? undefined))} />
        </Stack>
      </Paper>
    )
  }

  const literal = f.default !== undefined && !(typeof f.default === 'object' && f.default !== null && 'fn' in (f.default as object))
  const defMode = f.default === undefined ? '' : literal ? 'value' : `fn:${(f.default as { fn: string }).fn}`
  const fnOptions: Record<string, string[]> = { datetime: ['now'], date: ['today'], uuid: ['uuid'], string: ['currentUser'], text: ['currentUser'] }
  const target = templates.find((t) => t.name === f.ref?.target)

  return (
    <Paper withBorder radius="md" p="md">
      <Stack gap="lg">
        <Group justify="space-between">
          <Group gap="xs">
            <Title order={5} ff="monospace">{f.name}</Title>
            {f.renamedFrom && <Badge variant="light">renamed from {f.renamedFrom}</Badge>}
          </Group>
          <Button size="xs" variant="subtle" color="red" onClick={remove}>Delete field</Button>
        </Group>

        <Section title="Basics">
          <Grid gap="sm">
            <Grid.Col span={6}>
              <TextInput label="Name (column)" ff="monospace" value={name} description={isPublished ? 'Renaming keeps the data' : 'snake_case'}
                onChange={(e) => setName(e.currentTarget.value)} onBlur={commitName} />
            </Grid.Col>
            <Grid.Col span={6}>
              <TextInput label="Label" value={f.label ?? ''} onChange={(e) => set((x) => setOrDel(x, 'label', e.currentTarget.value))} />
            </Grid.Col>
            <Grid.Col span={6}>
              <Select label="Type" data={FIELD_TYPES} value={f.type} disabled={isPublished} allowDeselect={false}
                description={isPublished ? 'Immutable after publish (ADR-0013)' : undefined} onChange={(v) => v && setType(v as FieldType)} />
            </Grid.Col>
            {f.type === 'string' && (
              <Grid.Col span={6}>
                <NumberInput label="Length" min={1} value={f.length ?? 255} onChange={(v) => set((x) => setOrDel(x, 'length', v === '' ? undefined : Number(v)))} />
              </Grid.Col>
            )}
            {f.type === 'decimal' && (
              <>
                <Grid.Col span={3}><NumberInput label="Precision" value={f.precision ?? ''} onChange={(v) => set((x) => setOrDel(x, 'precision', v === '' ? undefined : Number(v)))} /></Grid.Col>
                <Grid.Col span={3}><NumberInput label="Scale" value={f.scale ?? ''} onChange={(v) => set((x) => setOrDel(x, 'scale', v === '' ? undefined : Number(v)))} /></Grid.Col>
              </>
            )}
            <Grid.Col span={12}>
              <TextInput label="Description" value={f.description ?? ''} onChange={(e) => set((x) => setOrDel(x, 'description', e.currentTarget.value))} />
            </Grid.Col>
          </Grid>
        </Section>

        {f.type === 'enum' && (
          <Section title="Enum values">
            <Table verticalSpacing={4}>
              <Table.Tbody>
                {enumValues(f).map((e, i) => (
                  <Table.Tr key={i}>
                    <Table.Td><TextInput size="xs" ff="monospace" aria-label="Value" value={e.value}
                      onChange={(ev) => set((x) => { const vs = enumValues(x); vs[i] = { ...vs[i], value: ev.currentTarget.value }; x.values = vs })} /></Table.Td>
                    <Table.Td><TextInput size="xs" aria-label="Label" placeholder="Label" value={e.label ?? ''}
                      onChange={(ev) => set((x) => { const vs = enumValues(x); setOrDel(vs[i], 'label', ev.currentTarget.value); x.values = vs })} /></Table.Td>
                    <Table.Td>
                      <Select size="xs" w={110} aria-label="Colour" placeholder="colour" clearable data={COLORS} value={e.color ?? null}
                        leftSection={e.color ? <ColorSwatch size={12} color={`var(--mantine-color-${SWATCH[e.color] ?? e.color}-6)`} /> : undefined}
                        onChange={(v) => set((x) => { const vs = enumValues(x); setOrDel(vs[i] as EnumValueObj, 'color', v ?? undefined); x.values = vs })} />
                    </Table.Td>
                    <Table.Td><ActionIcon variant="subtle" color="red" aria-label="Remove value"
                      onClick={() => set((x) => { const vs = enumValues(x); vs.splice(i, 1); x.values = vs })}>✕</ActionIcon></Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            <Button size="xs" variant="default" w="fit-content"
              onClick={() => set((x) => { x.values = [...enumValues(x), { value: `VALUE_${enumValues(x).length + 1}` }] })}>+ Value</Button>
          </Section>
        )}

        {f.type === 'ref' && (
          <Section title="Reference">
            <Grid gap="sm">
              <Grid.Col span={6}>
                <Select label="Target table" data={others.map((t) => t.name)} value={f.ref?.target ?? null} allowDeselect={false}
                  onChange={(v) => set((x) => {
                    const tgt = templates.find((t) => t.name === v)
                    x.ref = { ...x.ref!, target: v ?? '', display: tgt?.json.fields.find((y) => y.type !== 'id')?.name ?? 'id' }
                  })} />
              </Grid.Col>
              <Grid.Col span={6}>
                <Select label="Display field" data={(target?.json.fields ?? []).map((y) => y.name)} value={f.ref?.display ?? null} allowDeselect={false}
                  onChange={(v) => set((x) => { x.ref = { ...x.ref!, display: v ?? 'id' } })} />
              </Grid.Col>
              <Grid.Col span={6}>
                <Select label="When the target row is deleted" data={['RESTRICT', 'CASCADE', 'SET_NULL']} value={f.ref?.onDelete ?? 'RESTRICT'} allowDeselect={false}
                  onChange={(v) => set((x) => { x.ref = { ...x.ref!, onDelete: (v ?? 'RESTRICT') as 'RESTRICT' } })} />
              </Grid.Col>
            </Grid>
          </Section>
        )}

        <Section title="Constraints">
          <Group gap="lg">
            <Checkbox label="Required" checked={!!f.required} onChange={(e) => set((x) => setOrDel(x, 'required', e.currentTarget.checked || undefined))} />
            {f.type !== 'json' && f.type !== 'text' && (
              <Checkbox label="Unique" checked={!!f.unique} onChange={(e) => set((x) => setOrDel(x, 'unique', e.currentTarget.checked || undefined))} />
            )}
            {isStringy(f.type) && (
              <Checkbox label="Not blank" checked={!!c.notBlank} onChange={(e) => set((x) => setSub(x, 'constraints', 'notBlank', e.currentTarget.checked || undefined))} />
            )}
          </Group>
          <Grid gap="sm">
            <Grid.Col span={6}>
              <Select label="Default" value={defMode} allowDeselect={false}
                data={[{ value: '', label: 'none' }, { value: 'value', label: 'literal value' }, ...(fnOptions[f.type] ?? []).map((fn) => ({ value: `fn:${fn}`, label: `${fn}()` }))]}
                onChange={(v) => set((x) => {
                  if (!v) delete x.default
                  else if (v === 'value') x.default = x.type === 'enum' ? enumValues(x)[0]?.value : x.type === 'boolean' ? false : isNumeric(x.type) ? 0 : ''
                  else x.default = { fn: v.slice(3) }
                })} />
            </Grid.Col>
            {literal && (
              <Grid.Col span={6}>
                {f.type === 'enum' ? (
                  <Select label="Default value" data={enumValues(f).map((e) => e.value)} value={String(f.default ?? '')} onChange={(v) => set((x) => { x.default = v })} />
                ) : f.type === 'boolean' ? (
                  <Select label="Default value" data={['true', 'false']} value={String(f.default)} onChange={(v) => set((x) => { x.default = v === 'true' })} />
                ) : isNumeric(f.type) ? (
                  <NumberInput label="Default value" value={Number(f.default ?? 0)} onChange={(v) => set((x) => { x.default = Number(v) || 0 })} />
                ) : (
                  <TextInput label="Default value" value={String(f.default ?? '')} onChange={(e) => set((x) => { x.default = e.currentTarget.value })} />
                )}
              </Grid.Col>
            )}
            {isStringy(f.type) && (
              <>
                <Grid.Col span={6}><NumberInput label="Min length" value={c.minLength ?? ''} onChange={(v) => set((x) => setSub(x, 'constraints', 'minLength', v === '' ? undefined : Number(v)))} /></Grid.Col>
                <Grid.Col span={6}><NumberInput label="Max length" value={c.maxLength ?? ''} onChange={(v) => set((x) => setSub(x, 'constraints', 'maxLength', v === '' ? undefined : Number(v)))} /></Grid.Col>
              </>
            )}
            {f.type === 'string' && (
              <>
                <Grid.Col span={6}><TextInput label="Pattern (regex)" ff="monospace" description="Enforced by the service" value={c.pattern ?? ''}
                  onChange={(e) => set((x) => setSub(x, 'constraints', 'pattern', e.currentTarget.value))} /></Grid.Col>
                <Grid.Col span={6}><Select label="Format" clearable data={['email', 'url', 'hostname', 'ipv4']} value={c.format ?? null}
                  onChange={(v) => set((x) => setSub(x, 'constraints', 'format', v ?? undefined))} /></Grid.Col>
              </>
            )}
            {isNumeric(f.type) && (
              <>
                <Grid.Col span={6}><NumberInput label="Min" value={typeof c.min === 'number' ? c.min : ''} onChange={(v) => set((x) => setSub(x, 'constraints', 'min', v === '' ? undefined : Number(v)))} /></Grid.Col>
                <Grid.Col span={6}><NumberInput label="Max" value={typeof c.max === 'number' ? c.max : ''} onChange={(v) => set((x) => setSub(x, 'constraints', 'max', v === '' ? undefined : Number(v)))} /></Grid.Col>
              </>
            )}
            {isTemporal(f.type) && (
              <>
                <Grid.Col span={6}><TextInput label="Min" ff="monospace" placeholder="ISO value" value={String(c.min ?? '')} onChange={(e) => set((x) => setSub(x, 'constraints', 'min', e.currentTarget.value))} /></Grid.Col>
                <Grid.Col span={6}><TextInput label="Max" ff="monospace" placeholder="ISO value" value={String(c.max ?? '')} onChange={(e) => set((x) => setSub(x, 'constraints', 'max', e.currentTarget.value))} /></Grid.Col>
              </>
            )}
          </Grid>
          <LogicInput label="Required when" value={f.requiredWhen} onChange={(v) => set((x) => setOrDel(x, 'requiredWhen', v))} />
        </Section>

        <Section title="UI" extra={ds ? <Badge size="xs" color="yellow" variant="light">ignored for DATA_SOURCE</Badge> : undefined}>
          <Grid gap="sm">
            <Grid.Col span={12}>
              <Text size="sm" fw={500}>Placement in view grid</Text>
              <SegmentedControl size="xs" data={['column', 'detail', 'hidden']} value={placementOf(f)}
                onChange={(v) => set((x) => setSub(x, 'ui', 'placement', v))} />
              <Text size="xs" c="dimmed">column = row label · detail = expanded row · hidden = not shown</Text>
            </Grid.Col>
            <Grid.Col span={6}>
              <Select label="Input widget" value={f.ui?.widget ?? ''} allowDeselect={false}
                data={[{ value: '', label: `default (${TYPE_WIDGETS[f.type][0] ?? '—'})` }, ...TYPE_WIDGETS[f.type].map((w) => ({ value: w, label: w }))]}
                onChange={(v) => set((x) => setSub(x, 'ui', 'widget', v || undefined))} />
            </Grid.Col>
            <Grid.Col span={6}><NumberInput label="Column width (px)" value={f.ui?.width ?? ''} onChange={(v) => set((x) => setSub(x, 'ui', 'width', v === '' ? undefined : Number(v)))} /></Grid.Col>
            <Grid.Col span={6}><TextInput label="Form group" value={f.ui?.group ?? ''} onChange={(e) => set((x) => setSub(x, 'ui', 'group', e.currentTarget.value))} /></Grid.Col>
            <Grid.Col span={6}><TextInput label="Display format" ff="monospace" placeholder={f.type === 'datetime' ? 'yyyy-MM-dd HH:mm' : isNumeric(f.type) ? "0.00 's'" : ''}
              value={f.ui?.format ?? ''} onChange={(e) => set((x) => setSub(x, 'ui', 'format', e.currentTarget.value))} /></Grid.Col>
            <Grid.Col span={6}><TextInput label="Help text" value={f.ui?.help ?? ''} onChange={(e) => set((x) => setSub(x, 'ui', 'help', e.currentTarget.value))} /></Grid.Col>
            <Grid.Col span={6}><TextInput label="Placeholder" value={f.ui?.placeholder ?? ''} onChange={(e) => set((x) => setSub(x, 'ui', 'placeholder', e.currentTarget.value))} /></Grid.Col>
          </Grid>
          <LogicInput label="Visible when" value={f.ui?.visibleWhen} onChange={(v) => set((x) => setSub(x, 'ui', 'visibleWhen', v))} />
          <LogicInput label="Read-only when" value={f.ui?.readonlyWhen} onChange={(v) => set((x) => setSub(x, 'ui', 'readonlyWhen', v))} />
        </Section>

        <Section title="Effective access">
          <Table verticalSpacing={2}>
            <Table.Tbody>
              {roles.map((r) => (
                <Table.Tr key={r}>
                  <Table.Td w={140}><Text size="sm" ff="monospace">{r}</Text></Table.Td>
                  <Table.Td>
                    <Text size="sm">{fieldOps(doc, f, r).join(', ') || <Text span c="dimmed">none</Text>}</Text>
                  </Table.Td>
                  <Table.Td>{f.access?.[r] && <Badge size="xs" variant="light">field override</Badge>}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
          <Text size="xs" c="dimmed">Edit per-role field overrides in the Access tab.</Text>
        </Section>
      </Stack>
    </Paper>
  )
}
