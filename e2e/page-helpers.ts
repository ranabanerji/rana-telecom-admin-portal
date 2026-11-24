import type { Frame, Page, Request, Response } from "@playwright/test"

/**
 * Reloads the page and returns the response to the `GET <path>` request made by the reloaded page.
 *
 * Only requests that start after the reload has committed are accepted. A request from the previous load can still
 * be in flight (React runs effects twice in development, and hydration is slow on a cold dev build); its response
 * can even arrive after the commit, and the reload has already discarded its body, so reading it fails.
 */
export async function reloadAndWaitForResponse(page: Page, path: string): Promise<Response> {
  let reloaded = false
  const fromReload = new Set<Request>()
  const onNavigated = (frame: Frame) => {
    if (frame === page.mainFrame()) reloaded = true
  }
  const onRequest = (request: Request) => {
    if (reloaded) fromReload.add(request)
  }
  page.on("framenavigated", onNavigated)
  page.on("request", onRequest)
  try {
    const [response] = await Promise.all([
      page.waitForResponse(
        (candidate) =>
          fromReload.has(candidate.request()) &&
          new URL(candidate.url()).pathname === path &&
          candidate.request().method() === "GET"
      ),
      page.reload(),
    ])
    return response
  } finally {
    page.off("framenavigated", onNavigated)
    page.off("request", onRequest)
  }
}
