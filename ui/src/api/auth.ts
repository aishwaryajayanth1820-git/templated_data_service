import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError, apiFetch } from './client'

export interface Me {
  username: string
  displayName?: string
  roles: string[]
  mustChangePassword: boolean
}

export const ME_KEY = ['auth', 'me'] as const

/** The signed-in user, or `null` when there is no session. */
export function useMe() {
  return useQuery({
    queryKey: ME_KEY,
    queryFn: async (): Promise<Me | null> => {
      try {
        return await apiFetch<Me>('/api/auth/me')
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null
        throw e
      }
    },
    staleTime: 60_000,
    retry: false,
  })
}

export function isAdmin(me: Me | null | undefined): boolean {
  return !!me?.roles.includes('admin')
}

export function useLogin() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (credentials: { username: string; password: string }) =>
      apiFetch<Me>('/api/auth/login', { method: 'POST', body: credentials }),
    onSuccess: (me) => qc.setQueryData(ME_KEY, me),
  })
}

export function useLogout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiFetch<void>('/api/auth/logout', { method: 'POST' }),
    onSettled: () => {
      // Update the observed `me` query in place (clear() would detach its observers),
      // then drop everything else that belonged to the session.
      qc.setQueryData(ME_KEY, null)
      qc.removeQueries({ predicate: (q) => q.queryKey[0] !== ME_KEY[0] })
    },
  })
}

export function useChangePassword() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { currentPassword: string; newPassword: string }) =>
      apiFetch<Me>('/api/auth/password', { method: 'POST', body }),
    onSuccess: (me) => {
      qc.setQueryData(ME_KEY, me)
      // Requests made during a forced change were refused (403 PASSWORD_CHANGE_REQUIRED); load them again.
      qc.invalidateQueries({ predicate: (q) => q.queryKey[0] !== ME_KEY[0] })
    },
  })
}
