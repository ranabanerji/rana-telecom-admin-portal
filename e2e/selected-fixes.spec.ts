import { test, expect } from "@playwright/test"
import { csrfToken } from "./auth-helpers"

const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080"

test("used plan deletion remains visible as a conflict", async ({ page }) => {
  await page.goto("/plans")
  const row = page.locator("tbody tr").first()
  await expect(row).toBeVisible()
  await row.getByRole("button").last().click()
  const dialog = page.getByRole("alertdialog")
  await dialog.getByRole("button", { name: "Delete", exact: true }).click()
  await expect(dialog.getByRole("alert")).toContainText("Deactivate")
  await expect(dialog).toBeVisible()
  await dialog.getByRole("button", { name: "Cancel" }).click()
  await expect(row).toBeVisible()
})

test("dependent customer deletion keeps the dialog and error visible", async ({
  page,
  request,
}) => {
  const tickets = await (await request.get(`${backendUrl}/api/tickets?size=1`)).json()
  const customerId = tickets.content[0].customer_id
  await page.goto("/customers")
  const row = page
    .locator("tbody tr")
    .filter({ has: page.locator("td:first-child", { hasText: new RegExp(`^${customerId}$`) }) })
  await expect(row).toBeVisible()
  await row.getByRole("button").last().click()
  const dialog = page.getByRole("alertdialog")
  await dialog.getByRole("button", { name: "Delete", exact: true }).click()
  await expect(dialog.getByRole("alert")).toContainText("dependents")
  await expect(dialog).toBeVisible()
})

test("one failing dashboard endpoint only affects its card", async ({ page }) => {
  await page.route("**/api/dashboard/devices-by-status", (route) =>
    route.fulfill({
      status: 500,
      contentType: "application/problem+json",
      body: JSON.stringify({ detail: "Test failure" }),
    })
  )
  await page.goto("/dashboard")
  await expect(
    page.getByRole("alert").filter({ hasText: "Unable to load this chart" })
  ).toBeVisible()
  await expect(page.getByText("Monthly Revenue")).toBeVisible()
  await expect(page.getByText("Customers by Plan")).toBeVisible()
})

test("ticket create and clear description round-trip through the UI", async ({ page, request }) => {
  const customers = await (await request.get(`${backendUrl}/api/customers`)).json()
  const subject = `Optional description regression ${Date.now()}`
  let ticketId: number | undefined
  try {
    await page.goto("/tickets")
    await page.getByRole("button", { name: "Create Ticket", exact: true }).click()
    const dialog = page.getByRole("dialog")
    await dialog.getByLabel("Customer ID", { exact: true }).fill(String(customers[0].id))
    await dialog.getByLabel("Subject", { exact: true }).fill(subject)
    const created = page.waitForResponse(
      (response) =>
        response.url().endsWith("/api/tickets") && response.request().method() === "POST"
    )
    await dialog.getByRole("button", { name: "Create Ticket", exact: true }).click()
    const response = await created
    expect(response.ok()).toBeTruthy()
    const ticket = await response.json()
    ticketId = ticket.id
    expect(ticket.description).toBe("")
    const row = page.locator("tbody tr").filter({ hasText: subject })
    await expect(row).toBeVisible()
    await row.getByRole("button").first().click()
    await page.getByLabel("Description", { exact: true }).fill("Temporary details")
    await page.getByRole("button", { name: "Save Changes", exact: true }).click()
    await expect(page.getByRole("dialog")).not.toBeVisible()
    await row.getByRole("button").first().click()
    await page.getByLabel("Description", { exact: true }).fill("")
    const updated = page.waitForResponse(
      (response) =>
        response.url().endsWith(`/api/tickets/${ticketId}`) && response.request().method() === "PUT"
    )
    await page.getByRole("button", { name: "Save Changes", exact: true }).click()
    expect((await (await updated).json()).description).toBe("")
    await expect(page.getByRole("dialog")).not.toBeVisible()
  } finally {
    if (ticketId) {
      const response = await request.delete(`${backendUrl}/api/tickets/${ticketId}`, {
        headers: { "X-XSRF-TOKEN": await csrfToken(request) },
      })
      expect(response.status()).toBe(204)
    }
  }
})
