"use client"

import { useState, useEffect, useCallback } from "react"
import { apiFetch } from "@/lib/api"

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
  const [error, setError] = useState<string | null>(null)

  const fetchStats = useCallback(async () => {
    try {
      setLoading(true)
      const [statsRes, customersPlanRes, devicesStatusRes, ticketsStatusRes, revenueRes] =
        await Promise.all([
          apiFetch("/api/dashboard/stats"),
          apiFetch("/api/dashboard/customers-by-plan"),
          apiFetch("/api/dashboard/devices-by-status"),
          apiFetch("/api/dashboard/tickets-by-status"),
          apiFetch("/api/dashboard/revenue-by-plan"),
        ])

      if (!statsRes.ok) throw new Error("Failed to fetch stats")

      setStats(await statsRes.json())
      setCustomersByPlan(await customersPlanRes.json())
      setDevicesByStatus(await devicesStatusRes.json())
      setTicketsByStatus(await ticketsStatusRes.json())
      setRevenueByPlan(await revenueRes.json())
      setError(null)
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
    fetchStats,
  }
}
