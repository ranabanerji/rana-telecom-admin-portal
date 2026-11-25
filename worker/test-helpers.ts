/** Test-only Access token issuer: signs RS256 JWTs the way Cloudflare Access does. */

export interface TestIssuer {
  jwks: string
  sign(claims: Record<string, unknown>, options?: { kid?: string; alg?: string }): Promise<string>
}

export const TEAM = "test-team.cloudflareaccess.com"
export const ISSUER = `https://${TEAM}`
export const AUD = "test-aud-tag"

export async function createIssuer(kid = "test-key"): Promise<TestIssuer> {
  const pair = (await crypto.subtle.generateKey(
    {
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256",
    },
    true,
    ["sign", "verify"]
  )) as CryptoKeyPair
  const publicJwk = (await crypto.subtle.exportKey("jwk", pair.publicKey)) as JsonWebKey
  const jwks = JSON.stringify({ keys: [{ ...publicJwk, kid, alg: "RS256", use: "sig" }] })

  return {
    jwks,
    async sign(claims, options = {}) {
      const header = base64Url(
        JSON.stringify({ alg: options.alg ?? "RS256", kid: options.kid ?? kid })
      )
      const payload = base64Url(JSON.stringify(claims))
      const signature = await crypto.subtle.sign(
        "RSASSA-PKCS1-v1_5",
        pair.privateKey,
        new TextEncoder().encode(`${header}.${payload}`)
      )
      return `${header}.${payload}.${base64Url(new Uint8Array(signature))}`
    },
  }
}

export function validClaims(now = Date.now()): Record<string, unknown> {
  const seconds = Math.floor(now / 1000)
  return {
    iss: ISSUER,
    aud: [AUD],
    email: "user@example.com",
    iat: seconds,
    nbf: seconds,
    exp: seconds + 600,
  }
}

function base64Url(value: string | Uint8Array): string {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : value
  let binary = ""
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")
}
