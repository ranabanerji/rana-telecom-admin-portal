// Writes out/_headers for the Cloudflare static export, which cannot use next.config.ts headers().
// Workers static assets apply the file to every page and never serve it.
import { writeFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { securityHeaders } from "../security-headers.mjs"

const HSTS = "max-age=31536000; includeSubDomains"

export function staticHeadersFile() {
  const headers = [...securityHeaders(), { key: "Strict-Transport-Security", value: HSTS }]
  return ["/*", ...headers.map(({ key, value }) => `  ${key}: ${value}`), ""].join("\n")
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const target = new URL("../out/_headers", import.meta.url)
  writeFileSync(target, staticHeadersFile())
  console.log(`Wrote ${fileURLToPath(target)}`)
}
