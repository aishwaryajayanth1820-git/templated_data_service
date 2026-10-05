/**
 * Thin fetch wrapper for the TDS API: same-origin session cookie, CSRF header for writes,
 * and RFC 9457 ProblemDetail errors surfaced as {@link ApiError}.
 */

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

interface ProblemBody {
  title?: string
  detail?: string
  code?: string
  errors?: Record<string, string>
  general?: string[]
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly title: string
  readonly errors: Record<string, string>
  readonly general: string[]

  constructor(status: number, body: ProblemBody | undefined) {
    super(body?.detail ?? body?.title ?? `Request failed (${status})`)
    this.name = 'ApiError'
    this.status = status
    this.code = body?.code ?? 'UNKNOWN'
    this.title = body?.title ?? 'Error'
    this.errors = body?.errors ?? {}
    this.general = body?.general ?? []
  }

  get isCsrf(): boolean {
    return this.status === 403 && this.code === 'FORBIDDEN' && /csrf/i.test(this.message)
  }
}

export function readCookie(name: string): string | undefined {
  const entry = document.cookie.split('; ').find((c) => c.startsWith(`${name}=`))
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : undefined
}

async function primeCsrf(): Promise<void> {
  await fetch('/api/auth/csrf', { credentials: 'same-origin', headers: { Accept: 'application/json' } })
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  headers?: Record<string, string>
}

// The server stamps every API response with the catalog generation; a change means a template was published (ADR-0007).
const GENERATION_HEADER = 'X-TDS-Catalog-Generation'
let generation: string | null = null
let onGenerationChange: (() => void) | null = null

export function setGenerationListener(listener: (() => void) | null) {
  onGenerationChange = listener
}

function trackGeneration(res: Response) {
  const g = res.headers?.get?.(GENERATION_HEADER)
  if (!g) return
  if (generation !== null && g !== generation) onGenerationChange?.()
  generation = g
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    if (!readCookie(CSRF_COOKIE)) await primeCsrf()
    const token = readCookie(CSRF_COOKIE)
    if (token) headers[CSRF_HEADER] = token
  }
  return fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })
}

async function parse<T>(res: Response): Promise<T> {
  trackGeneration(res)
  if (res.status === 204) return undefined as T
  const text = await res.text()
  const data: unknown = text ? JSON.parse(text) : undefined
  if (!res.ok) throw new ApiError(res.status, data as ProblemBody | undefined)
  return data as T
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  try {
    return await parse<T>(await send(path, options))
  } catch (e) {
    // A stale CSRF cookie (e.g. after the session expired) is refreshed once, then retried.
    if (e instanceof ApiError && e.isCsrf) {
      await primeCsrf()
      return parse<T>(await send(path, options))
    }
    throw e
  }
}
