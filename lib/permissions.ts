export type Role = "ADMIN" | "AGENT"

export type Permission =
  "customers:write" | "plans:write" | "devices:write" | "tickets:write" | "tickets:delete"

// Mirrors the backend rules in SecurityConfig: agents read everything and may create or update tickets.
const GRANTS: Record<Role, readonly Permission[]> = {
  ADMIN: ["customers:write", "plans:write", "devices:write", "tickets:write", "tickets:delete"],
  AGENT: ["tickets:write"],
}

export function hasPermission(role: Role | null | undefined, permission: Permission): boolean {
  return role ? GRANTS[role].includes(permission) : false
}

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: "Admin",
  AGENT: "Agent",
}
