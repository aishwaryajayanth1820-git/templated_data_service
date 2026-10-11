import { createBrowserRouter } from 'react-router'

import { RolesPage } from './admin/RolesPage'
import { UsersPage } from './admin/UsersPage'
import { TemplatePage } from './data/TemplatePage'
import { AppLayout } from './shell/AppLayout'
import { HomePage } from './shell/HomePage'
import { LoginPage } from './shell/LoginPage'
import { NotFoundPage } from './shell/NotFoundPage'
import { RequireAdmin } from './shell/RequireAdmin'
import { RequireAuth } from './shell/RequireAuth'
import { StudioEditorPage } from './studio/StudioEditorPage'
import { StudioListPage } from './studio/StudioListPage'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { index: true, element: <HomePage /> },
          { path: 't/:name', element: <TemplatePage /> },
          {
            path: 'admin',
            element: <RequireAdmin />,
            children: [
              { path: 'data/:name', element: <TemplatePage adminBrowser /> },
              { path: 'studio', element: <StudioListPage /> },
              { path: 'studio/:name', element: <StudioEditorPage /> },
              { path: 'roles', element: <RolesPage /> },
              { path: 'users', element: <UsersPage /> },
            ],
          },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])
