import { Badge, Button, Grid, Group, MultiSelect, Paper, Select, Stack, Text, TextInput } from '@mantine/core'

import type { RuleDef, RuleKind } from '../grammar/types'
import { freshName, setOrDel } from './edit'
import { LogicInput } from './LogicInput'
import { useStudio } from './StudioContext'

const KINDS: Record<RuleKind, string> = {
  requires: 'If field X is present, fields Y… must be present',
  exclusive: 'At most one of the fields may be present',
  atLeastOne: 'At least one of the fields must be present',
  expr: 'Custom JsonLogic assertion',
}

/** Cross-field rules (grammar §7). requires/exclusive/atLeastOne without `when` also become SQL CHECKs. */
export function RulesTab() {
  const { doc, update, issues } = useStudio()
  const rules = doc.rules ?? []
  const fieldNames = doc.fields.filter((f) => f.type !== 'id').map((f) => f.name)
  const set = (i: number, fn: (r: RuleDef) => void) => update((t) => fn(t.rules![i]))
  const add = () => update((t) => {
    t.rules = t.rules ?? []
    t.rules.push({ id: freshName(t.rules.map((r) => r.id), 'rule'), kind: 'requires', if: '', then: [], message: '' })
  })
  const setKind = (i: number, k: RuleKind) => set(i, (r) => {
    delete r.if
    delete r.then
    delete r.fields
    delete r.assert
    r.kind = k
    if (k === 'requires') {
      r.if = ''
      r.then = []
    } else if (k === 'expr') {
      r.assert = true
    } else {
      r.fields = []
    }
  })

  return (
    <Stack maw={1000}>
      <Group justify="space-between">
        <Text size="sm" c="dimmed" maw={720}>
          Rules run on every write, in the browser and on the server. <b>requires</b>, <b>exclusive</b> and <b>atLeastOne</b> without a guard also
          become SQL CHECK constraints, so even direct database inserts must follow them.
        </Text>
        <Button size="xs" onClick={add}>+ Add rule</Button>
      </Group>
      {rules.length === 0 && <Text c="dimmed">No cross-field rules yet.</Text>}
      {rules.map((r, i) => {
        const sql = r.kind !== 'expr' && r.when === undefined
        const ri = issues.filter((x) => x.path === `rules[${i}]` || x.path.startsWith(`rules[${i}].`))
        return (
          <Paper key={i} withBorder radius="md" p="md">
            <Stack gap="sm">
              <Group justify="space-between">
                <Group gap="xs">
                  <Text ff="monospace" fw={600}>{r.id}</Text>
                  <Badge variant="light" color={sql ? 'teal' : 'blue'}>{sql ? 'DB CHECK + app' : 'app only'}</Badge>
                  {ri.map((x) => <Badge key={x.message} color={x.severity === 'ERROR' ? 'red' : 'yellow'} title={x.message}>{x.code}</Badge>)}
                </Group>
                <Button size="xs" variant="subtle" color="red" onClick={() => update((t) => { t.rules!.splice(i, 1); if (!t.rules!.length) delete t.rules })}>Delete</Button>
              </Group>
              <Grid gap="sm">
                <Grid.Col span={{ base: 12, sm: 4 }}>
                  <TextInput label="Rule id" ff="monospace" value={r.id} onChange={(e) => set(i, (x) => { x.id = e.currentTarget.value })} />
                </Grid.Col>
                <Grid.Col span={{ base: 12, sm: 8 }}>
                  <Select label="Kind" allowDeselect={false} value={r.kind} onChange={(v) => v && setKind(i, v as RuleKind)}
                    data={(Object.keys(KINDS) as RuleKind[]).map((k) => ({ value: k, label: `${k}: ${KINDS[k]}` }))} />
                </Grid.Col>
                {r.kind === 'requires' && (
                  <>
                    <Grid.Col span={{ base: 12, sm: 4 }}>
                      <Select label="If this field is present…" data={fieldNames} value={r.if || null} onChange={(v) => set(i, (x) => { x.if = v ?? '' })} />
                    </Grid.Col>
                    <Grid.Col span={{ base: 12, sm: 8 }}>
                      <MultiSelect label="…then these must be present" data={fieldNames.filter((n) => n !== r.if)} value={r.then ?? []}
                        onChange={(v) => set(i, (x) => { x.then = v })} />
                    </Grid.Col>
                  </>
                )}
                {(r.kind === 'exclusive' || r.kind === 'atLeastOne') && (
                  <Grid.Col span={12}>
                    <MultiSelect label="Fields" data={fieldNames} value={r.fields ?? []} onChange={(v) => set(i, (x) => { x.fields = v })} />
                  </Grid.Col>
                )}
                {r.kind === 'expr' && (
                  <>
                    <Grid.Col span={{ base: 12, sm: 7 }}>
                      <LogicInput label="Assert (must be true)" minRows={3} value={r.assert} onChange={(v) => set(i, (x) => { x.assert = v ?? true })} />
                    </Grid.Col>
                    <Grid.Col span={{ base: 12, sm: 5 }}>
                      <MultiSelect label="Highlight fields on failure" data={fieldNames} value={r.fields ?? []}
                        onChange={(v) => set(i, (x) => setOrDel(x, 'fields', v))} />
                    </Grid.Col>
                  </>
                )}
                <Grid.Col span={{ base: 12, sm: 6 }}>
                  <LogicInput label="Only when (optional guard)" description="A guarded rule is enforced by the service only" value={r.when}
                    onChange={(v) => set(i, (x) => setOrDel(x, 'when', v))} />
                </Grid.Col>
                <Grid.Col span={{ base: 12, sm: 6 }}>
                  <TextInput label="Error message" value={r.message ?? ''} onChange={(e) => set(i, (x) => setOrDel(x, 'message', e.currentTarget.value))} />
                </Grid.Col>
              </Grid>
            </Stack>
          </Paper>
        )
      })}
    </Stack>
  )
}
