import { test, expect, type Page } from "@playwright/test"
import { reloadAndWaitForResponse } from "./page-helpers"

const KPI_TITLES = ["Active Customers", "Monthly Revenue", "Open Tickets", "Devices in Use"]
const CHART_TITLES = ["Customers by Plan", "Revenue by Plan", "Device Status", "Ticket Status"]
// Pie slices and bars; each chart draws one per data row.
const CHART_MARKS = ".recharts-pie-sector, .recharts-bar-rectangle"

function card(page: Page, title: string) {
  return page
    .locator("[data-slot='card']")
    .filter({ has: page.locator("[data-slot='card-title']").getByText(title, { exact: true }) })
}

test.describe("Dashboard", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/dashboard")
  })

  test("should display a non-zero number in every KPI card", async ({ page }) => {
    await expect(page.getByLabel("breadcrumb")).toBeVisible()

    for (const title of KPI_TITLES) {
      // The page shows 0 when stats fail to load, and the seeded data has no zero totals.
      await expect(
        card(page, title).locator("[data-slot='card-content'] > div").first()
      ).toHaveText(/^\$?[1-9][\d,]*(\.\d{2})?$/)
    }
  })

  test("should load dashboard data from API", async ({ page }) => {
    const response = await reloadAndWaitForResponse(page, "/api/dashboard/stats")
    expect(response.status()).toBe(200)
    const stats = await response.json()
    expect(stats.active_customers).toBeGreaterThan(0)
    expect(stats.monthly_revenue).toBeGreaterThan(0)
  })

  test("should draw data in every chart", async ({ page }) => {
    for (const title of CHART_TITLES) {
      const chart = card(page, title)
      await expect(chart.locator("svg.recharts-surface")).toBeVisible()
      await expect(chart.locator(CHART_MARKS).first()).toBeVisible()
    }
  })

  test("should give every plan its own color", async ({ page }) => {
    for (const title of ["Customers by Plan", "Revenue by Plan"]) {
      const marks = card(page, title).locator(CHART_MARKS)
      await expect(marks.first()).toBeVisible()
      const fills = await marks.evaluateAll((elements) =>
        elements.map((element) => element.querySelector("path")?.getAttribute("fill") ?? "")
      )
      expect(fills.length).toBeGreaterThan(1)
      expect(fills).not.toContain("")
      expect(new Set(fills).size).toBe(fills.length)
    }
  })
})
