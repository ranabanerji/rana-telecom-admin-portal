"use client"

import { responseError } from "@/lib/api-error"
import { useState, useEffect, useCallback } from "react"

export interface Device {
  id: number
  imei: string
  model: string
  sim_number: string
  customer_id: number | null
  customer_name: string | null
  status: "AVAILABLE" | "ASSIGNED" | "LOST" | "DAMAGED"
  assigned_at: string | null
  created_at: string | null
}

export interface DeviceCreate {
  imei: string
  model: string
  simNumber: string
  customerId?: number | null
}

export interface DeviceUpdate extends DeviceCreate {
  status?: string
}

export function useDevices() {
  const [devices, setDevices] = useState<Device[]>([])
  const [totalCount, setTotalCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchDevices = useCallback(async () => {
    try {
      setLoading(true)
      const res = await fetch("/api/devices")
      if (!res.ok) throw new Error("Failed to fetch devices")
      const data = await res.json()
      setTotalCount(Number(res.headers?.get("X-Total-Count") ?? data.length))
      setDevices(data)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unknown error")
    } finally {
      setLoading(false)
    }
  }, [])

  const createDevice = useCallback(async (device: DeviceCreate) => {
    const res = await fetch("/api/devices", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(device),
    })
    if (!res.ok) throw await responseError(res, "Failed to create device")
    const newDevice = await res.json()
    setTotalCount((count) => count + 1)
    setDevices((prev) => [...prev, newDevice])
    return newDevice
  }, [])

  const updateDevice = useCallback(async (id: number, device: DeviceUpdate) => {
    const res = await fetch(`/api/devices/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(device),
    })
    if (!res.ok) throw await responseError(res, "Failed to update device")
    const updated = await res.json()
    setDevices((prev) => prev.map((d) => (d.id === id ? updated : d)))
    return updated
  }, [])

  const deleteDevice = useCallback(async (id: number) => {
    const res = await fetch(`/api/devices/${id}`, {
      method: "DELETE",
    })
    if (!res.ok) throw await responseError(res, "Failed to delete device")
    setTotalCount((count) => Math.max(0, count - 1))
    setDevices((prev) => prev.filter((d) => d.id !== id))
  }, [])

  useEffect(() => {
    fetchDevices()
  }, [fetchDevices])

  return {
    devices,
    totalCount,
    loading,
    error,
    fetchDevices,
    createDevice,
    updateDevice,
    deleteDevice,
  }
}
