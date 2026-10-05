import {
  ActionIcon,
  AppShell,
  Avatar,
  Burger,
  Group,
  Menu,
  NavLink,
  ScrollArea,
  Text,
  Title,
  useComputedColorScheme,
  useMantineColorScheme,
} from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router'

import { isAdmin, useLogout, useMe } from '../api/auth'
import { useNav } from '../api/meta'
import { ChangePasswordModal } from './ChangePasswordModal'

const ADMIN_LINKS: [string, string][] = [
  ['/admin/studio', 'Schema Studio'],
  ['/admin/roles', 'Roles'],
  ['/admin/users', 'Users'],
]

export function AppLayout() {
  const { data: me } = useMe()
  const nav = useNav()
  const location = useLocation()
  const logout = useLogout()
  const [navOpened, { toggle: toggleNav }] = useDisclosure()
  const [changeOpen, setChangeOpen] = useState(false)
  const { setColorScheme } = useMantineColorScheme()
  const scheme = useComputedColorScheme('light')

  if (!me) return null
  const name = me.displayName || me.username

  return (
    <AppShell
      header={{ height: 52 }}
      navbar={{ width: 240, breakpoint: 'sm', collapsed: { mobile: !navOpened } }}
      padding="md"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Group gap="sm" wrap="nowrap">
            <Burger opened={navOpened} onClick={toggleNav} hiddenFrom="sm" size="sm" aria-label="Toggle navigation" />
            <Title order={4}>TDS</Title>
            <Text c="dimmed" size="sm" visibleFrom="xs">
              Templated Data Service
            </Text>
          </Group>
          <Group gap="xs" wrap="nowrap">
            <ActionIcon
              variant="default"
              size="lg"
              aria-label="Toggle color scheme"
              onClick={() => setColorScheme(scheme === 'dark' ? 'light' : 'dark')}
            >
              {scheme === 'dark' ? '☀' : '☾'}
            </ActionIcon>
            <Menu position="bottom-end" width={220}>
              <Menu.Target>
                <ActionIcon variant="default" size="lg" radius="xl" aria-label="User menu">
                  <Avatar size={28} radius="xl" color="blue">
                    {name.slice(0, 1).toUpperCase()}
                  </Avatar>
                </ActionIcon>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Label>
                  <Text size="sm" fw={600} c="var(--mantine-color-text)">
                    {name}
                  </Text>
                  <Text size="xs">{me.roles.join(', ')}</Text>
                </Menu.Label>
                <Menu.Divider />
                <Menu.Item onClick={() => setChangeOpen(true)}>Change password</Menu.Item>
                <Menu.Item color="red" onClick={() => logout.mutate()}>
                  Sign out
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="xs">
        <ScrollArea>
          <Text size="xs" fw={700} c="dimmed" tt="uppercase" px="sm" py={6}>
            Data
          </Text>
          {nav.data?.tables.map((t) => (
            <NavLink key={t.name} component={Link} to={`/t/${t.name}`} label={t.label}
              active={location.pathname === `/t/${t.name}`} onClick={() => navOpened && toggleNav()} />
          ))}
          {nav.data && nav.data.tables.length === 0 && (
            <Text size="sm" c="dimmed" px="sm">
              No tables yet
            </Text>
          )}
          {isAdmin(me) && (
            <>
              <Text size="xs" fw={700} c="dimmed" tt="uppercase" px="sm" pt="md" pb={6}>
                Administration
              </Text>
              {nav.data?.dataSources.map((t) => (
                <NavLink key={t.name} component={Link} to={`/admin/data/${t.name}`} label={t.label} description="data source"
                  active={location.pathname === `/admin/data/${t.name}`} onClick={() => navOpened && toggleNav()} />
              ))}
              {ADMIN_LINKS.map(([to, label]) => (
                <NavLink key={to} component={Link} to={to} label={label}
                  active={location.pathname === to || location.pathname.startsWith(`${to}/`)} onClick={() => navOpened && toggleNav()} />
              ))}
            </>
          )}
        </ScrollArea>
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>

      <ChangePasswordModal
        opened={me.mustChangePassword || changeOpen}
        forced={me.mustChangePassword}
        onClose={() => setChangeOpen(false)}
      />
    </AppShell>
  )
}
