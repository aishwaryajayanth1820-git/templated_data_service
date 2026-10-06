import { Anchor, Button, Code, CopyButton, Group, Modal, ScrollArea, Text } from '@mantine/core'

import { type ActionResult, outputUrl } from '../api/data'

interface Props {
  table: string
  result: ActionResult | null
  onClose: () => void
}

/** Shows a `text` action result with Copy and (when the script wrote a file) Download (grammar §8). */
export function ActionResultModal({ table, result, onClose }: Props) {
  return (
    <Modal opened={!!result} onClose={onClose} size="xl" title={<Text fw={600}>{result?.title ?? 'Result'}</Text>}>
      {result && (
        <>
          <ScrollArea.Autosize mah="60vh">
            <Code block style={{ whiteSpace: 'pre-wrap' }}>{result.content}</Code>
          </ScrollArea.Autosize>
          <Group justify="space-between" mt="md">
            {result.file ? (
              <Anchor href={outputUrl(table, result.file)} download>Download {result.file}</Anchor>
            ) : <span />}
            <CopyButton value={result.content ?? ''}>
              {({ copied, copy }) => (
                <Button onClick={copy} color={copied ? 'teal' : undefined}>{copied ? 'Copied' : 'Copy'}</Button>
              )}
            </CopyButton>
          </Group>
        </>
      )}
    </Modal>
  )
}
