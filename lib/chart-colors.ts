const PLAN_PALETTE = Array.from({ length: 8 }, (_, index) => `var(--chart-${index + 1})`)

/**
 * Assigns palette colors to plans in ascending id order. Pass the ids from every plan chart so a plan has the
 * same color in each; up to 8 plans never share a color.
 */
export function planColors(planIds: Iterable<number>): Map<number, string> {
  const ids = [...new Set(planIds)].sort((a, b) => a - b)
  return new Map(ids.map((id, index) => [id, PLAN_PALETTE[index % PLAN_PALETTE.length]]))
}
