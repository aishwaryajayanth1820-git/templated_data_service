import { Alert, Badge, Button, Grid, Group, NavLink, NumberInput, Paper, Select, Stack, Text, Textarea, TextInput } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useState } from 'react'

import { useSaveScript, useScript } from '../api/admin'
import type { ActionDef } from '../grammar/types'
import { freshName, setOrDel } from './edit'
import { LogicInput } from './LogicInput'
import { useStudio } from './StudioContext'

const stub = (table: string, name: string) => `/**
 * Action: ${table}.${name}
 * Runs in the GraalJS sandbox; ctx is the only API (docs/01-schema-grammar.md §8).
 * @param {ActionContext} ctx
 * @returns {ActionResult}
 */
function run(ctx) {
  return { kind: "message", content: "Hello from ${name} for row " + (ctx.record ? ctx.record.id : "-") };
}
`

/** Actions (grammar §8): button definition plus the script file it calls, edited in place. */
export function ActionsTab() {
  const { doc, update, scripts } = useStudio()
  const actions = doc.actions ?? []
  const [sel, setSel] = useState(0)
  const idx = Math.min(sel, actions.length - 1)
  const a = actions[idx]
  const set = (fn: (x: ActionDef) => void) => update((t) => fn(t.actions![idx]))
  const saveScript = useSaveScript()

  const add = async () => {
    const name = freshName(actions.map((x) => x.name), 'action')
    const script = `${doc.name}/${name}.js`
    await saveScript.mutateAsync({ path: script, content: stub(doc.name, name) })
    update((t) => {
      t.actions = t.actions ?? []
      t.actions.push({ name, label: 'New action', placement: 'row', script, function: 'run', timeoutMs: 3000 })
    })
    setSel(actions.length)
  }

  return (
    <Grid gap="md">
      <Grid.Col span={{ base: 12, md: 3 }}>
        <Paper withBorder radius="md" p="xs">
          <Group justify="space-between" mb={6} px={6}>
            <Text fw={600} size="sm">Actions</Text>
            <Button size="compact-xs" onClick={add} loading={saveScript.isPending}>+ Add</Button>
          </Group>
          {actions.map((x, i) => (
            <NavLink key={i} active={i === idx} onClick={() => setSel(i)} label={x.label}
              description={<Text size="xs" ff="monospace">{x.name} · {x.placement}</Text>} />
          ))}
          {actions.length === 0 && <Text size="sm" c="dimmed" p={6}>No actions</Text>}
        </Paper>
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 9 }}>
        {a ? (
          <Paper withBorder radius="md" p="md">
            <Stack>
              <Group justify="space-between">
                <Text ff="monospace" fw={600}>{a.name}</Text>
                <Button size="xs" variant="subtle" color="red"
                  onClick={() => update((t) => { t.actions!.splice(idx, 1); if (!t.actions!.length) delete t.actions })}>Delete action</Button>
              </Group>
              <Grid gap="sm">
                <Grid.Col span={6}><TextInput label="Name" ff="monospace" description={`Permission: run:${a.name}`} value={a.name}
                  onChange={(e) => set((x) => { x.name = e.currentTarget.value })} /></Grid.Col>
                <Grid.Col span={6}><TextInput label="Button label" value={a.label} onChange={(e) => set((x) => { x.label = e.currentTarget.value })} /></Grid.Col>
                <Grid.Col span={6}>
                  <Select label="Placement" allowDeselect={false} value={a.placement} onChange={(v) => set((x) => { x.placement = (v ?? 'row') as ActionDef['placement'] })}
                    data={[{ value: 'row', label: 'row: a button on each row' }, { value: 'toolbar', label: 'toolbar: one button above the grid' },
                      { value: 'selection', label: 'selection: runs on selected rows' }]} />
                </Grid.Col>
                <Grid.Col span={6}><NumberInput label="Timeout (ms)" min={100} max={30000} value={a.timeoutMs ?? 3000}
                  onChange={(v) => set((x) => setOrDel(x, 'timeoutMs', v === '' ? undefined : Number(v)))} /></Grid.Col>
                <Grid.Col span={6}>
                  <Select label="Script file (scripts/…)" searchable allowDeselect={false} value={a.script ?? null}
                    data={[...new Set([...scripts.map((s) => s.path), ...(a.script ? [a.script] : [])])]}
                    onChange={(v) => set((x) => { x.script = v ?? '' })} />
                </Grid.Col>
                <Grid.Col span={6}><TextInput label="Function" ff="monospace" value={a.function ?? ''}
                  onChange={(e) => set((x) => { x.function = e.currentTarget.value })} /></Grid.Col>
                <Grid.Col span={6}><TextInput label="Confirmation prompt (optional)" value={a.confirm ?? ''}
                  onChange={(e) => set((x) => setOrDel(x, 'confirm', e.currentTarget.value))} /></Grid.Col>
                <Grid.Col span={6}><TextInput label="Icon" value={a.icon ?? ''} onChange={(e) => set((x) => setOrDel(x, 'icon', e.currentTarget.value))} /></Grid.Col>
              </Grid>
              <LogicInput label="Visible when (per row)" value={a.visibleWhen} onChange={(v) => set((x) => setOrDel(x, 'visibleWhen', v))} />
              {a.script && <ScriptEditor path={a.script} fn={a.function ?? ''} />}
            </Stack>
          </Paper>
        ) : (
          <Paper withBorder radius="md" p="xl"><Text c="dimmed">Add an action to attach a JavaScript function to a button.</Text></Paper>
        )}
      </Grid.Col>
    </Grid>
  )
}

