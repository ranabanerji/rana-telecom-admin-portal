"use client"

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react"
import { usePathname, useRouter } from "next/navigation"
import { apiFetch, ensureCsrfToken, UNAUTHORIZED_EVENT } from "@/lib/api"
import { hasPermission, type Permission, type Role } from "@/lib/permissions"

export interface AuthUser {
  username: string
  name: string
  email: string
  role: Role
}

type AuthStatus = "loading" | "authenticated" | "unauthenticated"

interface AuthContextValue {
  user: AuthUser | null
  status: AuthStatus
  login: (username: string, password: string) => Promise<AuthUser>
  logout: () => Promise<void>
  can: (permission: Permission) => boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

const PUBLIC_PATHS = ["/login"]

function isPublicPath(pathname: string) {
  return PUBLIC_PATHS.some((path) => pathname === path || pathname.startsWith(`${path}/`))
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const pathname = usePathname() ?? "/"
  const [user, setUser] = useState<AuthUser | null>(null)
  const [status, setStatus] = useState<AuthStatus>("loading")
  // After an explicit log out there is no page to come back to, so skip the ?next= parameter.
  const loggedOut = useRef(false)

  useEffect(() => {
    let cancelled = false
    const loadCurrentUser = async () => {
      try {
        const res = await fetch("/api/auth/me")
        if (cancelled) return
        if (res.ok) {
          setUser(await res.json())
          setStatus("authenticated")
        } else {
          setStatus("unauthenticated")
        }
      } catch {
        if (!cancelled) setStatus("unauthenticated")
      }
    }
    loadCurrentUser()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    const handleUnauthorized = () => {
      setUser(null)
      setStatus("unauthenticated")
    }
    window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
  }, [])

  const isPublic = isPublicPath(pathname)

  useEffect(() => {
    if (status === "unauthenticated" && !isPublic) {
      router.replace(loggedOut.current ? "/login" : `/login?next=${encodeURIComponent(pathname)}`)
    }
  }, [status, isPublic, pathname, router])

  const login = useCallback(async (username: string, password: string) => {
    const token = await ensureCsrfToken()
    const res = await fetch("/api/auth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        ...(token ? { "X-XSRF-TOKEN": token } : {}),
      },
      body: new URLSearchParams({ username, password }).toString(),
    })
    if (!res.ok) {
      throw new Error(
        res.status === 401 ? "Invalid username or password" : "Login failed, please try again"
      )
    }
    const profile: AuthUser = await res.json()
    loggedOut.current = false
    setUser(profile)
    setStatus("authenticated")
    return profile
  }, [])

  const logout = useCallback(async () => {
    try {
      await apiFetch("/api/auth/logout", { method: "POST" })
    } finally {
      loggedOut.current = true
      setUser(null)
      setStatus("unauthenticated")
    }
  }, [])

  const can = useCallback((permission: Permission) => hasPermission(user?.role, permission), [user])

  const value = useMemo(
    () => ({ user, status, login, logout, can }),
    [user, status, login, logout, can]
  )

  let content: React.ReactNode = children
  if (!isPublic && status !== "authenticated") {
    // Keep protected pages unmounted until the session is known so they never fire unauthenticated requests.
    content = (
      <div className="text-muted-foreground flex h-svh items-center justify-center">Loading...</div>
    )
  }

  return <AuthContext.Provider value={value}>{content}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error("useAuth must be used inside <AuthProvider>")
  return context
}
