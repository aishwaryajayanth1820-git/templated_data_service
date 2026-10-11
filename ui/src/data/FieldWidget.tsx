import { Checkbox, Group, JsonInput, NumberInput, Radio, Select, Switch, Textarea, TextInput } from '@mantine/core'
import { useEffect, useState } from 'react'

import { fetchOptions, type Option } from '../api/data'
import type { FieldDef } from '../grammar/types'
import { enumValues, isIntegral, labelOf, widgetOf } from '../grammar/types'
import { fromLocalInput, toLocalInput } from './format'

interface Props {
  table: string
  field: FieldDef
  value: unknown
  onChange: (value: unknown) => void
  disabled?: boolean
  required?: boolean
  error?: string
  /** Display text for a ref value already chosen (from `<field>$display`). */
  refLabel?: string
}

/** One input per field, chosen by `ui.widget` or the type default (grammar §9). */
export function FieldWidget({ table, field, value, onChange, disabled, required, error, refLabel }: Props) {
  const common = {
    label: labelOf(field),
    description: field.ui?.help,
    placeholder: field.ui?.placeholder,
    disabled,
    required,
    error,
  }
  const str = value === null || value === undefined ? '' : String(value)
  const text = (v: string) => onChange(v === '' ? null : v)

  switch (widgetOf(field)) {
    case 'textarea':
      return <Textarea {...common} autosize minRows={2} maxRows={10} value={str} onChange={(e) => text(e.currentTarget.value)} />
    case 'number':
      return (
        <NumberInput
          {...common}
          value={typeof value === 'number' ? value : str}
          allowDecimal={!isIntegral(field.type)}
          onChange={(v) => onChange(v === '' ? null : typeof v === 'number' ? v : Number(v))}
        />
      )
    case 'switch':
      return <Switch label={common.label} description={common.description} disabled={disabled} checked={!!value}
        onChange={(e) => onChange(e.currentTarget.checked)} error={error} />
    case 'checkbox':
      return <Checkbox label={common.label} description={common.description} disabled={disabled} checked={!!value}
        onChange={(e) => onChange(e.currentTarget.checked)} error={error} />
    case 'date':
      return <TextInput {...common} type="date" value={str.slice(0, 10)} onChange={(e) => text(e.currentTarget.value)} />
    case 'datetime':
      return (
        <TextInput {...common} type="datetime-local" value={toLocalInput(value)}
          onChange={(e) => onChange(fromLocalInput(e.currentTarget.value))} />
      )
    case 'time':
      return <TextInput {...common} type="time" step={1} value={str} onChange={(e) => text(e.currentTarget.value)} />
    case 'select':
      return (
        <Select {...common} clearable={!required} value={str || null}
          data={enumValues(field).map((e) => ({ value: e.value, label: e.label ?? e.value }))}
          onChange={(v) => onChange(v)} />
      )
    case 'radio':
      return (
        <Radio.Group label={common.label} description={common.description} required={required} error={error}
          value={str} onChange={(v) => onChange(v)}>
          <Group mt={4}>
            {enumValues(field).map((e) => (
              <Radio key={e.value} value={e.value} label={e.label ?? e.value} disabled={disabled} />
            ))}
          </Group>
        </Radio.Group>
      )
    case 'lookup':
      return <LookupSelect {...common} table={table} field={field.name} value={value} refLabel={refLabel} onChange={onChange} />
    case 'json':
      return (
        <JsonInput {...common} autosize minRows={3} formatOnBlur validationError="Invalid JSON"
          value={value === null || value === undefined ? '' : typeof value === 'string' ? value : JSON.stringify(value, null, 2)}
          onChange={(v) => {
            try {
              onChange(v ? JSON.parse(v) : null)
            } catch {
              onChange(v)
            }
          }} />
      )
    default:
      return <TextInput {...common} value={str} onChange={(e) => text(e.currentTarget.value)} />
  }
}

interface LookupProps {
  table: string
  field: string
  value: unknown
  refLabel?: string
  onChange: (v: unknown) => void
  label: string
  description?: string
  placeholder?: string
  disabled?: boolean
  required?: boolean
  error?: string
}

/** Searchable select over the referenced table's display field. */
function LookupSelect({ table, field, value, refLabel, onChange, ...rest }: LookupProps) {
  const [search, setSearch] = useState('')
  const [options, setOptions] = useState<Option[]>([])
  useEffect(() => {
    let live = true
    const t = setTimeout(() => {
      fetchOptions(table, field, search).then((o) => live && setOptions(o)).catch(() => live && setOptions([]))
    }, 200)
    return () => {
      live = false
      clearTimeout(t)
    }
  }, [table, field, search])

  const current = value === null || value === undefined ? null : String(value)
  const data = options.map((o) => ({ value: String(o.id), label: o.label }))
  if (current && !data.some((d) => d.value === current)) data.unshift({ value: current, label: refLabel ?? `#${current}` })

  return (
    <Select {...rest} searchable clearable={!rest.required} value={current} data={data}
      onSearchChange={setSearch} nothingFoundMessage="No matches"
      onChange={(v) => onChange(v === null ? null : Number(v))} />
  )
}
