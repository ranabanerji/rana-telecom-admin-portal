// Makes a throwaway Access signing key and token, so `wrangler dev` can run the Worker's Access
// check without a real Cloudflare Access application. Never use these values in a deployment.
//
// Usage: node scripts/dev-access-token.mjs <dev-vars-file>
// Appends CF_ACCESS_* settings to the file and prints a token to send as Cf-Access-Jwt-Assertion.
import { appendFileSync } from "node:fs"
import { generateKeyPairSync, sign } from "node:crypto"
import { fileURLToPath } from "node:url"

export const DEV_TEAM_DOMAIN = "local-dev.invalid"
export const DEV_AUDIENCE = "local-dev"
const KEY_ID = "local-dev"

export function createDevAccess({ now = Date.now(), lifetimeSeconds = 2 * 60 * 60 } = {}) {
  const { publicKey, privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 })
  const jwk = { ...publicKey.export({ format: "jwk" }), kid: KEY_ID, alg: "RS256", use: "sig" }
  const issuedAt = Math.floor(now / 1000)
  const header = base64Url(JSON.stringify({ alg: "RS256", kid: KEY_ID, typ: "JWT" }))
  const payload = base64Url(
    JSON.stringify({
      iss: `https://${DEV_TEAM_DOMAIN}`,
      aud: [DEV_AUDIENCE],
      email: "local-dev@example.com",
      iat: issuedAt,
      nbf: issuedAt,
      exp: issuedAt + lifetimeSeconds,
    })
  )
  const signature = sign("sha256", Buffer.from(`${header}.${payload}`), privateKey)
  return {
    vars: {
      CF_ACCESS_TEAM_DOMAIN: DEV_TEAM_DOMAIN,
      CF_ACCESS_AUD: DEV_AUDIENCE,
      CF_ACCESS_JWKS: JSON.stringify({ keys: [jwk] }),
    },
    token: `${header}.${payload}.${base64Url(signature)}`,
  }
}

/** Dotenv lines; single quotes keep the JSON's double quotes literal. */
export function devVarsLines(vars) {
  return Object.entries(vars)
    .map(([name, value]) => `${name}='${value}'\n`)
    .join("")
}

function base64Url(value) {
  return Buffer.from(value).toString("base64url")
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const file = process.argv[2]
  if (!file) {
    console.error("Usage: node scripts/dev-access-token.mjs <dev-vars-file>")
    process.exit(1)
  }
  const { vars, token } = createDevAccess()
  appendFileSync(file, devVarsLines(vars))
  process.stdout.write(`${token}\n`)
}
