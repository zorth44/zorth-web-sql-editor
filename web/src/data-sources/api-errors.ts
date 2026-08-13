import { isApiError } from '@/api/api-error'
import type { FieldError, VersionConflictDetails, DataSourceInUseDetails } from '@/types/contracts'

export function apiFieldErrors(error: unknown): FieldError[] {
  if (
    !isApiError(error) ||
    error.code !== 'VALIDATION_FAILED' ||
    !error.details ||
    !('fieldErrors' in error.details)
  )
    return []
  return Array.isArray(error.details.fieldErrors) ? error.details.fieldErrors : []
}
export function versionConflict(error: unknown): VersionConflictDetails | null {
  if (
    !isApiError(error) ||
    error.code !== 'VERSION_CONFLICT' ||
    !error.details ||
    !('currentVersion' in error.details)
  )
    return null
  return error.details as unknown as VersionConflictDetails
}
export function dataSourceInUse(error: unknown): DataSourceInUseDetails | null {
  if (
    !isApiError(error) ||
    error.code !== 'DATA_SOURCE_IN_USE' ||
    !error.details ||
    !('runningTaskCount' in error.details)
  )
    return null
  return error.details as unknown as DataSourceInUseDetails
}
