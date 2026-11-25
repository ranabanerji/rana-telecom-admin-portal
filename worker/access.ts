/**
 * Verifies the Cloudflare Access token on requests that reach the Worker.
 * https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/authorization-cookie/validating-json/
 */

export const ACCESS_TOKEN_HEADER = "Cf-Access-Jwt-Assertion"

export interface AccessConfig {
  /** Team domain, for example `myteam.cloudflareaccess.com` (with or without `https://`). */
  teamDomain?: string
  /** AUD tag of the Access application. */
  audience?: string
  /** JWKS JSON to use instead of fetching the team's certs; lets tests sign their own tokens. */
  jwks?: string
}

export type AccessResult = { ok: true; email?: string } | { ok: false; reason: string }

interface Jwk extends JsonWebKey {
  kid?: string
}

interface KeySet {
  source: string
  keys: Map<string, CryptoKey>
  fetchedAt: number
}

const KEY_CACHE_MS = 60 * 60 * 1000
// Bounds refetches when a token names an unknown key, so bad tokens cannot hammer the certs endpoint.
const MIN_REFETCH_MS = 60 * 1000
const CLOCK_SKEW_SECONDS = 60

let cachedKeys: KeySet | undefined

export function resetAccessKeyCache(): void {
  cachedKeys = undefined
}

export async function verifyAccess(
  request: Request,
  config: AccessConfig,
  fetcher: typeof fetch = fetch,
  now: number = Date.now()
): Promise<AccessResult> {
  const issuer = normalizeTeamDomain(config.teamDomain)
  const audience = config.audience?.trim()
  if (!issuer || !audience) {
    return { ok: false, reason: "CF_ACCESS_TEAM_DOMAIN and CF_ACCESS_AUD must be set" }
  }
  const token = request.headers.get(ACCESS_TOKEN_HEADER)
  if (!token) return { ok: false, reason: `missing ${ACCESS_TOKEN_HEADER} header` }

  const parts = token.split(".")
  if (parts.length !== 3) return { ok: false, reason: "malformed token" }
  const [encodedHeader, encodedPayload, encodedSignature] = parts

  let header: { alg?: unknown; kid?: unknown }
  let payload: Record<string, unknown>
  let signature: Uint8Array<ArrayBuffer>
  try {
    header = JSON.parse(decodeText(encodedHeader))
    payload = JSON.parse(decodeText(encodedPayload))
    signature = base64UrlDecode(encodedSignature)
  } catch {
    return { ok: false, reason: "malformed token" }
  }
  if (header.alg !== "RS256") return { ok: false, reason: "unexpected token algorithm" }
  if (typeof header.kid !== "string") return { ok: false, reason: "token has no key id" }

  let key: CryptoKey | undefined
  try {
    key = await signingKey(header.kid, issuer, config.jwks, fetcher, now)
  } catch (error) {
    return { ok: false, reason: `cannot load Access signing keys: ${String(error)}` }
  }
  if (!key) return { ok: false, reason: "token signed with an unknown key" }

  const signed = new TextEncoder().encode(`${encodedHeader}.${encodedPayload}`)
  const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, signature, signed)
  if (!valid) return { ok: false, reason: "bad token signature" }

  if (payload.iss !== issuer) return { ok: false, reason: "wrong token issuer" }
  const audiences = Array.isArray(payload.aud) ? payload.aud : [payload.aud]
  if (!audiences.includes(audience)) return { ok: false, reason: "wrong token audience" }
  const nowSeconds = now / 1000
  if (typeof payload.exp !== "number" || payload.exp <= nowSeconds - CLOCK_SKEW_SECONDS) {
    return { ok: false, reason: "token expired" }
  }
  if (typeof payload.nbf === "number" && payload.nbf > nowSeconds + CLOCK_SKEW_SECONDS) {
    return { ok: false, reason: "token not yet valid" }
  }
  return { ok: true, email: typeof payload.email === "string" ? payload.email : undefined }
}

export function normalizeTeamDomain(teamDomain: string | undefined): string | undefined {
  const host = teamDomain
    ?.trim()
    .replace(/^https?:\/\//, "")
    .replace(/\/+$/, "")
  return host ? `https://${host}` : undefined
}

async function signingKey(
  kid: string,
  issuer: string,
  inlineJwks: string | undefined,
  fetcher: typeof fetch,
  now: number
): Promise<CryptoKey | undefined> {
  const source = inlineJwks?.trim() ? "inline" : `${issuer}/cdn-cgi/access/certs`
  const cached = cachedKeys?.source === source ? cachedKeys : undefined
  const fresh = cached && now - cached.fetchedAt < KEY_CACHE_MS
  const key = fresh ? cached.keys.get(kid) : undefined
  if (key) return key
  if (cached && fresh && now - cached.fetchedAt < MIN_REFETCH_MS) return undefined

  const jwks = source === "inline" ? JSON.parse(inlineJwks!) : await fetchJwks(source, fetcher)
  cachedKeys = { source, keys: await importKeys(jwks), fetchedAt: now }
  return cachedKeys.keys.get(kid)
}

async function fetchJwks(url: string, fetcher: typeof fetch): Promise<unknown> {
  const response = await fetcher(url, { signal: AbortSignal.timeout(5000) })
  if (!response.ok) throw new Error(`${url} returned ${response.status}`)
  return response.json()
}

async function importKeys(jwks: unknown): Promise<Map<string, CryptoKey>> {
  const keys = new Map<string, CryptoKey>()
  const list = (jwks as { keys?: Jwk[] } | null)?.keys ?? []
  for (const jwk of list) {
    if (jwk.kty !== "RSA" || typeof jwk.kid !== "string") continue
    const { kid, ...material } = jwk
    keys.set(
      kid,
      await crypto.subtle.importKey(
        "jwk",
        { ...material, alg: "RS256", ext: true },
        { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
        false,
        ["verify"]
      )
    )
  }
  return keys
}

function decodeText(value: string): string {
  return new TextDecoder().decode(base64UrlDecode(value))
}

function base64UrlDecode(value: string): Uint8Array<ArrayBuffer> {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/")
  const binary = atob(base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "="))
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes
}
