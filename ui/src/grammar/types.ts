/** TypeScript view of the tds/v1 template document (docs/01-schema-grammar.md). */

export type ManageType = 'VIEW' | 'MANAGE_VIEW' | 'DATA_SOURCE'
export type FieldType =
  | 'id' | 'string' | 'text' | 'integer' | 'long' | 'decimal' | 'double' | 'boolean'
  | 'date' | 'datetime' | 'time' | 'enum' | 'ref' | 'json' | 'uuid'
export type Placement = 'column' | 'detail' | 'hidden'
export type Widget =
  | 'text' | 'textarea' | 'number' | 'switch' | 'checkbox' | 'date' | 'datetime' | 'time'
  | 'select' | 'radio' | 'lookup' | 'json'
export type FieldOp = 'read' | 'create' | 'update'

export interface EnumValueObj {
  value: string
  label?: string
  color?: string
  description?: string
}
export type EnumValue = string | EnumValueObj

export interface FieldUi {
  placement?: Placement
  widget?: Widget
  order?: number
  width?: number
  group?: string
  help?: string
  placeholder?: string
  format?: string
  visibleWhen?: unknown
  readonlyWhen?: unknown
}

export interface Constraints {
  notBlank?: boolean
  minLength?: number
  maxLength?: number
  pattern?: string
  format?: string
  min?: number | string
  max?: number | string
}

export interface FieldDef {
  name: string
  label?: string
  description?: string
  type: FieldType
  length?: number
  precision?: number
  scale?: number
  values?: EnumValue[]
  ref?: { target: string; display: string; onDelete?: 'RESTRICT' | 'CASCADE' | 'SET_NULL' }
  required?: boolean
  unique?: boolean
  default?: unknown
  constraints?: Constraints
  requiredWhen?: unknown
  ui?: FieldUi
  access?: Record<string, FieldOp[]>
  renamedFrom?: string
}

export type RuleKind = 'requires' | 'exclusive' | 'atLeastOne' | 'expr'
export interface RuleDef {
  id: string
  kind: RuleKind
  if?: string
  then?: string[]
  fields?: string[]
  assert?: unknown
  when?: unknown
  message?: string
}

export interface ActionDef {
  name: string
  label: string
  icon?: string
  placement: 'row' | 'toolbar' | 'selection'
  script?: string
  function?: string
  confirm?: string | null
  visibleWhen?: unknown
  timeoutMs?: number
}

export interface ViewDef {
  titleField?: string
  defaultSort?: { field: string; dir?: 'asc' | 'desc' }[]
  pageSize?: number
  search?: string[]
  filters?: string[]
}

export interface IndexDef {
  name: string
  fields: string[]
  unique?: boolean
}

export interface TemplateDoc {
  $schema?: string
  grammar: 'tds/v1'
  name: string
  label?: string
  description?: string
  manageType: ManageType
  options?: { audit?: boolean; optimisticLock?: boolean }
  fields: FieldDef[]
  indexes?: IndexDef[]
  rules?: RuleDef[]
  actions?: ActionDef[]
  access?: Record<string, string[]>
  view?: ViewDef
}

export const FIELD_TYPES: FieldType[] = [
  'string', 'text', 'integer', 'long', 'decimal', 'double', 'boolean',
  'date', 'datetime', 'time', 'enum', 'ref', 'json', 'uuid',
]

export const TYPE_WIDGETS: Record<FieldType, Widget[]> = {
  id: [],
  string: ['text', 'textarea'],
  text: ['textarea', 'text'],
  integer: ['number'],
  long: ['number'],
  decimal: ['number'],
  double: ['number'],
  boolean: ['switch', 'checkbox'],
  date: ['date'],
  datetime: ['datetime'],
  time: ['time'],
  enum: ['select', 'radio'],
  ref: ['lookup'],
  json: ['json', 'textarea'],
  uuid: ['text'],
}

export const AUDIT_COLUMNS = ['created_at', 'created_by', 'updated_at', 'updated_by', 'row_version']

export const isNumeric = (t: FieldType) => t === 'integer' || t === 'long' || t === 'decimal' || t === 'double'
export const isIntegral = (t: FieldType) => t === 'integer' || t === 'long'
export const isStringy = (t: FieldType) => t === 'string' || t === 'text'
export const isTemporal = (t: FieldType) => t === 'date' || t === 'datetime' || t === 'time'

export function enumValues(f: Pick<FieldDef, 'values'>): EnumValueObj[] {
  return (f.values ?? []).map((v) => (typeof v === 'string' ? { value: v } : v))
}

export function placementOf(f: FieldDef): Placement {
  return f.ui?.placement ?? (f.type === 'id' ? 'hidden' : 'column')
}

export function widgetOf(f: FieldDef): Widget | undefined {
  return f.ui?.widget ?? TYPE_WIDGETS[f.type][0]
}

export function labelOf(f: Pick<FieldDef, 'name' | 'label'>): string {
  return f.label || f.name
}

/** Fields in UI order: ui.order when set, otherwise their position × 10. */
export function orderedFields<T extends FieldDef>(fields: T[]): T[] {
  return fields
    .map((f, i) => ({ f, o: f.ui?.order ?? (i + 1) * 10 }))
    .sort((a, b) => a.o - b.o)
    .map((x) => x.f)
}
