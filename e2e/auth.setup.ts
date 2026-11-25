import { test as setup, expect, type Page } from "@playwright/test"
import { ADMIN_STATE, AGENT_STATE, adminLogin, agentLogin, type DemoLogin } from "./auth-helpers"

async function logInThroughTheUi(page: Page, login: DemoLogin) {
  await page.goto("/login")
  await page.getByLabel("Username").fill(login.username)
  await page.getByLabel("Password").fill(login.password)
  await page.getByRole("button", { name: "Log in" }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByTestId("nav-user-name")).toHaveText(login.name)
}

setup("log in as the demo admin", async ({ page }) => {
  await logInThroughTheUi(page, adminLogin())
  await page.context().storageState({ path: ADMIN_STATE })
})

setup("log in as the demo agent", async ({ page }) => {
  await logInThroughTheUi(page, agentLogin())
  await page.context().storageState({ path: AGENT_STATE })
})
