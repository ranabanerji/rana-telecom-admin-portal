import type { Frame, Page, Response } from "@playwright/test"

/**
 * Reloads the page and returns the response to the `GET <path>` request made by the reloaded page.
 *
 * Responses are only accepted once the reload has committed. Before that, a request from the previous load can
 * still be in flight (React runs effects twice in development); a wait that matched it would get a response the
 * reload has already discarded, and reading its body would hang.
 */
export async function reloadAndWaitForResponse(page: Page, path: string): Promise<Response> {
  let reloaded = false
  const onNavigated = (frame: Frame) => {
    if (frame === page.mainFrame()) reloaded = true
  }
  page.on("framenavigated", onNavigated)
  try {
    const [response] = await Promise.all([
      page.waitForResponse(
        (candidate) =>
          reloaded &&
          new URL(candidate.url()).pathname === path &&
          candidate.request().method() === "GET"
      ),
      page.reload(),
    ])
    return response
  } finally {
    page.off("framenavigated", onNavigated)
  }
}
