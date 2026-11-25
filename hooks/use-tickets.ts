"use client"

import { apiFetch } from "@/lib/api"

import { useCallback } from "react"
import { usePagedList } from "@/hooks/use-paged-list"
import { responseError } from "@/lib/api-error"
import { createLogger } from "@/lib/logger"

const log = createLogger("useTickets")

export interface Ticket {
  id: number
  customer_id: number
  customer_name: string
  subject: string
  description: string | null
  priority: "LOW" | "MEDIUM" | "HIGH" | "URGENT"
  status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED"
  created_at: string | null
  resolved_at: string | null
}

export interface TicketFilters {
  priority?: "LOW" | "MEDIUM" | "HIGH" | "URGENT" | null
  status?: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED" | null
  customerId?: number | null
}

export interface TicketCreate {
  customerId: number
  subject: string
  description?: string
  priority: string
}

export interface TicketUpdate {
  customerId?: number
  subject: string
  description?: string
  priority?: string
  status?: string
}

export function useTickets(filters: TicketFilters = {}) {
  const params = new URLSearchParams()
  if (filters.priority) params.set("priority", String(filters.priority))
  if (filters.status) params.set("status", String(filters.status))
  if (filters.customerId) params.set("customerId", String(filters.customerId))
  const {
    items: tickets,
    setItems: setTickets,
    loading,
    loadingMore,
    error,
    hasNext,
    totalElements,
    loadMore,
    refresh: fetchTickets,
  } = usePagedList<Ticket>(`/api/tickets?${params.toString()}`)

  const createTicket = useCallback(
    async (ticket: TicketCreate) => {
      log.info({ customerId: ticket.customerId }, "Creating ticket")
      const res = await apiFetch("/api/tickets", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(ticket),
      })
      if (!res.ok) {
        log.error({ status: res.status }, "Failed to create ticket")
        throw await responseError(res, "Failed to create ticket")
      }
      const newTicket = await res.json()
      log.info({ ticketId: newTicket.id }, "Ticket created")
      fetchTickets(true)
      return newTicket
    },
    [fetchTickets]
  )

  const updateTicket = useCallback(
    async (id: number, ticket: TicketUpdate) => {
      log.info({ ticketId: id }, "Updating ticket")
      const res = await apiFetch(`/api/tickets/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(ticket),
      })
      if (!res.ok) {
        log.error({ ticketId: id, status: res.status }, "Failed to update ticket")
        throw await responseError(res, "Failed to update ticket")
      }
      const updated = await res.json()
      log.info({ ticketId: id, status: updated.status }, "Ticket updated")
      setTickets((prev) => prev.map((t) => (t.id === id ? updated : t)))
      return updated
    },
    [setTickets]
  )

  const deleteTicket = useCallback(
    async (id: number) => {
      log.info({ ticketId: id }, "Deleting ticket")
      const res = await apiFetch(`/api/tickets/${id}`, {
        method: "DELETE",
      })
      if (!res.ok) {
        log.error({ ticketId: id, status: res.status }, "Failed to delete ticket")
        throw await responseError(res, "Failed to delete ticket")
      }
      log.info({ ticketId: id }, "Ticket deleted")
      setTickets((prev) => prev.filter((t) => t.id !== id))
    },
    [setTickets]
  )

  return {
    tickets,
    loading,
    loadingMore,
    error,
    hasNext,
    totalElements,
    loadMore,
    refresh: () => fetchTickets(true),
    createTicket,
    updateTicket,
    deleteTicket,
  }
}
