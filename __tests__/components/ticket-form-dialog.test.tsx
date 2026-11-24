import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"
import { TicketFormDialog } from "@/components/ticket-form-dialog"
import type { Ticket } from "@/hooks/use-tickets"

const ticket: Ticket = {
  id: 1,
  customer_id: 1,
  customer_name: "Ada",
  subject: "Help",
  description: "Old description",
  priority: "MEDIUM",
  status: "OPEN",
  created_at: null,
  resolved_at: null,
}

describe("optional ticket description", () => {
  it("creates a ticket with an explicit empty string", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const onOpenChange = vi.fn()
    render(<TicketFormDialog open ticket={null} onOpenChange={onOpenChange} onSubmit={onSubmit} />)
    fireEvent.change(screen.getByLabelText("Customer ID"), { target: { value: "1" } })
    fireEvent.change(screen.getByLabelText("Subject"), { target: { value: "Help" } })
    fireEvent.submit(screen.getByRole("button", { name: "Create Ticket" }).closest("form")!)
    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        customerId: 1,
        subject: "Help",
        description: "",
        priority: "MEDIUM",
      })
    )
    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it("clears an existing description and keeps a rejected edit open with its error", async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error("subject: Please check this field"))
    const onOpenChange = vi.fn()
    render(
      <TicketFormDialog open ticket={ticket} onOpenChange={onOpenChange} onSubmit={onSubmit} />
    )
    fireEvent.change(screen.getByLabelText("Description"), { target: { value: "" } })
    fireEvent.submit(screen.getByRole("button", { name: "Save Changes" }).closest("form")!)
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent("Please check this field")
    )
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ description: "" }))
    expect(onOpenChange).not.toHaveBeenCalled()
  })
})
