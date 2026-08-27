import { isAbortError } from '@/api/api-error'

export function suggestedExportFilename(sourceName: string, database: string | null): string {
  return `${safeExportName(sourceName)}-${safeExportName(database || 'database')}.csv`
}

export function canPickSaveFile(target: object): target is SaveFilePickerHost {
  return typeof (target as SaveFilePickerHost).showSaveFilePicker === 'function'
}

export async function pickExportWritable(
  host: SaveFilePickerHost,
  suggestedName: string,
): Promise<WritableStream<Uint8Array> | null> {
  try {
    const handle = await host.showSaveFilePicker({
      suggestedName,
      types: [{ description: 'CSV', accept: { 'text/csv': ['.csv'] } }],
    })
    return await handle.createWritable()
  } catch (error) {
    if (isAbortError(error)) return null
    throw error
  }
}

function safeExportName(value: string): string {
  const sanitized = value.replace(/[^A-Za-z0-9._-]+/g, '-')
  return sanitized || 'export'
}

export type SaveFilePickerHost = {
  showSaveFilePicker: (options: {
    suggestedName?: string
    types?: { description?: string; accept: Record<string, string[]> }[]
  }) => Promise<{ createWritable: () => Promise<WritableStream<Uint8Array>> }>
}
