import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { describe, expect, it } from 'vitest'

import { apply } from './jsonlogic'

interface Vector {
  name: string
  rule: unknown
  data: Record<string, unknown>
  expected: unknown
}

// Shared with JsonLogicParityTest.java (ADR-0003).
const vectors: Vector[] = JSON.parse(
  readFileSync(resolve(process.cwd(), '../testdata/parity/jsonlogic.json'), 'utf8'),
)

describe('jsonlogic parity vectors', () => {
  it.each(vectors.map((v) => [v.name, v] as const))('%s', (_, v) => {
    expect(apply(v.rule, v.data)).toEqual(v.expected)
  })
})
