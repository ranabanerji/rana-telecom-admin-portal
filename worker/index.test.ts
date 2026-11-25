// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from "vitest"
import worker from "./index"
import { resetAccessKeyCache } from "./access"
import { containerEnv, HSTS } from "./backend-env"
import { AUD, createIssuer, TEAM, validClaims, type TestIssuer } from "./test-helpers"

let issuer: TestIssuer
const assets = vi.fn(async () => new Response("<html></html>"))
const backend = vi.fn(async () => Response.json({ status: "ok" }))

function env(overrides: Record<string, unknown> = {}) {
  return {
    ASSETS: { fetch: assets },
    BACKEND: { getByName: () => ({ fetch: backend }) },
    CF_ACCESS_TEAM_DOMAIN: TEAM,
    CF_ACCESS_AUD: AUD,
    CF_ACCESS_JWKS: issuer.jwks,
    ...overrides,
  } as never
}

async function call(path: string, headers: Record<string, string> = {}, overrides = {}) {
  const handler = worker.fetch as (request: Request, env: never) => Promise<Response>
  return handler(new Request(`https://telecom.example.com${path}`, { headers }), env(overrides))
}

describe("worker fetch", () => {
  beforeEach(async () => {
    resetAccessKeyCache()
    assets.mockClear()
    backend.mockClear()
    issuer = await createIssuer()
  })

  it("serves pages from static assets", async () => {
    const response = await call("/dashboard")

    expect(response.status).toBe(200)
    expect(assets).toHaveBeenCalledOnce()
    expect(backend).not.toHaveBeenCalled()
  })

  it("refuses API requests without an Access token and never reaches the backend", async () => {
    const response = await call("/api/customers")

    expect(response.status).toBe(403)
    expect(await response.json()).toEqual({ error: "Forbidden" })
    expect(response.headers.get("Strict-Transport-Security")).toBe(HSTS)
    expect(backend).not.toHaveBeenCalled()
  })

  it("refuses API requests while Access is not configured, even with a valid token", async () => {
    const token = await issuer.sign(validClaims())

    const response = await call(
      "/api/health",
      { "Cf-Access-Jwt-Assertion": token },
      { CF_ACCESS_AUD: undefined }
    )

    expect(response.status).toBe(403)
    expect(backend).not.toHaveBeenCalled()
  })

  it("forwards API requests with a valid Access token and adds HSTS", async () => {
    const token = await issuer.sign(validClaims())

    const response = await call("/api/health", { "Cf-Access-Jwt-Assertion": token })

    expect(response.status).toBe(200)
    expect(await response.json()).toEqual({ status: "ok" })
    expect(response.headers.get("Strict-Transport-Security")).toBe(HSTS)
    expect(backend).toHaveBeenCalledOnce()
  })
})

describe("containerEnv", () => {
  it("turns on Secure cookies and the trusted client IP header by default", () => {
    expect(
      containerEnv({ DEMO_ADMIN_PASSWORD: "admin-password-1", DEMO_AGENT_PASSWORD: "" })
    ).toEqual({
      SESSION_COOKIE_SECURE: "true",
      LOGIN_CLIENT_IP_HEADER: "CF-Connecting-IP",
      DEMO_ADMIN_PASSWORD: "admin-password-1",
    })
  })

  it("allows plain-HTTP cookies only when explicitly turned off", () => {
    expect(containerEnv({ SESSION_COOKIE_SECURE: "false" }).SESSION_COOKIE_SECURE).toBe("false")
    expect(containerEnv({ SESSION_COOKIE_SECURE: "no" }).SESSION_COOKIE_SECURE).toBe("true")
  })
})
