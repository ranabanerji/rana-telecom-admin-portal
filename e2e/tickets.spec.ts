import { test, expect } from "@playwright/test"
import { backendUrl, csrfToken } from "./auth-helpers"

test("a rejected ticket delete keeps the dialog open and shows the error", async ({
  page,
  request,
}) => {
  const customers = await (await request.get(`${backendUrl}/api/customers`)).json()
  const subject = `Delete error regression ${Date.now()}`
  const headers = { "X-XSRF-TOKEN": await csrfToken(request) }
  const created = await request.post(`${backendUrl}/api/tickets`, {
    headers,
    data: { customerId: customers[0].id, subject, description: "", priority: "LOW" },
  })
  expect(created.ok()).toBeTruthy()
  const ticketId: number = (await created.json()).id

  await page.goto("/tickets")
  const row = page.locator("tbody tr").filter({ hasText: subject })
  await expect(row).toBeVisible()

  // Another session removes the ticket while this page still lists it, so the API answers 404.
  expect(
    (await request.delete(`${backendUrl}/api/tickets/${ticketId}`, { headers })).status()
  ).toBe(204)

  await page.getByRole("button", { name: `Delete ticket ${ticketId}`, exact: true }).click()
  const dialog = page.getByRole("alertdialog")
  const rejected = page.waitForResponse(
    (response) =>
      response.url().endsWith(`/api/tickets/${ticketId}`) &&
      response.request().method() === "DELETE"
  )
  await dialog.getByRole("button", { name: "Delete", exact: true }).click()
  expect((await rejected).status()).toBe(404)

  // The API answers 404 with a problem-details body, and the dialog shows its field error.
  await expect(dialog.getByRole("alert")).toHaveText(`id: Ticket ${ticketId} was not found`)
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole("button", { name: "Delete", exact: true })).toBeEnabled()
  await dialog.getByRole("button", { name: "Cancel" }).click()
  await expect(dialog).not.toBeVisible()
  await expect(row).toBeVisible()
})
