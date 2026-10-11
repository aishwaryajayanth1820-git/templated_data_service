import { Badge } from '@mantine/core'

import type { DraftView } from '../api/admin'

export function StatusBadge({ t }: { t: Pick<DraftView, 'status' | 'publishedVersion'> }) {
  if (t.status === 'DRAFT') return <Badge variant="light" color="gray">draft</Badge>
  if (t.status === 'PUBLISHED') return <Badge variant="light" color="teal">published v{t.publishedVersion}</Badge>
  return <Badge variant="light" color="orange">v{t.publishedVersion} + changes</Badge>
}
