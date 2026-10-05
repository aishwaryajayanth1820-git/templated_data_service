import { Center, Loader } from '@mantine/core'
import { Navigate, Outlet, useLocation } from 'react-router'

import { useMe } from '../api/auth'

/** Gate for signed-in routes; anonymous users go to /login and come back afterwards. */
export function RequireAuth() {
  const me = useMe()
  const location = useLocation()

  if (me.isPending) {
    return (
      <Center mih="100vh">
        <Loader />
      </Center>
    )
  }
  if (!me.data) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return <Outlet />
}
