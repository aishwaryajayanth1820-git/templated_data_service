import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { mockFetch, renderRoutes } from '../test/render'
import { LoginPage } from './LoginPage'

const routes = [
  { path: '/login', element: <LoginPage /> },
  { path: '/', element: <p>home</p> },
]

describe('LoginPage', () => {
  it('validates empty fields without calling the API', async () => {
    const calls = mockFetch([{ status: 401, body: { code: 'UNAUTHENTICATED' } }])
    renderRoutes(routes, '/login')

    await userEvent.click(await screen.findByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Enter your username')).toBeInTheDocument()
    expect(calls.map((c) => c.url)).toEqual(['/api/auth/me'])
  })

  it('shows the server message on bad credentials', async () => {
    document.cookie = 'XSRF-TOKEN=t1'
    mockFetch([
      { status: 401, body: { code: 'UNAUTHENTICATED' } },
      { status: 401, body: { code: 'UNAUTHENTICATED', detail: 'Invalid username or password' } },
    ])
    renderRoutes(routes, '/login')

    await userEvent.type(await screen.findByLabelText('Username'), 'admin')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password')
  })

  it('signs in with the CSRF header and goes home', async () => {
    document.cookie = 'XSRF-TOKEN=t2'
    const calls = mockFetch([
      { status: 401, body: { code: 'UNAUTHENTICATED' } },
      { status: 200, body: { username: 'admin', roles: ['admin', 'viewer'], mustChangePassword: false } },
    ])
    renderRoutes(routes, '/login')

    await userEvent.type(await screen.findByLabelText('Username'), 'admin')
    await userEvent.type(screen.getByLabelText('Password'), 'secret-password')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => expect(screen.getByText('home')).toBeInTheDocument())
    const login = calls[1]
    expect(login.url).toBe('/api/auth/login')
    expect((login.init!.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('t2')
    expect(JSON.parse(String(login.init?.body))).toEqual({ username: 'admin', password: 'secret-password' })
  })
})
