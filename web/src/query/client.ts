import { QueryClient } from '@tanstack/vue-query'
import { isApiError } from '@/api/api-error'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) =>
        failureCount < 1 && (!isApiError(error) || error.status === 0 || error.status >= 500),
      refetchOnWindowFocus: true,
    },
    mutations: { retry: false },
  },
})

export const queryKeys = {
  session: ['session'] as const,
  dataSources: ['data-sources'] as const,
  dataSourceLists: () => ['data-sources', 'list'] as const,
  dataSourceList: (keyword: string, pageSize: number, pageToken?: string) =>
    ['data-sources', 'list', { keyword, pageSize, pageToken: pageToken || '' }] as const,
  dataSourceDetails: () => ['data-sources', 'detail'] as const,
  dataSourceDetail: (id: string) => ['data-sources', 'detail', id] as const,
}
