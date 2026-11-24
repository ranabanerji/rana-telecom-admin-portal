"use client"

import { usePagedList } from "@/hooks/use-paged-list"

export interface UsageRecord {
  id: number
  customer_id: number
  customer_name: string
  type: "CALL" | "DATA" | "SMS"
  quantity: number
  cost: number
  recorded_at: string | null
}

export interface UsageFilters {
  type?: "CALL" | "DATA" | "SMS" | null
  customerId?: number | null
  dateFrom?: string | null
  dateTo?: string | null
}

export function useUsage(filters: UsageFilters = {}) {
  const params = new URLSearchParams()
  if (filters.type) params.set("type", String(filters.type))
  if (filters.customerId) params.set("customerId", String(filters.customerId))
  if (filters.dateFrom) params.set("dateFrom", String(filters.dateFrom))
  if (filters.dateTo) params.set("dateTo", String(filters.dateTo))
  const {
    items: records,
    loading,
    loadingMore,
    error,
    hasNext,
    totalElements,
    loadMore,
    refresh: fetchRecords,
  } = usePagedList<UsageRecord>(`/api/usage?${params.toString()}`)

  return {
    records,
    loading,
    loadingMore,
    error,
    hasNext,
    totalElements,
    loadMore,
    refresh: () => fetchRecords(true),
  }
}
