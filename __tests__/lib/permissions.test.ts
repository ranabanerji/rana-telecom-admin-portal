import { describe, it, expect } from "vitest"
import { hasPermission } from "@/lib/permissions"
import { safeNextPath } from "@/lib/redirect"

describe("hasPermission", () => {
  it("grants admins every write permission", () => {
    for (const permission of [
      "customers:write",
      "plans:write",
      "devices:write",
      "tickets:write",
      "tickets:delete",
    ] as const) {
      expect(hasPermission("ADMIN", permission)).toBe(true)
    }
  })

  it("only lets agents create and update tickets", () => {
    expect(hasPermission("AGENT", "tickets:write")).toBe(true)
    expect(hasPermission("AGENT", "tickets:delete")).toBe(false)
    expect(hasPermission("AGENT", "customers:write")).toBe(false)
    expect(hasPermission("AGENT", "plans:write")).toBe(false)
    expect(hasPermission("AGENT", "devices:write")).toBe(false)
  })

  it("grants nothing when nobody is logged in", () => {
    expect(hasPermission(null, "tickets:write")).toBe(false)
    expect(hasPermission(undefined, "customers:write")).toBe(false)
  })
})

describe("safeNextPath", () => {
  it("keeps same-site paths", () => {
    expect(safeNextPath("/customers")).toBe("/customers")
    expect(safeNextPath("/tickets?status=OPEN")).toBe("/tickets?status=OPEN")
  })

  it("falls back to the dashboard for missing, external or login targets", () => {
    expect(safeNextPath(null)).toBe("/dashboard")
    expect(safeNextPath("https://evil.example")).toBe("/dashboard")
    expect(safeNextPath("//evil.example")).toBe("/dashboard")
    expect(safeNextPath("/\\evil.example")).toBe("/dashboard")
    expect(safeNextPath("/login")).toBe("/dashboard")
    expect(safeNextPath("/login?next=/x")).toBe("/dashboard")
  })
})