function ScriptEditor({ path, fn }: { path: string; fn: string }) {
  const script = useScript(path)
  if (script.isPending) return null
  // Remount when the file content changes on the server so the editor starts from it.
  return <ScriptBody key={`${path}:${script.data?.content ?? ''}`} path={path} fn={fn} script={script} />
}

function ScriptBody({ path, fn, script }: { path: string; fn: string; script: ReturnType<typeof useScript> }) {
  const save = useSaveScript()
  const [text, setText] = useState(script.data?.content ?? '')
  const dirty = text !== (script.data?.content ?? '')
  const found = script.data?.functions.includes(fn)

  return (
    <Stack gap={6}>
      <Group justify="space-between">
        <Group gap="xs">
          <Text size="xs" fw={700} c="dimmed" tt="uppercase">scripts/{path}</Text>
          {script.data && <Badge size="sm" color={found ? 'teal' : 'red'} variant="light">{found ? `${fn}() found` : `${fn}() missing`}</Badge>}
          {script.data?.status === 'ERROR' && <Badge size="sm" color="red">load error</Badge>}
        </Group>
        <Button size="xs" disabled={!dirty} loading={save.isPending}
          onClick={() => save.mutate({ path, content: text }, {
            onSuccess: (s) => notifications.show({ message: s.status === 'OK' ? `Saved ${path}` : `Saved, but it does not load: ${s.error}`, color: s.status === 'OK' ? 'teal' : 'red' }),
            onError: (e) => notifications.show({ message: e.message, color: 'red' }),
          })}>Save script</Button>
      </Group>
      {script.error && <Alert color="yellow" variant="light">File not found yet. Write it below and save to create it.</Alert>}
      {script.data?.status === 'ERROR' && <Alert color="red" variant="light">{script.data.error} (the last good version stays active)</Alert>}
      <Textarea ff="monospace" autosize minRows={14} maxRows={30} spellCheck={false} value={text} onChange={(e) => setText(e.currentTarget.value)} />
      <Text size="xs" c="dimmed">
        The service loads every file under scripts/ at startup and hot-reloads it on change. Scripts run sandboxed: no host access, file IO or threads; ctx is the only bridge.
      </Text>
    </Stack>
  )
}
