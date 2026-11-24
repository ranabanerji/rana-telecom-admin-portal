"use client"

import { responseError } from "@/lib/api-error"
import { useState, useEffect, useCallback } from "react"

export interface Customer {
  id: number
  first_name: string
  last_name: string
  email: string
  phone: string
  plan_id: number | null
  plan_name: string | null
  status: "ACTIVE" | "SUSPENDED" | "CANCELLED"
  balance: number
  activated_at: string | null
  created_at: string | null
}

export interface CustomerCreate {
  firstName: string
  lastName: string
  email: string
  phone: string
  planId: number
  balance?: number
}

export interface CustomerUpdate extends CustomerCreate {
  status?: string
}

export function useCustomers() {
  const [customers, setCustomers] = useState<Customer[]>([])
  const [totalCount, setTotalCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchCustomers = useCallback(async () => {
    try {
      setLoading(true)
      const res = await fetch("/api/customers")
      if (!res.ok) throw new Error("Failed to fetch customers")
      const data = await res.json()
      setTotalCount(Number(res.headers?.get("X-Total-Count") ?? data.length))
      setCustomers(data)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unknown error")
    } finally {
      setLoading(false)
    }
  }, [])

  const createCustomer = useCallback(async (customer: CustomerCreate) => {
    const res = await fetch("/api/customers", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(customer),
    })
    if (!res.ok) throw await responseError(res, "Failed to create customer")
    const newCustomer = await res.json()
    setTotalCount((count) => count + 1)
    setCustomers((prev) => [...prev, newCustomer])
    return newCustomer
  }, [])

  const updateCustomer = useCallback(async (id: number, customer: CustomerUpdate) => {
    const res = await fetch(`/api/customers/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(customer),
    })
    if (!res.ok) throw await responseError(res, "Failed to update customer")
    const updated = await res.json()
    setCustomers((prev) => prev.map((c) => (c.id === id ? updated : c)))
    return updated
  }, [])

  const deleteCustomer = useCallback(async (id: number) => {
    const res = await fetch(`/api/customers/${id}`, {
      method: "DELETE",
    })
    if (!res.ok) throw await responseError(res, "Failed to delete customer")
    setTotalCount((count) => Math.max(0, count - 1))
    setCustomers((prev) => prev.filter((c) => c.id !== id))
  }, [])

  useEffect(() => {
    fetchCustomers()
  }, [fetchCustomers])

  return {
    customers,
    totalCount,
    loading,
    error,
    fetchCustomers,
    createCustomer,
    updateCustomer,
    deleteCustomer,
  }
}
