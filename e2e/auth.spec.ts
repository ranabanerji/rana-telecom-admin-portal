import { test, expect } from "@playwright/test"
import { adminLogin, backendUrl } from "./auth-helpers"

test.describe("Authentication", () => {
  test.use({ storageState: { cookies: [], origins: [] } })

  test("protected pages redirect to the login page", async ({ page }) => {
    await page.goto("/customers")

    await expect(page).toHaveURL(/\/login\?next=%2Fcustomers$/)
    await expect(page.getByRole("button", { name: "Log in" })).toBeVisible()
  })

  test("a wrong password shows an error and stays on the login page", async ({ page }) => {
    await page.goto("/login")
    await page.getByLabel("Username").fill(adminLogin().username)
    await page.getByLabel("Password").fill("definitely-not-the-password")
    await page.getByRole("button", { name: "Log in" }).click()

    // Next.js renders its own (empty) role="alert" route announcer, so match on the message.
    await expect(
      page.getByRole("alert").filter({ hasText: "Invalid username or password" })
    ).toBeVisible()
    await expect(page).toHaveURL(/\/login$/)
  })

  test("logging in returns to the requested page and shows the real user", async ({ page }) => {
    const admin = adminLogin()
    await page.goto("/customers")
    await expect(page).toHaveURL(/\/login/)

    await page.getByLabel("Username").fill(admin.username)
    await page.getByLabel("Password").fill(admin.password)
    await page.getByRole("button", { name: "Log in" }).click()

    await expect(page).toHaveURL(/\/customers$/)
    await expect(page.getByTestId("nav-user-name")).toHaveText(admin.name)
    await expect(page.getByTestId("nav-user-email")).toHaveText(admin.email)
  })

  test("log out ends the session", async ({ page }) => {
    const admin = adminLogin()
    await page.goto("/login")
    await page.getByLabel("Username").fill(admin.username)
    await page.getByLabel("Password").fill(admin.password)
    await page.getByRole("button", { name: "Log in" }).click()
    await expect(page).toHaveURL(/\/dashboard$/)

    await page.getByRole("button", { name: "User menu" }).click()
    await page.getByRole("menuitem", { name: "Log out" }).click()

    await expect(page).toHaveURL(/\/login$/)
    const me = await page.request.get("/api/auth/me")
    expect(me.status()).toBe(401)

    await page.goto("/dashboard")
    await expect(page).toHaveURL(/\/login\?next=%2Fdashboard$/)
  })

  test("the API rejects requests without a login", async ({ request }) => {
    expect((await request.get(`${backendUrl}/api/health`)).status()).toBe(200)
    expect((await request.get(`${backendUrl}/api/customers`)).status()).toBe(401)
    expect((await request.delete(`${backendUrl}/api/customers/1`)).status()).toBe(401)
  })
})
