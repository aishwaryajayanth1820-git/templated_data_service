import { Alert, Button, Group, Stack, Text, Textarea } from '@mantine/core'
import { useState } from 'react'

import type { TemplateDoc } from '../grammar/types'
import { useStudio } from './StudioContext'

const pretty = (d: TemplateDoc) => JSON.stringify(d, null, 2)

/** The template document itself; edits here replace the draft when applied. */
export function JsonTab() {
  const { doc } = useStudio()
  const current = pretty(doc)
  // Remount on every draft change so the editor always starts from the current document.
  return <JsonEditor key={current} current={current} />
}

function JsonEditor({ current }: { current: string }) {
  const { doc, update } = useStudio()
  const [text, setText] = useState(current)
  const [error, setError] = useState<string | null>(null)

  const apply = () => {
    try {
      const next = JSON.parse(text) as TemplateDoc
      if (next.name !== doc.name) throw new Error(`"name" must stay "${doc.name}"; use Import for a different template`)
      if (!Array.isArray(next.fields)) throw new Error('"fields" must be an array')
      update((t) => {
        for (const k of Object.keys(t)) delete (t as unknown as Record<string, unknown>)[k]
        Object.assign(t, next)
      })
      setError(null)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Text size="sm" c="dimmed">Validated against schema/tds-template.schema.json (JSON Schema 2020-12) and then by the semantic checks.</Text>
        <Group gap="xs">
          <Button size="xs" variant="default" disabled={text === current} onClick={() => setText(current)}>Revert</Button>
          <Button size="xs" disabled={text === current} onClick={apply}>Apply to draft</Button>
        </Group>
      </Group>
      {error && <Alert color="red" variant="light">{error}</Alert>}
      <Textarea ff="monospace" autosize minRows={24} maxRows={60} spellCheck={false} value={text} onChange={(e) => setText(e.currentTarget.value)} />
    </Stack>
  )
}
