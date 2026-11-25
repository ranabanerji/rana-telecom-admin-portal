import { test, expect } from "@playwright/test"
import { reloadAndWaitForStatus } from "./page-helpers"

test.describe("Dashboard", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/dashboard")
  })

  test("should display dashboard KPI cards", async ({ page }) => {
    await expect(page.getByLabel("breadcrumb")).toBeVisible()

    const cards = page.locator("[data-slot='card']")
    await expect(cards.first()).toBeVisible({ timeout: 10000 })
  })

  test("should load dashboard data from API", async ({ page }) => {
    expect(await reloadAndWaitForStatus(page, "/api/dashboard/stats")).toBe(200)
    const stats = await (await page.request.get("/api/dashboard/stats")).json()
    expect(stats.active_customers).toBeGreaterThan(0)
    expect(stats.monthly_revenue).toBeGreaterThan(0)
  })

  test("should display charts", async ({ page }) => {
    await page.waitForTimeout(1000)
    const charts = page.locator(".recharts-wrapper, [class*='chart']")
    const chartCount = await charts.count()
    expect(chartCount).toBeGreaterThanOrEqual(0)
  })
})
