import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'

import { ME_KEY } from './api/auth'
import { ApiError, setGenerationListener } from './api/client'

/** Any 401 means the session is gone: drop the cached user so the app routes to /login. */
export function createQueryClient(): QueryClient {
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({ onError: (e) => onAuthError(client, e) }),
    mutationCache: new MutationCache({ onError: (e) => onAuthError(client, e) }),
    defaultOptions: {
      queries: {
        retry: (count, e) => !(e instanceof ApiError && e.status < 500) && count < 2,
        refetchOnWindowFocus: false,
      },
    },
  })
  setGenerationListener(() => {
    client.invalidateQueries({ queryKey: ['meta'] })
    client.invalidateQueries({ queryKey: ['data'] })
  })
  return client
}

function onAuthError(client: QueryClient, e: unknown) {
  if (e instanceof ApiError && e.status === 401) {
    client.setQueryData(ME_KEY, null)
  }
}
