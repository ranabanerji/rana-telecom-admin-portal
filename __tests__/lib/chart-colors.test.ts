import { describe, it, expect } from "vitest"
import { planColors } from "@/lib/chart-colors"

describe("planColors", () => {
  it("gives 8 plans 8 different colors", () => {
    const colors = planColors([1, 2, 3, 4, 5, 6, 7, 8])
    expect(new Set(colors.values()).size).toBe(8)
  })

  it("does not depend on plan names, so Family Share gets its own color", () => {
    const colors = planColors([5, 1])
    expect(colors.get(1)).toBe("var(--chart-1)")
    expect(colors.get(5)).toBe("var(--chart-2)")
  })

  it("gives a plan the same color whatever order or chart the ids come from", () => {
    const fromCustomers = planColors([3, 1, 2])
    const fromBoth = planColors([1, 2, 3, 2, 1])
    expect([...fromCustomers]).toEqual([...fromBoth])
  })

  it("reuses the palette only after 8 plans", () => {
    const colors = planColors([10, 20, 30, 40, 50, 60, 70, 80, 90])
    expect(colors.get(90)).toBe(colors.get(10))
  })
})
