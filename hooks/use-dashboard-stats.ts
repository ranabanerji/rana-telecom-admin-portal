"use client"

import { useState, useEffect, useCallback } from "react"

export interface DashboardStats {
  active_customers: number
  monthly_revenue: number
  open_tickets: number
  devices_in_use: number
}

export interface ChartData {
  name: string
  value: number
}

export interface RevenueData {
  name: string
  revenue: number
}

export function useDashboardStats() {
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [customersByPlan, setCustomersByPlan] = useState<ChartData[]>([])
  const [devicesByStatus, setDevicesByStatus] = useState<ChartData[]>([])
  const [ticketsByStatus, setTicketsByStatus] = useState<ChartData[]>([])
  const [revenueByPlan, setRevenueByPlan] = useState<RevenueData[]>([])
  const [loading, setLoading] = useState(true)
  const [chartErrors, setChartErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)

  const fetchStats = useCallback(async () => {
    try {
      setLoading(true)
      const endpoints = [
        "stats",
        "customers-by-plan",
        "devices-by-status",
        "tickets-by-status",
        "revenue-by-plan",
      ]
      const results = await Promise.allSettled(
        endpoints.map(async (endpoint) => {
          const response = await fetch(`/api/dashboard/${endpoint}`)
          if (!response.ok)
            throw new Error(
              endpoint === "stats" ? "Failed to fetch stats" : "Unable to load this chart"
            )
          const data = await response.json()
          if (endpoint !== "stats" && !Array.isArray(data))
            throw new Error("Invalid chart response")
          return data
        })
      )
      const [summary, ...charts] = results
      if (summary.status === "fulfilled") {
        setStats(summary.value)
        setError(null)
      } else {
        setStats(null)
        setError("Failed to fetch stats")
      }
      const setters = [setCustomersByPlan, setDevicesByStatus, setTicketsByStatus, setRevenueByPlan]
      const errors: Record<string, string> = {}
      charts.forEach((result, index) => {
        setters[index](result.status === "fulfilled" ? result.value : [])
        if (result.status === "rejected")
          errors[endpoints[index + 1]] = "Unable to load this chart. Please refresh to retry."
      })
      setChartErrors(errors)
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unknown error")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchStats()
  }, [fetchStats])

  return {
    stats,
    customersByPlan,
    devicesByStatus,
    ticketsByStatus,
    revenueByPlan,
    loading,
    error,
    chartErrors,
    fetchStats,
  }
}
