import { ActionIcon, Badge, Button, Grid, Group, MultiSelect, Paper, Select, Stack, Text, Title } from '@mantine/core'

import type { ViewDef } from '../grammar/types'
import { AUDIT_COLUMNS, isStringy, labelOf, orderedFields, placementOf } from '../grammar/types'
import { setOrDel } from './edit'
import { useStudio } from './StudioContext'

/** Table-level view settings (grammar §9). */
export function ViewTab() {
  const { doc, update } = useStudio()
  const v = doc.view ?? {}
  const setV = (key: keyof ViewDef, value: unknown) => update((t) => {
    t.view = t.view ?? {}
    setOrDel(t.view, key, value)
    if (!Object.keys(t.view).length) delete t.view
  })
  const names = doc.fields.map((f) => f.name)
  const sortable = [...names, ...AUDIT_COLUMNS]
  const columns = orderedFields(doc.fields).filter((f) => placementOf(f) === 'column')
  const details = orderedFields(doc.fields).filter((f) => placementOf(f) === 'detail')

  if (doc.manageType === 'DATA_SOURCE') {
    return (
      <Paper withBorder radius="md" p="md" maw={560}>
        <Text size="sm" mb="sm">DATA_SOURCE tables get no end-user UI. The title field is what lookups in other tables show.</Text>
        <Select label="Title field" clearable data={names} value={v.titleField ?? null} onChange={(x) => setV('titleField', x ?? undefined)} />
      </Paper>
    )
  }

  return (
    <Grid gap="md">
      <Grid.Col span={{ base: 12, md: 7 }}>
        <Paper withBorder radius="md" p="md">
          <Stack>
            <Title order={5}>Grid settings</Title>
            <Grid gap="sm">
              <Grid.Col span={6}><Select label="Title field" clearable data={names} value={v.titleField ?? null} onChange={(x) => setV('titleField', x ?? undefined)} /></Grid.Col>
              <Grid.Col span={6}><Select label="Page size" allowDeselect={false} data={['10', '25', '50', '100']} value={String(v.pageSize ?? 25)}
                onChange={(x) => setV('pageSize', Number(x))} /></Grid.Col>
            </Grid>
            <Stack gap={6}>
              <Text size="sm" fw={500}>Default sort</Text>
              {(v.defaultSort ?? []).map((s, i) => (
                <Group key={i} gap="xs">
                  <Select size="xs" w={220} data={sortable} value={s.field} allowDeselect={false}
                    onChange={(x) => update((t) => { t.view!.defaultSort![i].field = x ?? s.field })} />
                  <Select size="xs" w={100} data={['asc', 'desc']} value={s.dir ?? 'asc'} allowDeselect={false}
                    onChange={(x) => update((t) => { t.view!.defaultSort![i].dir = (x ?? 'asc') as 'asc' })} />
                  <ActionIcon variant="subtle" aria-label="Remove sort key"
                    onClick={() => update((t) => { t.view!.defaultSort!.splice(i, 1); if (!t.view!.defaultSort!.length) delete t.view!.defaultSort })}>✕</ActionIcon>
                </Group>
              ))}
              <Button size="xs" variant="default" w="fit-content"
                onClick={() => update((t) => { t.view = t.view ?? {}; t.view.defaultSort = [...(t.view.defaultSort ?? []), { field: names[0], dir: 'asc' }] })}>
                + Sort key
              </Button>
            </Stack>
            <MultiSelect label="Free-text search fields" data={doc.fields.filter((f) => isStringy(f.type) || f.type === 'enum').map((f) => f.name)}
              value={v.search ?? []} onChange={(x) => setV('search', x)} />
            <MultiSelect label="Filter fields" data={doc.fields.filter((f) => f.type !== 'json' && f.type !== 'id').map((f) => f.name)}
              value={v.filters ?? []} onChange={(x) => setV('filters', x)} />
          </Stack>
        </Paper>
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 5 }}>
        <Paper withBorder radius="md" p="md">
          <Stack gap="sm">
            <Title order={5}>Layout from field placement</Title>
            <Text size="xs" fw={700} c="dimmed" tt="uppercase">Columns</Text>
            <Group gap={6}>{columns.map((f) => <Badge key={f.name} variant="light">{labelOf(f)}</Badge>)}</Group>
            <Text size="xs" fw={700} c="dimmed" tt="uppercase">Expanded row</Text>
            <Group gap={6}>{details.length ? details.map((f) => <Badge key={f.name} variant="outline">{f.ui?.group ? `${f.ui.group} · ` : ''}{labelOf(f)}</Badge>) : <Text size="sm" c="dimmed">none</Text>}</Group>
            <Text size="xs" c="dimmed">Reorder fields in the Fields tab; set placement per field.</Text>
          </Stack>
        </Paper>
      </Grid.Col>
    </Grid>
  )
}
