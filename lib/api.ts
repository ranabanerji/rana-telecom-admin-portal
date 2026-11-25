export const CSRF_COOKIE = "XSRF-TOKEN"
export const CSRF_HEADER = "X-XSRF-TOKEN"
export const UNAUTHORIZED_EVENT = "auth:unauthorized"

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"])

function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null
  const prefix = `${name}=`
  const match = document.cookie.split("; ").find((part) => part.startsWith(prefix))
  return match ? decodeURIComponent(match.slice(prefix.length)) : null
}

/**
 * Returns the CSRF token from the XSRF-TOKEN cookie, asking the backend to issue one if it is missing
 * (for example on first visit or right after login, which rotates the token).
 */
export async function ensureCsrfToken(): Promise<string | null> {
  const existing = readCookie(CSRF_COOKIE)
  if (existing) return existing
  await fetch("/api/auth/csrf")
  return readCookie(CSRF_COOKIE)
}

/**
 * fetch() for the backend API. Adds the CSRF header to unsafe requests and announces a 401 so the auth
 * provider can send the user back to the login page.
 */
export async function apiFetch(input: string, init?: RequestInit): Promise<Response> {
  const method = (init?.method ?? "GET").toUpperCase()
  let res: Response
  if (SAFE_METHODS.has(method)) {
    res = init ? await fetch(input, init) : await fetch(input)
  } else {
    const headers = new Headers(init?.headers)
    const token = await ensureCsrfToken()
    if (token) headers.set(CSRF_HEADER, token)
    res = await fetch(input, { ...init, headers })
  }
  if (res.status === 401 && typeof window !== "undefined") {
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
  }
  return res
}
