import { test, expect } from "@playwright/test"
import { reloadAndWaitForResponse } from "./page-helpers"

test.describe("Customers Page", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/customers")
  })

  test("should display customers table", async ({ page }) => {
    await expect(page.getByLabel("breadcrumb")).toBeVisible()

    const table = page.locator("table")
    await expect(table).toBeVisible()
  })

  test("should load customers from API", async ({ page }) => {
    const response = await reloadAndWaitForResponse(page, "/api/customers")
    expect(response.status()).toBe(200)
    const data = await response.json()
    expect(Array.isArray(data)).toBeTruthy()
    expect(data.length).toBeGreaterThan(0)
  })

  test("should display customer data in table rows", async ({ page }) => {
    await page.waitForSelector("table tbody tr", { timeout: 10000 })
    const rows = page.locator("table tbody tr")
    const rowCount = await rows.count()
    expect(rowCount).toBeGreaterThan(0)
  })

  test("should have add customer button", async ({ page }) => {
    const addButton = page.getByRole("button", { name: "Add Customer", exact: true })
    await expect(addButton).toBeVisible()
  })
})
