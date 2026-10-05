import { Alert, Badge, Button, Code, Group, List, Loader, Modal, ScrollArea, Stack, Tabs, Text, TextInput } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useState } from 'react'

import { type PlanStep, usePlan, useTemplateMutations } from '../api/admin'
import { ApiError } from '../api/client'

const CLS_COLOR: Record<PlanStep['cls'], string> = { SAFE: 'teal', METADATA: 'gray', CHECKED: 'yellow', DESTRUCTIVE: 'red', BLOCKED: 'red' }

interface Props {
  name: string
  checksum: string
  opened: boolean
  onClose: () => void
  onPublished: (version: number) => void
}

/** Review the server's migration plan and publish (architecture §7, ADR-0013). */
export function PublishModal({ name, checksum, opened, onClose, onPublished }: Props) {
  const plan = usePlan(name, opened)
  const { publish } = useTemplateMutations()
  const [confirm, setConfirm] = useState('')
  const p = plan.data
  const ok = !!p && p.publishable && (!p.needsConfirmation || confirm === name)

  const go = () => publish.mutate({ name, checksum, confirm: confirm || undefined }, {
    onSuccess: (r) => {
      notifications.show({ message: `${name} published as v${r.version}`, color: 'teal' })
      setConfirm('')
      onPublished(r.version)
    },
  })

  return (
    <Modal opened={opened} onClose={onClose} size="xl" title={<Text fw={600}>Publish {name}{p ? ` as v${(p.fromVersion ?? 0) + 1}` : ''}</Text>}>
      {plan.isPending ? <Group justify="center" p="xl"><Loader /></Group> : plan.error ? (
        <Alert color="red">{plan.error.message}</Alert>
      ) : p && (
        <Stack>
          {p.blockers.length > 0 && (
            <Alert color="red" variant="light" title="Cannot publish yet">
              <List size="sm">{p.blockers.map((b) => <List.Item key={b}>{b}</List.Item>)}</List>
            </Alert>
          )}
          <Stack gap={4}>
            <Text size="xs" fw={700} c="dimmed" tt="uppercase">Migration plan</Text>
            {p.steps.length === 0 && <Text size="sm" c="dimmed">No changes.</Text>}
            {p.steps.map((s, i) => (
              <Group key={i} gap="sm" wrap="nowrap" align="flex-start">
                <Badge w={110} variant="light" color={CLS_COLOR[s.cls]}>{s.cls.toLowerCase()}</Badge>
                <Text size="sm">{s.description}</Text>
              </Group>
            ))}
            {p.rebuild && <Text size="xs" c="dimmed">SQLite applies these changes by rebuilding the table in one transaction (create new → copy → drop → rename), then checks foreign keys.</Text>}
          </Stack>
          {p.preChecks.length > 0 && (
            <Stack gap={4}>
              <Text size="xs" fw={700} c="dimmed" tt="uppercase">Pre-checks on existing rows</Text>
              {p.preChecks.map((c) => (
                <Group key={c.description} gap="sm">
                  <Badge variant="light" color={c.violations > 0 ? 'red' : c.error ? 'yellow' : 'teal'}>{c.error ? '?' : c.violations}</Badge>
                  <Text size="sm">{c.description}{c.error ? ` (${c.error})` : ''}</Text>
                </Group>
              ))}
            </Stack>
          )}
          <Tabs defaultValue="sqlite">
            <Tabs.List>
              <Tabs.Tab value="sqlite">SQLite DDL</Tabs.Tab>
              <Tabs.Tab value="postgresql">PostgreSQL DDL</Tabs.Tab>
              {p.statements.length > 0 && <Tabs.Tab value="sql">SQL to run</Tabs.Tab>}
            </Tabs.List>
            {Object.entries(p.ddl).map(([k, sql]) => (
              <Tabs.Panel key={k} value={k} pt="xs">
                <ScrollArea.Autosize mah={260}><Code block>{sql}</Code></ScrollArea.Autosize>
              </Tabs.Panel>
            ))}
            <Tabs.Panel value="sql" pt="xs">
              <ScrollArea.Autosize mah={260}><Code block>{p.statements.join(';\n\n')}</Code></ScrollArea.Autosize>
            </Tabs.Panel>
          </Tabs>
          {p.needsConfirmation && (
            <Alert color="orange" variant="light" title="This change loses data">
              <TextInput label={`Type ${name} to confirm`} ff="monospace" value={confirm} onChange={(e) => setConfirm(e.currentTarget.value)} />
            </Alert>
          )}
          {publish.error && (
            <Alert color="red" variant="light">
              {publish.error instanceof ApiError ? publish.error.message : String(publish.error)}
            </Alert>
          )}
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>Cancel</Button>
            <Button disabled={!ok} loading={publish.isPending} onClick={go}>Apply & publish</Button>
          </Group>
        </Stack>
      )}
    </Modal>
  )
}
