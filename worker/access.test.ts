// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from "vitest"
import {
  ACCESS_TOKEN_HEADER,
  normalizeTeamDomain,
  resetAccessKeyCache,
  verifyAccess,
} from "./access"
import { AUD, createIssuer, ISSUER, TEAM, validClaims, type TestIssuer } from "./test-helpers"

let issuer: TestIssuer

function request(token?: string): Request {
  return new Request("https://telecom.example.com/api/customers", {
    headers: token ? { [ACCESS_TOKEN_HEADER]: token } : {},
  })
}

const noFetch = vi.fn<typeof fetch>(() => Promise.reject(new Error("unexpected fetch")))

describe("verifyAccess", () => {
  beforeEach(async () => {
    resetAccessKeyCache()
    issuer = await createIssuer()
  })

  const config = () => ({ teamDomain: TEAM, audience: AUD, jwks: issuer.jwks })

  it("accepts a token signed by the team for this application", async () => {
    const result = await verifyAccess(request(await issuer.sign(validClaims())), config(), noFetch)

    expect(result).toEqual({ ok: true, email: "user@example.com" })
  })

  it.each([
    ["no team domain", { teamDomain: "", audience: AUD }],
    ["no audience", { teamDomain: TEAM, audience: undefined }],
  ])("fails closed when Access is not configured (%s)", async (_, partial) => {
    const result = await verifyAccess(
      request(await issuer.sign(validClaims())),
      { ...partial, jwks: issuer.jwks },
      noFetch
    )

    expect(result).toEqual({
      ok: false,
      reason: "CF_ACCESS_TEAM_DOMAIN and CF_ACCESS_AUD must be set",
    })
  })

  it("refuses a request without a token", async () => {
    expect(await verifyAccess(request(), config(), noFetch)).toMatchObject({ ok: false })
  })

  it.each([
    ["wrong audience", { aud: ["another-app"] }, "wrong token audience"],
    ["wrong issuer", { iss: "https://evil.cloudflareaccess.com" }, "wrong token issuer"],
    ["expired", { exp: Math.floor(Date.now() / 1000) - 3600 }, "token expired"],
    ["missing expiry", { exp: undefined }, "token expired"],
    ["not yet valid", { nbf: Math.floor(Date.now() / 1000) + 3600 }, "token not yet valid"],
  ])("refuses a token with %s", async (_, override, reason) => {
    const token = await issuer.sign({ ...validClaims(), ...override })

    expect(await verifyAccess(request(token), config(), noFetch)).toEqual({ ok: false, reason })
  })

  it("refuses a token whose payload was changed after signing", async () => {
    const token = await issuer.sign(validClaims())
    const [header, , signature] = token.split(".")
    const forgedPayload = btoa(JSON.stringify({ ...validClaims(), email: "admin@example.com" }))
      .replace(/=+$/, "")
      .replace(/\+/g, "-")
      .replace(/\//g, "_")

    const result = await verifyAccess(
      request(`${header}.${forgedPayload}.${signature}`),
      config(),
      noFetch
    )

    expect(result).toEqual({ ok: false, reason: "bad token signature" })
  })

  it("refuses a token signed by someone else", async () => {
    const stranger = await createIssuer()

    const result = await verifyAccess(
      request(await stranger.sign(validClaims())),
      config(),
      noFetch
    )

    expect(result).toEqual({ ok: false, reason: "bad token signature" })
  })

  it("refuses unsigned and non-RS256 tokens", async () => {
    const none = await issuer.sign(validClaims(), { alg: "none" })
    const hs256 = await issuer.sign(validClaims(), { alg: "HS256" })

    for (const token of [none, hs256, "not-a-jwt", "a.b.c"]) {
      expect(await verifyAccess(request(token), config(), noFetch)).toMatchObject({ ok: false })
    }
  })

  it("loads the team's certs once and refetches only for a new key", async () => {
    const certs = vi.fn<typeof fetch>(async () => new Response(issuer.jwks))
    const remote = { teamDomain: `https://${TEAM}/`, audience: AUD }
    const start = Date.now()

    expect(
      await verifyAccess(request(await issuer.sign(validClaims())), remote, certs, start)
    ).toMatchObject({
      ok: true,
    })
    expect(
      await verifyAccess(request(await issuer.sign(validClaims())), remote, certs, start + 1000)
    ).toMatchObject({
      ok: true,
    })
    expect(certs).toHaveBeenCalledTimes(1)
    expect(String(certs.mock.calls[0][0])).toBe(`${ISSUER}/cdn-cgi/access/certs`)

    const unknownKid = await issuer.sign(validClaims(), { kid: "rotated" })
    expect(await verifyAccess(request(unknownKid), remote, certs, start + 2000)).toEqual({
      ok: false,
      reason: "token signed with an unknown key",
    })
    expect(certs).toHaveBeenCalledTimes(1)

    await verifyAccess(request(unknownKid), remote, certs, start + 120_000)
    expect(certs).toHaveBeenCalledTimes(2)
  })

  it("fails closed when the certs cannot be loaded", async () => {
    const down = vi.fn<typeof fetch>(async () => new Response("unavailable", { status: 503 }))

    const result = await verifyAccess(
      request(await issuer.sign(validClaims())),
      { teamDomain: TEAM, audience: AUD },
      down
    )

    expect(result).toMatchObject({ ok: false })
  })
})

describe("normalizeTeamDomain", () => {
  it.each([
    ["team.cloudflareaccess.com", "https://team.cloudflareaccess.com"],
    ["https://team.cloudflareaccess.com/", "https://team.cloudflareaccess.com"],
    ["  ", undefined],
    [undefined, undefined],
  ])("%s -> %s", (input, expected) => {
    expect(normalizeTeamDomain(input)).toBe(expected)
  })
})
