import { MantineProvider } from '@mantine/core'
import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router'

import { createQueryClient } from '../queryClient'
import { theme } from '../theme'

/** Renders routes inside the same providers as the app, starting at `initialPath`. */
export function renderRoutes(routes: RouteObject[], initialPath = '/') {
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] })
  const client = createQueryClient()
  const result = render(
    <MantineProvider theme={theme}>
      <QueryClientProvider client={client}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </MantineProvider>,
  )
  return { ...result, router, client }
}

export function element(node: ReactElement): RouteObject {
  return { path: '*', element: node }
}

type Reply = { status: number; body?: unknown }

/**
 * Mocks fetch. Either a queue (each call consumes the next response), or a route map from URL path
 * (without query string) to a response — or to a list of responses used in order, the last one repeating.
 */
export function mockFetch(responses: Reply[] | Record<string, Reply | Reply[]>) {
  const calls: Array<{ url: string; init?: RequestInit }> = []
  const queue = Array.isArray(responses) ? [...responses] : []
  const routes: Record<string, Reply | Reply[]> = Array.isArray(responses)
    ? {}
    : Object.fromEntries(Object.entries(responses).map(([k, v]) => [k, Array.isArray(v) ? [...v] : v]))
  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ url: String(input), init })
    const path = String(input).split('?')[0]
    const route = routes[path]
    const routed = Array.isArray(route) ? (route.length > 1 ? route.shift() : route[0]) : route
    const next = routed ?? queue.shift() ?? { status: 500, body: { title: `unexpected call ${path}` } }
    const text = next.body === undefined ? '' : JSON.stringify(next.body)
    return new Response(next.status === 204 ? null : text, {
      status: next.status,
      headers: { 'Content-Type': 'application/json' },
    })
  }) as typeof fetch
  return calls
}
