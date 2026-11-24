"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { responseError } from "@/lib/api-error"

interface Page<T> {
  content: T[]
  hasNext: boolean
  totalElements: number
}

/** Success-only page advancement and a generation for each filter/reset. */
export function usePagedList<T>(url: string) {
  const [items, setItems] = useState<T[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [hasNext, setHasNext] = useState(false)
  const [totalElements, setTotalElements] = useState(0)
  const state = useRef({ page: 0, generation: 0, busy: false, controller: new AbortController() })

  const refresh = useCallback(
    async (reset = true) => {
      const current = state.current
      if (reset) {
        current.controller.abort()
        current.controller = new AbortController()
        current.generation += 1
        current.page = 0
        setItems([])
        setHasNext(false)
        setTotalElements(0)
        setLoading(true)
        setLoadingMore(false)
      } else {
        if (current.busy) return
        setLoadingMore(true)
      }
      current.busy = true
      const generation = current.generation
      const page = reset ? 0 : current.page + 1
      try {
        const res = await fetch(`${url}&page=${page}&size=50`, {
          signal: current.controller.signal,
        })
        if (!res.ok) throw await responseError(res, "Failed to fetch records")
        const data: Page<T> = await res.json()
        if (generation !== current.generation) return
        current.page = page
        setItems((prev) => (reset ? data.content : [...prev, ...data.content]))
        setHasNext(data.hasNext)
        setTotalElements(data.totalElements)
        setError(null)
      } catch (e) {
        if (generation === current.generation)
          setError(e instanceof Error ? e.message : "Unknown error")
      } finally {
        if (generation === current.generation) {
          current.busy = false
          setLoading(false)
          setLoadingMore(false)
        }
      }
    },
    [url]
  )

  useEffect(() => {
    void refresh()
    const current = state.current
    return () => {
      current.generation += 1
      current.controller.abort()
    }
  }, [refresh])

  const loadMore = useCallback(() => {
    if (hasNext && !loading) return refresh(false)
  }, [hasNext, loading, refresh])

  return { items, setItems, loading, loadingMore, error, hasNext, totalElements, loadMore, refresh }
}
