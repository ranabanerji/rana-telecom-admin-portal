import type { APIRequestContext } from "@playwright/test"

export const backendUrl = process.env.BACKEND_URL ?? "http://127.0.0.1:8080"

export const ADMIN_STATE = "playwright/.auth/admin.json"
export const AGENT_STATE = "playwright/.auth/agent.json"

export interface DemoLogin {
  username: string
  password: string
  name: string
  email: string
}

function required(variable: string): string {
  const value = process.env[variable]
  if (!value) {
    throw new Error(
      `${variable} is not set. Export the demo login variables before running E2E tests (see "Demo logins" in the README).`
    )
  }
  return value
}

// Defaults mirror backend/src/main/resources/application.yaml; passwords have no default.
export function adminLogin(): DemoLogin {
  return {
    username: process.env.DEMO_ADMIN_USERNAME ?? "admin",
    password: required("DEMO_ADMIN_PASSWORD"),
    name: process.env.DEMO_ADMIN_NAME ?? "Demo Admin",
    email: process.env.DEMO_ADMIN_EMAIL ?? "admin@example.com",
  }
}

export function agentLogin(): DemoLogin {
  return {
    username: process.env.DEMO_AGENT_USERNAME ?? "agent",
    password: required("DEMO_AGENT_PASSWORD"),
    name: process.env.DEMO_AGENT_NAME ?? "Demo Agent",
    email: process.env.DEMO_AGENT_EMAIL ?? "agent@example.com",
  }
}

/** Fetches a CSRF token the way the browser client does and returns it for the X-XSRF-TOKEN header. */
export async function csrfToken(request: APIRequestContext): Promise<string> {
  await request.get(`${backendUrl}/api/auth/csrf`)
  const state = await request.storageState()
  const cookie = state.cookies.find((c) => c.name === "XSRF-TOKEN")
  if (!cookie) throw new Error("Backend did not issue an XSRF-TOKEN cookie")
  return cookie.value
}
