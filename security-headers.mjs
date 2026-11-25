// Shared by next.config.ts and the static-export header file, so every deployment sends the same policy.

/**
 * Security headers for every page.
 *
 * Scripts and styles need 'unsafe-inline' because Next.js inlines its hydration data and the theme script,
 * and a static export cannot carry per-request nonces. The policy still blocks scripts from other origins,
 * plugins, framing, and form posts or fetches to other sites.
 *
 * @param {{ dev?: boolean }} [options] dev adds what `next dev` needs (eval for React Refresh, the HMR socket).
 * @returns {{ key: string, value: string }[]}
 */
export function securityHeaders({ dev = false } = {}) {
  const csp = [
    "default-src 'self'",
    `script-src 'self' 'unsafe-inline'${dev ? " 'unsafe-eval'" : ""}`,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: blob:",
    "font-src 'self' data:",
    `connect-src 'self'${dev ? " ws:" : ""}`,
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join("; ")

  return [
    { key: "Content-Security-Policy", value: csp },
    { key: "X-Content-Type-Options", value: "nosniff" },
    { key: "X-Frame-Options", value: "DENY" },
    { key: "Referrer-Policy", value: "same-origin" },
    { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
    {
      key: "Permissions-Policy",
      value: "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
    },
  ]
}
