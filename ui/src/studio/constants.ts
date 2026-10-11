import type { ManageType } from '../grammar/types'

export const MANAGE_TYPES: Record<ManageType, string> = {
  VIEW: 'Viewer grid only. Rows arrive via REST or direct DB insert.',
  MANAGE_VIEW: 'Admins edit rows in a generated form; viewers see the grid.',
  DATA_SOURCE: 'No end-user UI. Loaded directly; feeds other tables as a lookup.',
}

export const MANAGE_COLORS: Record<string, string> = { VIEW: 'blue', MANAGE_VIEW: 'teal', DATA_SOURCE: 'gray' }
