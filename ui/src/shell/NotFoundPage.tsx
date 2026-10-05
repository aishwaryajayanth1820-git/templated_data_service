import { Button, Stack, Text, Title } from '@mantine/core'
import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <Stack align="flex-start">
      <Title order={2}>Page not found</Title>
      <Text c="dimmed">The page you opened does not exist or is not available to you.</Text>
      <Button component={Link} to="/" variant="default">
        Go to start
      </Button>
    </Stack>
  )
}
