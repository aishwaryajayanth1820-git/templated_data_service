import { Alert, Button, Center, Paper, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core'
import { useForm } from '@mantine/form'
import { Navigate, useLocation } from 'react-router'

import { useLogin, useMe } from '../api/auth'

export function LoginPage() {
  const me = useMe()
  const login = useLogin()
  const location = useLocation()
  const form = useForm({
    initialValues: { username: '', password: '' },
    validate: {
      username: (v) => (v.trim() ? null : 'Enter your username'),
      password: (v) => (v ? null : 'Enter your password'),
    },
  })

  if (me.data) {
    const from = (location.state as { from?: string } | null)?.from ?? '/'
    return <Navigate to={from} replace />
  }

  return (
    <Center mih="100vh" p="md">
      <Paper withBorder shadow="sm" p="xl" radius="md" w="100%" maw={380}>
        <form onSubmit={form.onSubmit((values) => login.mutate(values))} noValidate>
          <Stack>
            <div>
              <Title order={2}>Sign in</Title>
              <Text c="dimmed" size="sm">
                Templated Data Service
              </Text>
            </div>
            {login.error && (
              <Alert color="red" variant="light" role="alert">
                {login.error.message}
              </Alert>
            )}
            <TextInput label="Username" autoComplete="username" autoFocus {...form.getInputProps('username')} />
            <PasswordInput label="Password" autoComplete="current-password" {...form.getInputProps('password')} />
            <Button type="submit" loading={login.isPending} fullWidth>
              Sign in
            </Button>
          </Stack>
        </form>
      </Paper>
    </Center>
  )
}
