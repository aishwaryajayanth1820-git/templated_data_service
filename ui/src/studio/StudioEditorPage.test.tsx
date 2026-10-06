import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { mockFetch, renderRoutes } from '../test/render'
import { StudioEditorPage } from './StudioEditorPage'

const doc = {
  grammar: 'tds/v1',
  name: 'jackpot_limits',
  label: 'Jackpot Limits',
  manageType: 'MANAGE_VIEW',
  fields: [{ name: 'id', type: 'id', ui: { placement: 'hidden' } }],
}
const draft = { name: 'jackpot_limits', manageType: 'MANAGE_VIEW', json: doc, checksum: 'c1', status: 'DRAFT', issues: [], updatedAt: '2026-10-04T00:00:00Z', updatedBy: 'admin' }

function api() {
  return mockFetch({
    '/api/admin/templates/jackpot_limits': { status: 200, body: draft },
    '/api/admin/templates': { status: 200, body: [draft] },
    '/api/admin/roles': { status: 200, body: [{ name: 'admin', builtin: true, users: 1, templates: [] }, { name: 'viewer', builtin: true, users: 0, templates: [] }] },
    '/api/admin/scripts': { status: 200, body: [] },
    '/api/admin/templates/validate': { status: 200, body: [] },
  })
}

const routes = [{ path: '/admin/studio/:name', element: <StudioEditorPage /> }]

// These render the whole Studio and type character by character; on a cold start that exceeds the 5 s default.
const SLOW = 20_000

describe('StudioEditorPage', () => {
  it('edits fields through inputs without crashing and marks the draft unsaved', async () => {
    api()
    renderRoutes(routes, '/admin/studio/jackpot_limits')

    await userEvent.click(await screen.findByRole('button', { name: '+ Add field' }))
    // The template header also has a "Label" input; the field inspector's is the last one.
    const labels = await screen.findAllByLabelText('Label')
    const label = labels[labels.length - 1]
    await userEvent.clear(label)
    await userEvent.type(label, 'Game')

    expect(label).toHaveValue('Game')
    expect(screen.getByText('unsaved changes')).toBeInTheDocument()
  }, SLOW)

  it('renames a field on blur and keeps the edit when typing the label afterwards', async () => {
    api()
    renderRoutes(routes, '/admin/studio/jackpot_limits')

    await userEvent.click(await screen.findByRole('button', { name: '+ Add field' }))
    const name = screen.getByLabelText('Name (column)')
    await userEvent.clear(name)
    await userEvent.type(name, 'game_name')
    await userEvent.tab()

    await waitFor(() => expect(screen.getAllByText('game_name').length).toBeGreaterThan(0))
    expect(screen.queryByText('new_field_1')).not.toBeInTheDocument()
  }, SLOW)
})
