import { describe, expect, it } from 'vitest'

import { mockFetch } from '../test/render'
import { ApiError, apiFetch } from './client'

describe('apiFetch', () => {
  it('turns ProblemDetail into ApiError with field errors', async () => {
    mockFetch([{ status: 422, body: { code: 'VALIDATION', title: 'Validation failed', detail: '1 problem found', errors: { a: 'Required' } } }])

    const err = await apiFetch('/api/x').catch((e: unknown) => e)

    expect(err).toBeInstanceOf(ApiError)
    expect(err).toMatchObject({ status: 422, code: 'VALIDATION', errors: { a: 'Required' }, message: '1 problem found' })
  })

  it('primes the CSRF cookie before the first write when it is missing', async () => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
    const calls = mockFetch([{ status: 200, body: {} }, { status: 204 }])

    await apiFetch('/api/auth/logout', { method: 'POST' })

    expect(calls.map((c) => c.url)).toEqual(['/api/auth/csrf', '/api/auth/logout'])
  })

  it('retries once after a CSRF rejection', async () => {
    document.cookie = 'XSRF-TOKEN=stale'
    const calls = mockFetch([
      { status: 403, body: { code: 'FORBIDDEN', detail: 'Missing or invalid CSRF token' } },
      { status: 200, body: {} },
      { status: 200, body: { ok: true } },
    ])

    const res = await apiFetch<{ ok: boolean }>('/api/thing', { method: 'POST', body: {} })

    expect(res).toEqual({ ok: true })
    expect(calls.map((c) => c.url)).toEqual(['/api/thing', '/api/auth/csrf', '/api/thing'])
  })
})
