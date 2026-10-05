import {
  Alert, Anchor, Badge, Button, Center, Code, Collapse, Grid, Group, Loader, Paper, SegmentedControl, Stack, Table, Tabs, Text, TextInput, Title,
} from '@mantine/core'
import { useDebouncedValue } from '@mantine/hooks'
import { notifications } from '@mantine/notifications'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'

import {
  type Issue, useRoles, useScripts, useTemplate, useTemplateMutations, useTemplates, useVersions, validateTemplate,
} from '../api/admin'
import { ApiError } from '../api/client'
import type { ManageType, TemplateDoc } from '../grammar/types'
import { AccessTab } from './AccessTab'
import { ActionsTab } from './ActionsTab'
import { setOrDel } from './edit'
import { FieldsTab } from './FieldsTab'
import { JsonTab } from './JsonTab'
import { PublishModal } from './PublishModal'
import { RulesTab } from './RulesTab'
import { MANAGE_COLORS, MANAGE_TYPES } from './constants'
import { StatusBadge } from './StatusBadge'
import { StudioContext, type StudioState } from './StudioContext'
import { ViewTab } from './ViewTab'

export function StudioEditorPage() {
  const { name = '' } = useParams()
  const draft = useTemplate(name)
  if (draft.isPending) return <Center p="xl"><Loader /></Center>
  if (draft.error) return <Alert color="red" title="Cannot open template">{draft.error.message}</Alert>
  // Keyed by name only: saving changes the checksum but must keep tab, selection and dialogs.
  return <Editor key={name} />
}

