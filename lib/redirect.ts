/** Only allow same-site relative paths so ?next= cannot redirect to another origin. */
export function safeNextPath(next: string | null | undefined): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) {
    return "/dashboard"
  }
  return next === "/login" || next.startsWith("/login?") || next.startsWith("/login/")
    ? "/dashboard"
    : next
}
