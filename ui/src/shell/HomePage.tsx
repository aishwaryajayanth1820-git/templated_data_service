import { Badge, Card, Group, SimpleGrid, Stack, Text, Title } from '@mantine/core'
import { Link } from 'react-router'

import { isAdmin, useMe } from '../api/auth'
import { useNav } from '../api/meta'

const TYPE_COLORS: Record<string, string> = { VIEW: 'blue', MANAGE_VIEW: 'teal', DATA_SOURCE: 'gray' }

export function HomePage() {
  const { data: me } = useMe()
  const nav = useNav()
  const tables = nav.data?.tables ?? []
  return (
    <Stack maw={1100}>
      <Title order={2}>Welcome, {me?.displayName || me?.username}</Title>
      {nav.data && tables.length === 0 && (
        <Text c="dimmed">
          No tables are available to you yet. Tables appear as soon as an administrator publishes a template your roles can read.
        </Text>
      )}
      <SimpleGrid cols={{ base: 1, sm: 2, md: 3 }}>
        {tables.map((t) => (
          <Card key={t.name} withBorder radius="md" component={Link} to={`/t/${t.name}`}>
            <Group justify="space-between" mb={4}>
              <Text fw={600}>{t.label}</Text>
              <Badge size="sm" variant="light" color={TYPE_COLORS[t.manageType]}>{t.manageType.replace('_', ' ')}</Badge>
            </Group>
            <Text size="sm" c="dimmed" lineClamp={2}>{t.description ?? t.name}</Text>
          </Card>
        ))}
      </SimpleGrid>
      {isAdmin(me) && (
        <Text size="sm" c="dimmed">
          Administration: define tables in the <Link to="/admin/studio">Schema Studio</Link>, manage <Link to="/admin/roles">roles</Link> and{' '}
          <Link to="/admin/users">users</Link>.
        </Text>
      )}
    </Stack>
  )
}
