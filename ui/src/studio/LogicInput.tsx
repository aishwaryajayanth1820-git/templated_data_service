import { Textarea } from '@mantine/core'
import { useState } from 'react'

interface Props {
  label: string
  value: unknown
  onChange: (v: unknown) => void
  description?: string
  minRows?: number
}

/** Edits a JsonLogic expression as JSON text; commits on blur, shows parse errors inline. */
export function LogicInput(props: Props) {
  // Remount when the committed value changes from outside, resetting the edit buffer.
  return <LogicEditor key={JSON.stringify(props.value ?? null)} {...props} />
}

function LogicEditor({ label, value, onChange, description, minRows = 2 }: Props) {
  const [text, setText] = useState(value === undefined ? '' : JSON.stringify(value))
  const [error, setError] = useState<string | null>(null)

  const commit = () => {
    if (!text.trim()) {
      setError(null)
      onChange(undefined)
      return
    }
    try {
      onChange(JSON.parse(text))
      setError(null)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <Textarea label={label} description={description ?? 'JsonLogic. Context: record fields, $op, $user. Custom op: present'}
      autosize minRows={minRows} ff="monospace" value={text} error={error}
      placeholder='{"==":[{"var":"alert_type"},"CRITICAL"]}' onChange={(e) => setText(e.currentTarget.value)} onBlur={commit} />
  )
}
