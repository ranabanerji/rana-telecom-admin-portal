import type { Page } from "@playwright/test"

/**
 * Reloads the page and returns the HTTP status of the page's next `GET <path>` request.
 *
 * Only the status is returned on purpose. The matching response can belong to a request the previous load still
 * had in flight (React runs effects twice in development, and hydration is late on a cold dev build). The reload
 * discards that response's body, and reading it then fails or hangs, so tests fetch data through `page.request`.
 */
export async function reloadAndWaitForStatus(page: Page, path: string): Promise<number> {
  const [response] = await Promise.all([
    page.waitForResponse(
      (candidate) =>
        new URL(candidate.url()).pathname === path && candidate.request().method() === "GET"
    ),
    page.reload(),
  ])
  return response.status()
}
