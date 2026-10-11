import { Alert, Button, Group, Modal, PasswordInput, Stack, Text } from '@mantine/core'
import { useForm } from '@mantine/form'

import { useChangePassword, useLogout } from '../api/auth'
import { ApiError } from '../api/client'

const MIN_LENGTH = 10

interface Props {
  opened: boolean
  /** Forced change after bootstrap or a password reset: cannot be dismissed. */
  forced: boolean
  onClose: () => void
}

export function ChangePasswordModal({ opened, forced, onClose }: Props) {
  const change = useChangePassword()
  const logout = useLogout()
  const form = useForm({
    initialValues: { currentPassword: '', newPassword: '', confirm: '' },
    validate: {
      currentPassword: (v) => (v ? null : 'Enter your current password'),
      newPassword: (v) => (v.length >= MIN_LENGTH ? null : `Use at least ${MIN_LENGTH} characters`),
      confirm: (v, values) => (v === values.newPassword ? null : 'Passwords do not match'),
    },
  })

  const submit = form.onSubmit(({ currentPassword, newPassword }) =>
    change.mutate(
      { currentPassword, newPassword },
      {
        onSuccess: () => {
          form.reset()
          onClose()
        },
        onError: (e) => {
          if (e instanceof ApiError) form.setErrors(e.errors)
        },
      },
    ),
  )
  const general = change.error instanceof ApiError && Object.keys(change.error.errors).length === 0 ? change.error.message : null

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title="Change password"
      withCloseButton={!forced}
      closeOnClickOutside={!forced}
      closeOnEscape={!forced}
      centered
    >
      <form onSubmit={submit} noValidate>
        <Stack>
          {forced && (
            <Text size="sm">Your password was set by the system. Choose your own password to continue.</Text>
          )}
          {general && (
            <Alert color="red" variant="light">
              {general}
            </Alert>
          )}
          <PasswordInput label="Current password" autoComplete="current-password" {...form.getInputProps('currentPassword')} />
          <PasswordInput
            label="New password"
            description={`At least ${MIN_LENGTH} characters`}
            autoComplete="new-password"
            {...form.getInputProps('newPassword')}
          />
          <PasswordInput label="Confirm new password" autoComplete="new-password" {...form.getInputProps('confirm')} />
          <Group justify="space-between">
            {forced ? (
              <Button variant="subtle" color="gray" onClick={() => logout.mutate()}>
                Sign out
              </Button>
            ) : (
              <Button variant="default" onClick={onClose}>
                Cancel
              </Button>
            )}
            <Button type="submit" loading={change.isPending}>
              Change password
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  )
}
