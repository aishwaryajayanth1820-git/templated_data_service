import { ActionIcon, Checkbox, Group, Paper, SimpleGrid, Stack, Table, Text, Tooltip, UnstyledButton } from '@mantine/core'
import { Fragment, type ReactNode, useState } from 'react'

import type { Row } from '../api/data'
import type { FieldMeta, TemplateMeta } from '../api/meta'
import { labelOf, orderedFields, placementOf } from '../grammar/types'
import { CellValue } from './CellValue'

interface Props {
  meta: TemplateMeta
  rows: Row[]
  sort: { field: string; dir: 'asc' | 'desc' } | null
  onSort: (field: string) => void
  selectable: boolean
  selected: number[]
  onSelect: (ids: number[]) => void
  rowActions: (row: Row) => ReactNode
}

/** Server-driven grid (ADR-0015): `column` fields as columns, `detail` fields in an expandable row. */
export function DataGrid({ meta, rows, sort, onSort, selectable, selected, onSelect, rowActions }: Props) {
  const [open, setOpen] = useState<Record<number, boolean>>({})
  const readable = orderedFields(meta.fields).filter((f) => f.ops.includes('read'))
  const columns = readable.filter((f) => placementOf(f) === 'column')
  const details = readable.filter((f) => placementOf(f) === 'detail')
  const groups = new Map<string, FieldMeta[]>()
  details.forEach((f) => groups.set(f.ui?.group ?? '', [...(groups.get(f.ui?.group ?? '') ?? []), f]))
  const span = columns.length + (details.length ? 1 : 0) + (selectable ? 1 : 0) + 2
  const allSelected = rows.length > 0 && rows.every((r) => selected.includes(r.id))

  return (
    <Table.ScrollContainer minWidth={640}>
      <Table highlightOnHover verticalSpacing="xs" stickyHeader>
        <Table.Thead>
          <Table.Tr>
            {details.length > 0 && <Table.Th w={32} />}
            {selectable && (
              <Table.Th w={32}>
                <Checkbox aria-label="Select all rows on this page" checked={allSelected}
                  onChange={(e) => onSelect(e.currentTarget.checked
                    ? [...new Set([...selected, ...rows.map((r) => r.id)])]
                    : selected.filter((id) => !rows.some((r) => r.id === id)))} />
              </Table.Th>
            )}
            {columns.map((f) => (
              <Table.Th key={f.name} w={f.ui?.width}>
                <UnstyledButton onClick={() => onSort(f.name)} aria-label={`Sort by ${labelOf(f)}`}>
                  <Group gap={4} wrap="nowrap">
                    <Text size="sm" fw={600}>{labelOf(f)}</Text>
                    <Text size="xs" c="dimmed">{sort?.field === f.name ? (sort.dir === 'desc' ? '▼' : '▲') : ''}</Text>
                  </Group>
                </UnstyledButton>
              </Table.Th>
            ))}
            <Table.Th w={28} />
            <Table.Th />
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rows.map((r) => (
            <Fragment key={r.id}>
              <Table.Tr style={{ cursor: details.length ? 'pointer' : undefined }}
                onClick={() => details.length && setOpen((o) => ({ ...o, [r.id]: !o[r.id] }))}>
                {details.length > 0 && (
                  <Table.Td>
                    <ActionIcon variant="subtle" size="sm" aria-label={open[r.id] ? 'Collapse row' : 'Expand row'}>
                      {open[r.id] ? '▾' : '▸'}
                    </ActionIcon>
                  </Table.Td>
                )}
                {selectable && (
                  <Table.Td onClick={(e) => e.stopPropagation()}>
                    <Checkbox aria-label={`Select row ${r.id}`} checked={selected.includes(r.id)}
                      onChange={(e) => onSelect(e.currentTarget.checked ? [...selected, r.id] : selected.filter((x) => x !== r.id))} />
                  </Table.Td>
                )}
                {columns.map((f) => (
                  <Table.Td key={f.name}><CellValue field={f} value={r[f.name]} row={r} /></Table.Td>
                ))}
                <Table.Td>
                  {r.$issues && (
                    <Tooltip multiline maw={360} withArrow label={['Breaks app-level rules (inserted directly?):', ...r.$issues].join('\n')}
                      style={{ whiteSpace: 'pre-line' }}>
                      <Text c="orange" aria-label="Row has rule violations">⚠</Text>
                    </Tooltip>
                  )}
                </Table.Td>
                <Table.Td onClick={(e) => e.stopPropagation()}>
                  <Group gap={6} justify="flex-end" wrap="nowrap">{rowActions(r)}</Group>
                </Table.Td>
              </Table.Tr>
              {open[r.id] && (
                <Table.Tr>
                  <Table.Td colSpan={span} p={0}>
                    <Paper radius={0} p="md" bg="var(--mantine-color-default-hover)">
                      <Stack gap="sm">
                        {[...groups.entries()].map(([g, fs]) => (
                          <Stack key={g || '_'} gap={6}>
                            {g && <Text size="xs" fw={700} c="dimmed" tt="uppercase">{g}</Text>}
                            <SimpleGrid cols={{ base: 1, sm: 2, md: 3 }} spacing="md" verticalSpacing="xs">
                              {fs.map((f) => (
                                <div key={f.name}>
                                  <Text size="xs" c="dimmed">{labelOf(f)}</Text>
                                  <CellValue field={f} value={r[f.name]} row={r} full />
                                </div>
                              ))}
                            </SimpleGrid>
                          </Stack>
                        ))}
                      </Stack>
                    </Paper>
                  </Table.Td>
                </Table.Tr>
              )}
            </Fragment>
          ))}
          {rows.length === 0 && (
            <Table.Tr>
              <Table.Td colSpan={span}>
                <Text c="dimmed" ta="center" py="lg">No rows match</Text>
              </Table.Td>
            </Table.Tr>
          )}
        </Table.Tbody>
      </Table>
    </Table.ScrollContainer>
  )
}
