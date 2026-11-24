import { render, screen } from "@testing-library/react"
import { describe, it, expect, vi } from "vitest"
import { CustomersTable } from "@/components/customers-table"
import { TicketsTable } from "@/components/tickets-table"
import type { Customer } from "@/hooks/use-customers"
import type { Ticket } from "@/hooks/use-tickets"

const customer: Customer = {
  id: 1,
  first_name: "Ada",
  last_name: "Lovelace",
  email: "ada@example.com",
  phone: "555-0100",
  plan_id: 2,
  plan_name: "Standard",
  status: "ACTIVE",
  balance: 12.3,
  activated_at: null,
  created_at: null,
}

const ticket: Ticket = {
  id: 7,
  customer_id: 1,
  customer_name: "Ada Lovelace",
  subject: "Billing",
  description: null,
  priority: "LOW",
  status: "OPEN",
  created_at: null,
  resolved_at: null,
}

describe("role-based table actions", () => {
  it("hides the actions column entirely for read-only users", () => {
    render(
      <CustomersTable
        customers={[customer]}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
        canEdit={false}
        canDelete={false}
      />
    )

    expect(screen.queryByRole("columnheader", { name: "Actions" })).not.toBeInTheDocument()
    expect(screen.queryByRole("button")).not.toBeInTheDocument()
  })

  it("shows edit but not delete when only editing is allowed", () => {
    const onEdit = vi.fn()
    render(<TicketsTable tickets={[ticket]} onEdit={onEdit} onDelete={vi.fn()} canDelete={false} />)

    screen.getByRole("button", { name: "Edit ticket 7" }).click()

    expect(onEdit).toHaveBeenCalledWith(ticket)
    expect(screen.queryByRole("button", { name: "Delete ticket 7" })).not.toBeInTheDocument()
  })

  it("labels action buttons with the row they act on", () => {
    render(<CustomersTable customers={[customer]} onEdit={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.getByRole("button", { name: "Edit Ada Lovelace" })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Delete Ada Lovelace" })).toBeInTheDocument()
  })
})
