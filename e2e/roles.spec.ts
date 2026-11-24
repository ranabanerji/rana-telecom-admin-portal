import { test, expect } from "@playwright/test"
import { ADMIN_STATE, AGENT_STATE, agentLogin, backendUrl, csrfToken } from "./auth-helpers"

test.describe("Agent permissions", () => {
  test.use({ storageState: AGENT_STATE })

  test("customers are read-only for agents", async ({ page }) => {
    await page.goto("/customers")
    await expect(page.locator("table tbody tr").first()).toBeVisible({ timeout: 10000 })

    await expect(page.getByTestId("nav-user-name")).toHaveText(agentLogin().name)
    await expect(page.getByRole("button", { name: "Add Customer" })).toHaveCount(0)
    await expect(page.getByRole("button", { name: /^(Edit|Delete) / })).toHaveCount(0)
    await expect(page.getByRole("columnheader", { name: "Actions" })).toHaveCount(0)
  })

  test("plans and devices are read-only for agents", async ({ page }) => {
    await page.goto("/plans")
    await expect(page.locator("table tbody tr").first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByRole("button", { name: "Add Plan" })).toHaveCount(0)
    await expect(page.getByRole("button", { name: /^(Edit|Delete) / })).toHaveCount(0)

    await page.goto("/devices")
    await expect(page.locator("table tbody tr").first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByRole("button", { name: "Add Device" })).toHaveCount(0)
    await expect(page.getByRole("button", { name: /^(Edit|Delete) / })).toHaveCount(0)
  })

  test("agents can create and edit tickets but not delete them", async ({ page }) => {
    await page.goto("/tickets")
    await expect(page.locator("table tbody tr").first()).toBeVisible({ timeout: 10000 })

    await expect(page.getByRole("button", { name: "Create Ticket" })).toBeVisible()
    await expect(page.getByRole("button", { name: /^Edit ticket / }).first()).toBeVisible()
    await expect(page.getByRole("button", { name: /^Delete ticket / })).toHaveCount(0)
  })

  test("the API answers 403 when an agent deletes a customer", async ({ request }) => {
    const customers = await (await request.get(`${backendUrl}/api/customers`)).json()
    expect(customers.length).toBeGreaterThan(0)

    const token = await csrfToken(request)
    const response = await request.delete(`${backendUrl}/api/customers/${customers[0].id}`, {
      headers: { "X-XSRF-TOKEN": token },
    })

    expect(response.status()).toBe(403)
    expect((await request.get(`${backendUrl}/api/customers/${customers[0].id}`)).status()).toBe(200)
  })
})

test.describe("Admin permissions", () => {
  test.use({ storageState: ADMIN_STATE })

  test("admins see every management action", async ({ page }) => {
    await page.goto("/customers")
    await expect(page.locator("table tbody tr").first()).toBeVisible({ timeout: 10000 })

    await expect(page.getByRole("button", { name: "Add Customer" })).toBeVisible()
    await expect(page.getByRole("button", { name: /^Delete / }).first()).toBeVisible()

    await page.goto("/tickets")
    await expect(page.locator("table tbody tr").first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByRole("button", { name: /^Delete ticket / }).first()).toBeVisible()
  })
})
