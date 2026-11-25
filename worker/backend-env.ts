// Cloudflare sets this on every incoming request and overwrites any value the client sends, and the
// container is reachable only through this Worker, so the backend can trust it for login throttling.
export const CLIENT_IP_HEADER = "CF-Connecting-IP"
export const HSTS = "max-age=31536000; includeSubDomains"

const DEMO_LOGIN_SECRETS = ["DEMO_ADMIN_PASSWORD", "DEMO_AGENT_PASSWORD"] as const

export interface BackendSettings {
  DEMO_ADMIN_PASSWORD?: string
  DEMO_AGENT_PASSWORD?: string
  /** "false" turns off Secure cookies, for `wrangler dev` over plain HTTP only. */
  SESSION_COOKIE_SECURE?: string
}

/** Environment variables the backend container starts with. */
export function containerEnv(settings: BackendSettings): Record<string, string> {
  const env: Record<string, string> = {
    // Users reach the app over HTTPS at Cloudflare, but the container only sees plain HTTP.
    SESSION_COOKIE_SECURE: settings.SESSION_COOKIE_SECURE === "false" ? "false" : "true",
    LOGIN_CLIENT_IP_HEADER: CLIENT_IP_HEADER,
  }
  for (const name of DEMO_LOGIN_SECRETS) {
    const value = settings[name]
    if (value) env[name] = value
  }
  return env
}

export function withHsts(response: Response): Response {
  const copy = new Response(response.body, response)
  copy.headers.set("Strict-Transport-Security", HSTS)
  return copy
}
