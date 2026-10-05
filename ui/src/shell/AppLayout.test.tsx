import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { mockFetch, renderRoutes } from '../test/render'
import { AppLayout } from './AppLayout'
import { HomePage } from './HomePage'
import { LoginPage } from './LoginPage'
import { RequireAuth } from './RequireAuth'

const routes = [
  { path: '/login', element: <LoginPage /> },
  { element: <RequireAuth />, children: [{ element: <AppLayout />, children: [{ index: true, element: <HomePage /> }] }] },
]

const nav = { generation: 1, tables: [], dataSources: [] }
const admin = { username: 'admin', displayName: 'Administrator', roles: ['admin', 'viewer'], mustChangePassword: false }

describe('AppLayout', () => {
  it('signs out back to the login page', async () => {
    document.cookie = 'XSRF-TOKEN=t'
    mockFetch({ '/api/auth/me': { status: 200, body: admin }, '/api/meta': { status: 200, body: nav }, '/api/auth/logout': { status: 204 } })
    renderRoutes(routes, '/')

    expect(await screen.findByText('Welcome, Administrator')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'User menu' }))
    await userEvent.click(await screen.findByText('Sign out'))

    await waitFor(() => expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument())
  })

  it('forces the password change when required', async () => {
    mockFetch({ '/api/auth/me': { status: 200, body: { ...admin, mustChangePassword: true } }, '/api/meta': { status: 200, body: nav } })
    renderRoutes(routes, '/')

    expect(await screen.findByText(/Choose your own password to continue/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument()
  })

  it('loads the navigation after a forced password change', async () => {
    document.cookie = 'XSRF-TOKEN=t'
    const blocked = { status: 403, body: { code: 'PASSWORD_CHANGE_REQUIRED' } }
    const tables = { status: 200, body: { ...nav, tables: [{ name: 'alerts', label: 'Alerts', manageType: 'VIEW' }] } }
    mockFetch({
      '/api/auth/me': { status: 200, body: { ...admin, mustChangePassword: true } },
      '/api/meta': [blocked, tables],
      '/api/auth/password': { status: 200, body: admin },
    })
    renderRoutes(routes, '/')

    await screen.findByText(/Choose your own password to continue/)
    await userEvent.type(screen.getByLabelText('Current password'), 'old-password-1')
    await userEvent.type(screen.getByLabelText(/^New password/), 'new-password-1')
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'new-password-1')
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }))

    expect((await screen.findAllByRole('link', { name: /^Alerts/ }))[0]).toHaveAttribute('href', '/t/alerts')
  })

  it('lists published tables and admin links from /api/meta', async () => {
    mockFetch({
      '/api/auth/me': { status: 200, body: admin },
      '/api/meta': {
        status: 200,
        body: { ...nav, tables: [{ name: 'alerts', label: 'Alerts', manageType: 'VIEW' }], dataSources: [{ name: 'alert_groups', label: 'Alert Groups', manageType: 'DATA_SOURCE' }] },
      },
    })
    renderRoutes(routes, '/')

    expect((await screen.findAllByRole('link', { name: /^Alerts/ }))[0]).toHaveAttribute('href', '/t/alerts')
    expect(screen.getByRole('link', { name: /Alert Groups/ })).toHaveAttribute('href', '/admin/data/alert_groups')
    expect(screen.getAllByRole('link', { name: 'Schema Studio' })[0]).toHaveAttribute('href', '/admin/studio')
  })

  it('shows administration only to admins', async () => {
    mockFetch({ '/api/auth/me': { status: 200, body: { ...admin, roles: ['viewer'] } }, '/api/meta': { status: 200, body: nav } })
    renderRoutes(routes, '/')

    expect(await screen.findByText('No tables yet')).toBeInTheDocument()
    expect(screen.queryByText('Administration')).not.toBeInTheDocument()
    expect(screen.queryByText('Schema Studio')).not.toBeInTheDocument()
  })
})
