import { render, screen, waitFor, act } from "@testing-library/react"
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { AuthProvider, useAuth } from "@/components/auth-provider"
import { initials } from "@/components/nav-user"
import { UNAUTHORIZED_EVENT } from "@/lib/api"

const replace = vi.fn()
let pathname = "/customers"

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  usePathname: () => pathname,
}))

const admin = { username: "admin", name: "Demo Admin", email: "admin@example.com", role: "ADMIN" }

function jsonResponse(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body } as Response
}

function Probe() {
  const { user, can, logout } = useAuth()
  return (
    <div>
      <button onClick={() => void logout()}>log out</button>
      <span>user:{user?.name ?? "none"}</span>
      <span>delete-customers:{String(can("customers:write"))}</span>
    </div>
  )
}

describe("AuthProvider", () => {
  beforeEach(() => {
    replace.mockReset()
    pathname = "/customers"
    document.cookie = "XSRF-TOKEN=test-token"
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("redirects to the login page when there is no session", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(null, 401)))

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    )

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login?next=%2Fcustomers"))
    expect(screen.queryByText(/user:/)).not.toBeInTheDocument()
  })

  it("renders protected content with the signed-in user's permissions", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(admin)))

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    )

    expect(await screen.findByText("user:Demo Admin")).toBeInTheDocument()
    expect(screen.getByText("delete-customers:true")).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })

  it("renders the login page without a session", async () => {
    pathname = "/login"
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(null, 401)))

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    )

    expect(await screen.findByText("user:none")).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })

  it("returns to the login page when the session expires", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(admin)))
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    )
    await screen.findByText("user:Demo Admin")

    act(() => {
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    })

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login?next=%2Fcustomers"))
  })
})

describe("AuthProvider logout", () => {
  beforeEach(() => {
    replace.mockReset()
    pathname = "/dashboard"
    document.cookie = "XSRF-TOKEN=test-token"
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("posts to the logout endpoint and goes to a plain login page", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(admin))
      .mockResolvedValueOnce(jsonResponse(null, 204))
    vi.stubGlobal("fetch", fetchMock)
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    )
    await screen.findByText("user:Demo Admin")

    await act(async () => {
      screen.getByRole("button", { name: "log out" }).click()
    })

    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe("/api/auth/logout")
    expect(init.method).toBe("POST")
    expect(new Headers(init.headers).get("X-XSRF-TOKEN")).toBe("test-token")
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"))
    expect(replace).not.toHaveBeenCalledWith(expect.stringContaining("next="))
  })
})

describe("initials", () => {
  it("uses the first and last name", () => {
    expect(initials("Demo Admin")).toBe("DA")
    expect(initials("Mary Ann Smith")).toBe("MS")
    expect(initials("agent")).toBe("AG")
    expect(initials("  ")).toBe("?")
  })
})
