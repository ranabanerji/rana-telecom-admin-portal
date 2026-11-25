import { afterEach, describe, expect, it } from "vitest"
import { createDevAccess, devVarsLines } from "@/scripts/dev-access-token.mjs"
import { resetAccessKeyCache, verifyAccess } from "@/worker/access"

function requestWith(token: string): Request {
  return new Request("https://example.com/api/health", {
    headers: { "Cf-Access-Jwt-Assertion": token },
  })
}

const noFetch = (() => Promise.reject(new Error("must use the inline JWKS"))) as typeof fetch

describe("dev Access token", () => {
  afterEach(() => resetAccessKeyCache())

  it("passes the Worker's Access check with the settings it writes", async () => {
    const { vars, token } = createDevAccess()

    const result = await verifyAccess(
      requestWith(token),
      {
        teamDomain: vars.CF_ACCESS_TEAM_DOMAIN,
        audience: vars.CF_ACCESS_AUD,
        jwks: vars.CF_ACCESS_JWKS,
      },
      noFetch
    )

    expect(result).toEqual({ ok: true, email: "local-dev@example.com" })
  })

  it("is rejected by another run's key", async () => {
    const first = createDevAccess()
    const second = createDevAccess()

    const result = await verifyAccess(
      requestWith(second.token),
      {
        teamDomain: first.vars.CF_ACCESS_TEAM_DOMAIN,
        audience: first.vars.CF_ACCESS_AUD,
        jwks: first.vars.CF_ACCESS_JWKS,
      },
      noFetch
    )

    expect(result).toEqual({ ok: false, reason: "bad token signature" })
  })

  it("writes one single-quoted dotenv line per setting", () => {
    const { vars } = createDevAccess()
    const lines = devVarsLines(vars).trimEnd().split("\n")

    expect(lines).toHaveLength(3)
    expect(lines[2]).toBe(`CF_ACCESS_JWKS='${vars.CF_ACCESS_JWKS}'`)
    expect(vars.CF_ACCESS_JWKS).not.toContain("'")
  })
})
