import { expect, test } from "@playwright/test"

test.describe("security headers", () => {
  test("pages send a CSP that the app runs under without violations", async ({ page }) => {
    const violations: string[] = []
    page.on("console", (message) => {
      if (/content security policy/i.test(message.text())) violations.push(message.text())
    })
    page.on("pageerror", (error) => violations.push(error.message))

    const response = await page.goto("/dashboard")
    const headers = response?.headers() ?? {}
    expect(headers["content-security-policy"]).toContain("frame-ancestors 'none'")
    expect(headers["content-security-policy"]).toContain("object-src 'none'")
    expect(headers["x-frame-options"]).toBe("DENY")
    expect(headers["x-content-type-options"]).toBe("nosniff")
    expect(headers["x-powered-by"]).toBeUndefined()

    await expect(page.locator("[data-slot='card']").first()).toBeVisible()
    await expect(page.locator(".recharts-wrapper").first()).toBeVisible()
    await page.goto("/customers")
    await expect(page.locator("tbody tr").first()).toBeVisible()
    await page.goto("/tickets")
    await expect(page.locator("tbody tr").first()).toBeVisible()

    expect(violations).toEqual([])
  })
})
