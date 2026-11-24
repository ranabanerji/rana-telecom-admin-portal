import { fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import PlansPage from "@/app/plans/page"
import CustomersPage from "@/app/customers/page"
import DevicesPage from "@/app/devices/page"

vi.mock("@/components/auth-provider", () => ({
  useAuth: () => ({ can: () => true }),
}))
beforeEach(() => {
  document.cookie = "XSRF-TOKEN=test-token"
})

// Keep the real pages, tables, dialogs and data hooks; remove unrelated navigation.
vi.mock("@/components/app-sidebar", () => ({ AppSidebar: () => null }))
vi.mock("@/components/health-status", () => ({ HealthStatus: () => null }))
vi.mock("@/components/ui/sidebar", () => ({
  SidebarProvider: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
  SidebarInset: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
  SidebarTrigger: () => null,
}))

const plan = {
  id: 1,
  name: "Basic",
  monthly_price: 20,
  data_limit_gb: 1,
  minutes_limit: 1,
  sms_limit: 1,
  is_active: true,
}
const customer = {
  id: 1,
  first_name: "Ada",
  last_name: "Lovelace",
  email: "ada@example.com",
  phone: "555",
  plan_id: 1,
  plan_name: "Basic",
  status: "ACTIVE",
  balance: 0,
}
const device = {
  id: 1,
  imei: "123",
  model: "Phone",
  sim_number: "456",
  customer_id: null,
  status: "AVAILABLE",
}

afterEach(() => vi.unstubAllGlobals())

for (const { name, Component, rows, total, message } of [
  {
    name: "plans",
    Component: PlansPage,
    rows: [plan],
    total: 101,
    message: "Customers use this plan. Deactivate it instead.",
  },
  {
    name: "customers",
    Component: CustomersPage,
    rows: [customer],
    total: 1200,
    message: "Customer has tickets, usage or devices.",
  },
  {
    name: "devices",
    Component: DevicesPage,
    rows: [device],
    total: 1200,
    message: "Device cannot be deleted.",
  },
])
  describe(`${name} page`, () => {
    it("shows bounded-list totals and preserves the dialog and row on a rejected delete", async () => {
      vi.stubGlobal(
        "fetch",
        vi.fn(async (url: string, init?: RequestInit) => {
          if (init?.method === "DELETE")
            return { ok: false, json: async () => ({ detail: message }) } as Response
          return {
            ok: true,
            headers: new Headers({ "X-Total-Count": String(url.endsWith(name) ? total : 1) }),
            json: async () => (url.endsWith(name) ? rows : [plan]),
          } as Response
        })
      )
      render(<Component />)
      await waitFor(() =>
        expect(screen.getByRole("status")).toHaveTextContent(`Showing 1 of ${total}`)
      )
      const row = screen.getAllByRole("row")[1]
      const buttons = within(row).getAllByRole("button")
      fireEvent.click(buttons[buttons.length - 1])
      const dialog = screen.getByRole("alertdialog")
      fireEvent.click(within(dialog).getByRole("button", { name: "Delete" }))
      await waitFor(() => expect(within(dialog).getByRole("alert")).toHaveTextContent(message))
      expect(screen.getByRole("alertdialog")).toBeVisible()
      expect(row).toBeInTheDocument()
    })
  })