function Editor() {
  const { name = '' } = useParams()
  const navigate = useNavigate()
  const draft = useTemplate(name)
  const templates = useTemplates()
  const roles = useRoles()
  const scripts = useScripts()
  const { save, remove } = useTemplateMutations()
  const server = draft.data!
  const [doc, setDoc] = useState<TemplateDoc>(() => structuredClone(server.json))
  const [issues, setIssues] = useState<Issue[]>(server.issues)
  const [tab, setTab] = useState<string>('fields')
  const [fieldIdx, setFieldIdx] = useState(1)
  const [publishing, setPublishing] = useState(false)
  const [showIssues, setShowIssues] = useState(true)
  const [debounced] = useDebouncedValue(doc, 400)
  const dirty = JSON.stringify(doc) !== JSON.stringify(server.json)

  useEffect(() => {
    let live = true
    validateTemplate(debounced).then((i) => live && setIssues(i)).catch(() => {})
    return () => { live = false }
  }, [debounced])

  // Mutations run immediately inside the event handler (not as a deferred state updater), so callers may
  // read event values such as e.currentTarget.value. The ref keeps consecutive updates in one handler ordered.
  const latest = useRef(doc)
  const update = (fn: (d: TemplateDoc) => void) => {
    const n = structuredClone(latest.current)
    fn(n)
    latest.current = n
    setDoc(n)
  }
  const published = useMemo(() => (server.publishedVersion ? server.json : undefined), [server])
  const ctx: StudioState = {
    doc, update, published, issues,
    templates: templates.data ?? [],
    roles: (roles.data ?? []).map((r) => r.name),
    scripts: scripts.data ?? [],
  }
  const errors = issues.filter((i) => i.severity === 'ERROR').length
  const warnings = issues.length - errors

  const saveDraft = (then?: () => void) => save.mutate({ doc, checksum: server.checksum }, {
    onSuccess: () => {
      notifications.show({ message: 'Draft saved', color: 'teal' })
      then?.()
    },
    onError: (e) => notifications.show({ title: 'Save failed', message: e instanceof ApiError ? e.message : String(e), color: 'red' }),
  })

  const gotoIssue = (i: Issue) => {
    const m = /^(fields|rules|actions)\[(\d+)\]/.exec(i.path)
    if (m?.[1] === 'fields') {
      setTab('fields')
      setFieldIdx(Number(m[2]))
    } else if (m?.[1] === 'rules') setTab('rules')
    else if (m?.[1] === 'actions') setTab('actions')
    else if (i.path === 'access') setTab('access')
    else if (i.path === 'view') setTab('view')
    else setTab('json')
  }

  const exportJson = () => {
    const blob = new Blob([JSON.stringify({ $schema: '../schema/tds-template.schema.json', ...doc }, null, 2) + '\n'], { type: 'application/json' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `${doc.name}.json`
    a.click()
    URL.revokeObjectURL(a.href)
  }

  return (
    <StudioContext.Provider value={ctx}>
      <Stack gap="md">
        <Group justify="space-between" align="flex-start" wrap="wrap">
          <Stack gap={4}>
            <Group gap="xs">
              <Anchor component={Link} to="/admin/studio" size="sm">Schema Studio</Anchor>
              <Text size="sm" c="dimmed">/</Text>
            </Group>
            <Group gap="sm">
              <Title order={2}>{doc.label || doc.name}</Title>
              <Text ff="monospace" c="dimmed">{doc.name}</Text>
              <StatusBadge t={server} />
              {dirty && <Badge color="orange" variant="dot">unsaved changes</Badge>}
            </Group>
          </Stack>
          <Group gap="xs">
            <Badge size="lg" variant="light" color={errors ? 'red' : warnings ? 'yellow' : 'teal'} style={{ cursor: 'pointer' }} onClick={() => setShowIssues((s) => !s)}>
              {errors ? `${errors} error${errors > 1 ? 's' : ''}` : 'valid'}{warnings ? ` · ${warnings} warning${warnings > 1 ? 's' : ''}` : ''}
            </Badge>
            {server.publishedVersion && <Button variant="subtle" component={Link} to={doc.manageType === 'DATA_SOURCE' ? `/admin/data/${doc.name}` : `/t/${doc.name}`}>Open table</Button>}
            <Button variant="default" onClick={exportJson}>Export JSON</Button>
            {!server.publishedVersion && (
              <Button variant="subtle" color="red" loading={remove.isPending}
                onClick={() => window.confirm(`Delete draft ${doc.name}?`) && remove.mutate(doc.name, { onSuccess: () => navigate('/admin/studio') })}>
                Delete draft
              </Button>
            )}
            <Button variant="default" disabled={!dirty} loading={save.isPending} onClick={() => saveDraft()}>Save draft</Button>
            <Button disabled={!dirty && server.status === 'PUBLISHED'} onClick={() => (dirty ? saveDraft(() => setPublishing(true)) : setPublishing(true))}>
              Publish…
            </Button>
          </Group>
        </Group>

        <Paper withBorder radius="md" p="sm">
          <Grid gap="sm" align="flex-end">
            <Grid.Col span={{ base: 12, md: 5 }}>
              <Text size="sm" fw={500} mb={4}>Manage type</Text>
              <SegmentedControl fullWidth value={doc.manageType} onChange={(v) => update((d) => { d.manageType = v as ManageType })}
                data={(Object.keys(MANAGE_TYPES) as ManageType[]).map((k) => ({ value: k, label: k.replace('_', ' ') }))} />
              <Text size="xs" c="dimmed" mt={4}>{MANAGE_TYPES[doc.manageType]}</Text>
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 6, md: 3 }}>
              <TextInput label="Label" value={doc.label ?? ''} onChange={(e) => update((d) => setOrDel(d, 'label', e.currentTarget.value))} />
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
              <TextInput label="Description" value={doc.description ?? ''} onChange={(e) => update((d) => setOrDel(d, 'description', e.currentTarget.value))} />
            </Grid.Col>
          </Grid>
          <Badge mt="xs" variant="light" color={MANAGE_COLORS[doc.manageType]}>{doc.manageType}</Badge>
        </Paper>

        {issues.length > 0 && (
          <Paper withBorder radius="md">
            <Group p="xs" px="sm" style={{ cursor: 'pointer' }} onClick={() => setShowIssues((s) => !s)}>
              <Text size="sm" fw={600}>{showIssues ? '▾' : '▸'} Validation</Text>
              {errors > 0 && <Badge color="red" variant="light">{errors} error{errors > 1 ? 's' : ''}</Badge>}
              {warnings > 0 && <Badge color="yellow" variant="light">{warnings} warning{warnings > 1 ? 's' : ''}</Badge>}
            </Group>
            <Collapse expanded={showIssues}>
              <Table highlightOnHover verticalSpacing={4}>
                <Table.Tbody>
                  {issues.map((i, k) => (
                    <Table.Tr key={k} style={{ cursor: 'pointer' }} onClick={() => gotoIssue(i)}>
                      <Table.Td w={70}><Text size="xs" ff="monospace" c={i.severity === 'ERROR' ? 'red' : 'yellow.7'}>{i.code}</Text></Table.Td>
                      <Table.Td><Text size="sm">{i.message}</Text></Table.Td>
                      <Table.Td w={140}><Text size="xs" c="dimmed" ff="monospace">{i.path}</Text></Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </Collapse>
          </Paper>
        )}

        <Tabs value={tab} onChange={(v) => setTab(v ?? 'fields')} keepMounted={false}>
          <Tabs.List mb="md">
            <Tabs.Tab value="fields">Fields <Badge size="xs" variant="light" ml={4}>{doc.fields.length}</Badge></Tabs.Tab>
            <Tabs.Tab value="rules">Rules {doc.rules?.length ? <Badge size="xs" variant="light" ml={4}>{doc.rules.length}</Badge> : null}</Tabs.Tab>
            <Tabs.Tab value="actions">Actions {doc.actions?.length ? <Badge size="xs" variant="light" ml={4}>{doc.actions.length}</Badge> : null}</Tabs.Tab>
            <Tabs.Tab value="access">Access</Tabs.Tab>
            <Tabs.Tab value="view">View</Tabs.Tab>
            <Tabs.Tab value="json">JSON</Tabs.Tab>
            <Tabs.Tab value="versions">Versions</Tabs.Tab>
          </Tabs.List>
          <Tabs.Panel value="fields"><FieldsTab selected={fieldIdx} onSelect={setFieldIdx} /></Tabs.Panel>
          <Tabs.Panel value="rules"><RulesTab /></Tabs.Panel>
          <Tabs.Panel value="actions"><ActionsTab /></Tabs.Panel>
          <Tabs.Panel value="access"><AccessTab /></Tabs.Panel>
          <Tabs.Panel value="view"><ViewTab /></Tabs.Panel>
          <Tabs.Panel value="json"><JsonTab /></Tabs.Panel>
          <Tabs.Panel value="versions"><Versions name={name} /></Tabs.Panel>
        </Tabs>
      </Stack>
      <PublishModal name={name} checksum={draft.data?.checksum ?? server.checksum} opened={publishing} onClose={() => setPublishing(false)}
        onPublished={async () => {
          setPublishing(false)
          // Publishing normalises the document (e.g. drops renamedFrom); continue from the server's copy.
          const fresh = await draft.refetch()
          if (fresh.data) {
            latest.current = structuredClone(fresh.data.json)
            setDoc(latest.current)
            setIssues(fresh.data.issues)
          }
        }} />
    </StudioContext.Provider>
  )
}

function Versions({ name }: { name: string }) {
  const versions = useVersions(name)
  const [open, setOpen] = useState<number | null>(null)
  if (!versions.data?.length) return <Text c="dimmed">Not published yet.</Text>
  return (
    <Paper withBorder radius="md" maw={1000}>
      <Table verticalSpacing="xs">
        <Table.Thead><Table.Tr><Table.Th>Version</Table.Th><Table.Th>Published</Table.Th><Table.Th>By</Table.Th><Table.Th /></Table.Tr></Table.Thead>
        <Table.Tbody>
          {versions.data.map((v) => (
            <>
              <Table.Tr key={v.version}>
                <Table.Td><Badge variant="light">v{v.version}</Badge></Table.Td>
                <Table.Td>{new Date(v.publishedAt).toLocaleString()}</Table.Td>
                <Table.Td>{v.publishedBy}</Table.Td>
                <Table.Td>{v.appliedSql && <Button size="compact-xs" variant="subtle" onClick={() => setOpen(open === v.version ? null : v.version)}>SQL applied</Button>}</Table.Td>
              </Table.Tr>
              {open === v.version && (
                <Table.Tr key={`${v.version}-sql`}><Table.Td colSpan={4}><Code block>{v.appliedSql || '(metadata only)'}</Code></Table.Td></Table.Tr>
              )}
            </>
          ))}
        </Table.Tbody>
      </Table>
    </Paper>
  )
}
