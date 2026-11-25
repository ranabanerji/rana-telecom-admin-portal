import { render, screen, waitFor } from "@testing-library/react"
import { afterEach, expect, it, vi } from "vitest"
import DashboardPage from "@/app/dashboard/page"

vi.mock("@/components/app-sidebar", () => ({ AppSidebar: () => null }))
vi.mock("@/components/health-status", () => ({ HealthStatus: () => null }))
vi.mock("@/components/ui/sidebar", () => ({
  SidebarProvider: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
  SidebarInset: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
  SidebarTrigger: () => null,
}))
vi.mock("@/components/ui/chart", () => ({
  ChartContainer: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
  ChartTooltip: () => null,
  ChartTooltipContent: () => null,
}))
vi.mock("recharts", () => ({
  BarChart: () => <div>Bar chart</div>,
  PieChart: () => <div>Pie chart</div>,
  Bar: () => null,
  Pie: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
}))

afterEach(() => vi.unstubAllGlobals())

it("renders healthy dashboard cards alongside a chart-specific error", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => ({
      ok: !url.endsWith("devices-by-status"),
      json: async () =>
        url.endsWith("stats")
          ? { active_customers: 5, monthly_revenue: 123, open_tickets: 2, devices_in_use: 3 }
          : [],
    }))
  )
  render(<DashboardPage />)
  await waitFor(() =>
    expect(screen.getByRole("alert")).toHaveTextContent("Unable to load this chart")
  )
  expect(screen.getByText("$123.00")).toBeVisible()
  expect(screen.getByText("Customers by Plan")).toBeVisible()
  expect(screen.getByText("Ticket Status")).toBeVisible()
  expect(screen.getAllByText("Pie chart")).toHaveLength(2)
})
