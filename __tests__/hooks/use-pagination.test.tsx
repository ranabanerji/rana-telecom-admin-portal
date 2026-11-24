import { act, renderHook, waitFor } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import { useTickets } from "@/hooks/use-tickets"
import { UNAUTHORIZED_EVENT } from "@/lib/api"
import { useUsage } from "@/hooks/use-usage"

function page(id: number, hasNext = true): Response {
  return {
    ok: true,
    json: async () => ({ content: [{ id }], hasNext, totalElements: 100 }),
  } as Response
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

const cases = [
  {
    name: "usage",
    useHook: (type: string) => {
      const h = useUsage({ type: type as "SMS" | "CALL" })
      return { ...h, items: h.records }
    },
  },
  {
    name: "tickets",
    useHook: (status: string) => {
      const h = useTickets({ status: status as "OPEN" | "CLOSED" })
      return { ...h, items: h.tickets }
    },
  },
]

afterEach(() => vi.unstubAllGlobals())

for (const { name, useHook } of cases)
  describe(`${name} pagination`, () => {
    const firstFilter = name === "usage" ? "SMS" : "OPEN"
    const secondFilter = name === "usage" ? "CALL" : "CLOSED"

    it("retries the failed page and prevents concurrent load-more requests", async () => {
      const pending = deferred<Response>()
      const fetcher = vi
        .fn()
        .mockResolvedValueOnce(page(1))
        .mockReturnValueOnce(pending.promise)
        .mockResolvedValueOnce(page(2, false))
      vi.stubGlobal("fetch", fetcher)
      const { result } = renderHook(() => useHook(firstFilter))
      await waitFor(() => expect(result.current.loading).toBe(false))
      act(() => {
        void result.current.loadMore()
        void result.current.loadMore()
      })
      expect(fetcher).toHaveBeenCalledTimes(2)
      await act(async () =>
        pending.resolve({ ok: false, json: async () => ({ detail: "Try again" }) } as Response)
      )
      expect(result.current.error).toBe("Try again")
      await act(async () => {
        await result.current.loadMore()
      })
      expect(
        fetcher.mock.calls
          .slice(1)
          .map(([url]) => new URL(url, "http://test").searchParams.get("page"))
      ).toEqual(["1", "1"])
      expect(result.current.items).toEqual([{ id: 1 }, { id: 2 }])
      expect(result.current.hasNext).toBe(false)
      expect(result.current.error).toBeNull()
    })

    it.each([false, true])(
      "ignores stale load-more responses or failures after filters change (failure=%s)",
      async (failure) => {
        const pending = deferred<Response>()
        const fetcher = vi
          .fn()
          .mockResolvedValueOnce(page(1))
          .mockReturnValueOnce(pending.promise)
          .mockResolvedValueOnce(page(20, false))
        vi.stubGlobal("fetch", fetcher)
        const { result, rerender } = renderHook(({ filter }) => useHook(filter), {
          initialProps: { filter: firstFilter },
        })
        await waitFor(() => expect(result.current.loading).toBe(false))
        act(() => {
          void result.current.loadMore()
        })
        const oldSignal = fetcher.mock.calls[1][1].signal as AbortSignal
        rerender({ filter: secondFilter })
        await waitFor(() => expect(result.current.items).toEqual([{ id: 20 }]))
        expect(oldSignal.aborted).toBe(true)
        await act(async () =>
          pending.resolve(
            failure
              ? ({ ok: false, json: async () => ({ detail: "Old error" }) } as Response)
              : page(2)
          )
        )
        expect(result.current.items).toEqual([{ id: 20 }])
        expect(result.current.error).toBeNull()
        expect(result.current.hasNext).toBe(false)
      }
    )

    it("does not refetch for a new filters object with the same values", async () => {
      const fetcher = vi.fn().mockResolvedValue(page(1))
      vi.stubGlobal("fetch", fetcher)
      const { result, rerender } = renderHook(() => useHook(firstFilter))
      await waitFor(() => expect(result.current.loading).toBe(false))
      rerender()
      expect(fetcher).toHaveBeenCalledTimes(1)
    })
  })

describe("authenticated pagination", () => {
  it("announces an expired session while keeping pagination error handling", async () => {
    const unauthorized = vi.fn()
    window.addEventListener(UNAUTHORIZED_EVENT, unauthorized)
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: async () => ({ detail: "Log in again" }),
      })
    )
    try {
      const { result } = renderHook(() => useUsage())
      await waitFor(() => expect(result.current.loading).toBe(false))
      expect(unauthorized).toHaveBeenCalledOnce()
      expect(result.current.error).toBe("Log in again")
      expect(result.current.records).toEqual([])
    } finally {
      window.removeEventListener(UNAUTHORIZED_EVENT, unauthorized)
    }
  })
})
