import { Alert } from '@mantine/core'
import { Outlet } from 'react-router'

import { isAdmin, useMe } from '../api/auth'

/** Admin-only routes; the API enforces the same rule (/api/admin/**). */
export function RequireAdmin() {
  const { data: me } = useMe()
  if (!isAdmin(me)) {
    return <Alert color="red" title="Administrators only">You need the admin role to open this page.</Alert>
  }
  return <Outlet />
}
