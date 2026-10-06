import { Badge, Code, Text, Tooltip } from '@mantine/core'

import type { FieldDef } from '../grammar/types'
import { enumValues } from '../grammar/types'
import { ENUM_COLORS, formatNumber, textOf } from './format'

interface Props {
  field: FieldDef
  value: unknown
  row: Record<string, unknown>
  /** Detail view shows long text in full. */
  full?: boolean
}

export function CellValue({ field, value, row, full }: Props) {
  if (value === null || value === undefined || value === '') {
    return <Text span c="dimmed">—</Text>
  }
  switch (field.type) {
    case 'enum': {
      const e = enumValues(field).find((x) => x.value === value)
      return (
        <Badge variant="light" color={ENUM_COLORS[e?.color ?? 'grey'] ?? 'gray'} radius="sm">
          {e?.label ?? String(value)}
        </Badge>
      )
    }
    case 'boolean':
      return <Text span>{value ? '✓' : '✗'}</Text>
    case 'json':
      return <Code>{JSON.stringify(value)}</Code>
    case 'decimal':
    case 'double':
    case 'integer':
    case 'long':
      return <Text span ff="monospace">{typeof value === 'number' ? formatNumber(value, field.ui?.format) : String(value)}</Text>
    default: {
      const s = textOf(field, value, row)
      if (!full && field.type === 'text' && s.length > 80) {
        return (
          <Tooltip label={s} multiline maw={420} withArrow>
            <Text span>{s.slice(0, 80)}…</Text>
          </Tooltip>
        )
      }
      return <Text span style={{ whiteSpace: full ? 'pre-wrap' : undefined }}>{s}</Text>
    }
  }
}
