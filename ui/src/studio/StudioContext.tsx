import { createContext, useContext } from 'react'

import type { DraftView, Issue, ScriptInfo } from '../api/admin'
import type { TemplateDoc } from '../grammar/types'

export interface StudioState {
  doc: TemplateDoc
  /** Applies a mutation to a copy of the draft. */
  update: (fn: (d: TemplateDoc) => void) => void
  /** The published version, if any (field types are immutable after publish, ADR-0013). */
  published?: TemplateDoc
  templates: DraftView[]
  roles: string[]
  scripts: ScriptInfo[]
  issues: Issue[]
}

export const StudioContext = createContext<StudioState | null>(null)

export function useStudio(): StudioState {
  const s = useContext(StudioContext)
  if (!s) throw new Error('useStudio outside StudioContext')
  return s
}
