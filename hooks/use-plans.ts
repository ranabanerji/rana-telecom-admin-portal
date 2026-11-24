"use client"

import { responseError } from "@/lib/api-error"
import { useState, useEffect, useCallback } from "react"
import { apiFetch } from "@/lib/api"

export interface Plan {
  id: number
  name: string
  monthly_price: number
  data_limit_gb: number | null
  minutes_limit: number | null
  sms_limit: number | null
  is_active: boolean
}

export interface PlanCreate {
  name: string
  monthlyPrice: number
  dataLimitGb?: number | null
  minutesLimit?: number | null
  smsLimit?: number | null
}

export interface PlanUpdate extends PlanCreate {
  isActive?: boolean
}

export function usePlans() {
  const [plans, setPlans] = useState<Plan[]>([])
  const [totalCount, setTotalCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchPlans = useCallback(async () => {
    try {
      setLoading(true)
      const res = await apiFetch("/api/plans")
      if (!res.ok) throw new Error("Failed to fetch plans")
      const data = await res.json()
      setTotalCount(Number(res.headers?.get("X-Total-Count") ?? data.length))
      setPlans(data)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unknown error")
    } finally {
      setLoading(false)
    }
  }, [])

  const createPlan = useCallback(async (plan: PlanCreate) => {
    const res = await apiFetch("/api/plans", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(plan),
    })
    if (!res.ok) throw await responseError(res, "Failed to create plan")
    const newPlan = await res.json()
    setTotalCount((count) => count + 1)
    setPlans((prev) => [...prev, newPlan])
    return newPlan
  }, [])

  const updatePlan = useCallback(async (id: number, plan: PlanUpdate) => {
    const res = await apiFetch(`/api/plans/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(plan),
    })
    if (!res.ok) throw await responseError(res, "Failed to update plan")
    const updated = await res.json()
    setPlans((prev) => prev.map((p) => (p.id === id ? updated : p)))
    return updated
  }, [])

  const deletePlan = useCallback(async (id: number) => {
    const res = await apiFetch(`/api/plans/${id}`, {
      method: "DELETE",
    })
    if (!res.ok) throw await responseError(res, "Failed to delete plan")
    setTotalCount((count) => Math.max(0, count - 1))
    setPlans((prev) => prev.filter((p) => p.id !== id))
  }, [])

  useEffect(() => {
    fetchPlans()
  }, [fetchPlans])

  return {
    plans,
    totalCount,
    loading,
    error,
    fetchPlans,
    createPlan,
    updatePlan,
    deletePlan,
  }
}
