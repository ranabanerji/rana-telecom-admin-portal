import { describe, expect, it } from "vitest"
import { securityHeaders } from "@/security-headers.mjs"
import { staticHeadersFile } from "@/scripts/write-static-headers.mjs"

describe("static export headers", () => {
  it("applies the same security headers as next.config.ts to every page, plus HSTS", () => {
    const lines = staticHeadersFile().trim().split("\n")

    expect(lines[0]).toBe("/*")
    for (const { key, value } of securityHeaders()) {
      expect(lines).toContain(`  ${key}: ${value}`)
    }
    expect(lines).toContain("  Strict-Transport-Security: max-age=31536000; includeSubDomains")
  })

  it("uses the production policy, without the dev server's eval and websocket allowances", () => {
    const csp = securityHeaders().find(({ key }) => key === "Content-Security-Policy")?.value

    expect(csp).not.toContain("unsafe-eval")
    expect(csp).not.toContain("ws:")
    expect(staticHeadersFile()).toContain(`  Content-Security-Policy: ${csp}`)
  })
})
