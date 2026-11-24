import { test, expect } from "@playwright/test"

const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080"

interface ApiCustomer {
  status: string
  plan_id: number | null
}

interface ApiPlan {
  id: number
  monthly_price: number
}

/**
 * Cross-checks the dashboard aggregates against the raw customer and plan lists.
 * The seeded database contains suspended and cancelled subscribers, so any
 * aggregation that forgets to filter on ACTIVE shows up here as a mismatch.
 */
test.describe("Dashboard revenue aggregates", () => {
  test("monthly revenue counts only active subscribers", async ({ request }) => {
    const [statsRes, customersRes, plansRes] = await Promise.all([
      request.get(`${backendUrl}/api/dashboard/stats`),
      request.get(`${backendUrl}/api/customers`),
      request.get(`${backendUrl}/api/plans`),
    ])
    expect(statsRes.ok()).toBeTruthy()
    expect(customersRes.ok()).toBeTruthy()
    expect(plansRes.ok()).toBeTruthy()

    const stats = await statsRes.json()
    const customers: ApiCustomer[] = await customersRes.json()
    test.skip(
      Number(customersRes.headers()["x-total-count"]) > customers.length,
      "Bounded customer list is truncated; aggregate cross-check needs the full fixture"
    )
    const plans: ApiPlan[] = await plansRes.json()
    test.skip(
      Number(plansRes.headers()["x-total-count"]) > plans.length,
      "Bounded plan list is truncated; aggregate cross-check needs the full fixture"
    )

    const priceByPlan = new Map(plans.map((p) => [p.id, p.monthly_price]))
    const expected = customers
      .filter((c) => c.status === "ACTIVE" && c.plan_id !== null)
      .reduce((sum, c) => sum + (priceByPlan.get(c.plan_id as number) ?? 0), 0)

    expect(Number(stats.monthly_revenue)).toBeCloseTo(expected, 2)
  })

  test("active customer count matches the customer list", async ({ request }) => {
    const [statsRes, customersRes] = await Promise.all([
      request.get(`${backendUrl}/api/dashboard/stats`),
      request.get(`${backendUrl}/api/customers`),
    ])

    const stats = await statsRes.json()
    const customers: ApiCustomer[] = await customersRes.json()
    test.skip(
      Number(customersRes.headers()["x-total-count"]) > customers.length,
      "Bounded customer list is truncated; aggregate cross-check needs the full fixture"
    )
    const activeCount = customers.filter((c) => c.status === "ACTIVE").length

    expect(stats.active_customers).toBe(activeCount)
  })

  test("revenue by plan excludes plans with no active subscribers", async ({ request }) => {
    const [revenueRes, customersRes] = await Promise.all([
      request.get(`${backendUrl}/api/dashboard/revenue-by-plan`),
      request.get(`${backendUrl}/api/customers`),
    ])

    const revenue: { plan_id: number; revenue: number }[] = await revenueRes.json()
    const customers: ApiCustomer[] = await customersRes.json()
    test.skip(
      Number(customersRes.headers()["x-total-count"]) > customers.length,
      "Bounded customer list is truncated; aggregate cross-check needs the full fixture"
    )
    const plansWithActive = new Set(
      customers.filter((c) => c.status === "ACTIVE").map((c) => c.plan_id)
    )

    for (const row of revenue) {
      expect(plansWithActive.has(row.plan_id)).toBeTruthy()
    }
  })
})
