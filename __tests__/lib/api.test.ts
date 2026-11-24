import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { apiFetch, ensureCsrfToken, UNAUTHORIZED_EVENT } from "@/lib/api"

function response(status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => ({}) } as Response
}

function clearCookie() {
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT"
}

describe("apiFetch", () => {
  beforeEach(() => {
    clearCookie()
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response()))
  })

  afterEach(() => {
    clearCookie()
    vi.unstubAllGlobals()
  })

  it("passes GET requests straight through", async () => {
    await apiFetch("/api/plans")

    expect(fetch).toHaveBeenCalledTimes(1)
    expect(fetch).toHaveBeenCalledWith("/api/plans")
  })

  it("echoes the XSRF-TOKEN cookie in a header on unsafe requests", async () => {
    document.cookie = "XSRF-TOKEN=abc123"

    await apiFetch("/api/plans/1", { method: "DELETE" })

    expect(fetch).toHaveBeenCalledTimes(1)
    const [, init] = vi.mocked(fetch).mock.calls[0]
    expect(new Headers(init?.headers).get("X-XSRF-TOKEN")).toBe("abc123")
  })

  it("keeps caller headers alongside the CSRF header", async () => {
    document.cookie = "XSRF-TOKEN=abc123"

    await apiFetch("/api/plans", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "{}",
    })

    const headers = new Headers(vi.mocked(fetch).mock.calls[0][1]?.headers)
    expect(headers.get("Content-Type")).toBe("application/json")
    expect(headers.get("X-XSRF-TOKEN")).toBe("abc123")
  })

  it("asks the backend for a token when the cookie is missing", async () => {
    vi.mocked(fetch).mockImplementationOnce(async () => {
      document.cookie = "XSRF-TOKEN=fresh"
      return response(204)
    })

    await apiFetch("/api/tickets", { method: "POST", body: "{}" })

    expect(vi.mocked(fetch).mock.calls[0][0]).toBe("/api/auth/csrf")
    const [url, init] = vi.mocked(fetch).mock.calls[1]
    expect(url).toBe("/api/tickets")
    expect(new Headers(init?.headers).get("X-XSRF-TOKEN")).toBe("fresh")
  })

  it("announces a 401 so the app can return to the login page", async () => {
    const listener = vi.fn()
    window.addEventListener(UNAUTHORIZED_EVENT, listener)
    vi.mocked(fetch).mockResolvedValueOnce(response(401))

    await apiFetch("/api/customers")

    window.removeEventListener(UNAUTHORIZED_EVENT, listener)
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it("does not treat a 403 as a logout", async () => {
    const listener = vi.fn()
    window.addEventListener(UNAUTHORIZED_EVENT, listener)
    document.cookie = "XSRF-TOKEN=abc123"
    vi.mocked(fetch).mockResolvedValueOnce(response(403))

    const res = await apiFetch("/api/customers/1", { method: "DELETE" })

    window.removeEventListener(UNAUTHORIZED_EVENT, listener)
    expect(res.status).toBe(403)
    expect(listener).not.toHaveBeenCalled()
  })
})

describe("ensureCsrfToken", () => {
  afterEach(() => {
    clearCookie()
    vi.unstubAllGlobals()
  })

  it("reuses an existing cookie without a network call", async () => {
    vi.stubGlobal("fetch", vi.fn())
    document.cookie = "XSRF-TOKEN=existing"

    await expect(ensureCsrfToken()).resolves.toBe("existing")
    expect(fetch).not.toHaveBeenCalled()
  })
})
