import { Button, Group, Select, TextInput } from '@mantine/core'
import { useEffect, useState } from 'react'

import { fetchOptions, type Option } from '../api/data'
import type { FieldMeta } from '../api/meta'
import { enumValues, isTemporal, labelOf } from '../grammar/types'

interface Props {
  table: string
  fields: FieldMeta[]
  /** Current filters, keyed by API parameter (f.<field> or f.<field>.<op>). */
  values: Record<string, string>
  onChange: (param: string, value: string | null) => void
  onClear: () => void
}

/** Filter controls for `view.filters`, writing API filter parameters straight into the URL. */
export function FilterBar({ table, fields, values, onChange, onClear }: Props) {
  if (fields.length === 0) return null
  return (
    <Group gap="xs" wrap="wrap">
      {fields.map((f) => {
        const label = labelOf(f)
        if (f.type === 'enum') {
          return (
            <Select key={f.name} size="xs" w={170} clearable placeholder={`${label}: all`} aria-label={`Filter ${label}`}
              value={values[`f.${f.name}`] ?? null} onChange={(v) => onChange(`f.${f.name}`, v)}
              data={enumValues(f).map((e) => ({ value: e.value, label: e.label ?? e.value }))} />
          )
        }
        if (f.type === 'ref') {
          return <RefFilter key={f.name} table={table} field={f} value={values[`f.${f.name}`] ?? null}
            onChange={(v) => onChange(`f.${f.name}`, v)} />
        }
        if (f.type === 'boolean') {
          return (
            <Select key={f.name} size="xs" w={150} clearable placeholder={`${label}: all`} aria-label={`Filter ${label}`}
              value={values[`f.${f.name}`] ?? null} onChange={(v) => onChange(`f.${f.name}`, v)}
              data={[{ value: 'true', label: 'Yes' }, { value: 'false', label: 'No' }]} />
          )
        }
        if (isTemporal(f.type) && f.type !== 'time') {
          return (
            <TextInput key={f.name} size="xs" type="date" w={170} aria-label={`${label} from`}
              leftSection={<span style={{ fontSize: 11 }}>from</span>} leftSectionWidth={40}
              value={values[`f.${f.name}.gte`] ?? ''} onChange={(e) => onChange(`f.${f.name}.gte`, e.currentTarget.value || null)} />
          )
        }
        return (
          <TextInput key={f.name} size="xs" w={170} placeholder={`${label} contains`} aria-label={`Filter ${label}`}
            value={values[`f.${f.name}.like`] ?? ''} onChange={(e) => onChange(`f.${f.name}.like`, e.currentTarget.value || null)} />
        )
      })}
      {Object.keys(values).length > 0 && (
        <Button size="xs" variant="subtle" onClick={onClear}>Clear filters</Button>
      )}
    </Group>
  )
}

function RefFilter({ table, field, value, onChange }: { table: string; field: FieldMeta; value: string | null; onChange: (v: string | null) => void }) {
  const [options, setOptions] = useState<Option[]>([])
  useEffect(() => {
    fetchOptions(table, field.name, '').then(setOptions).catch(() => setOptions([]))
  }, [table, field.name])
  return (
    <Select size="xs" w={170} clearable searchable placeholder={`${labelOf(field)}: all`} aria-label={`Filter ${labelOf(field)}`}
      value={value} onChange={onChange} data={options.map((o) => ({ value: String(o.id), label: o.label }))} />
  )
}
