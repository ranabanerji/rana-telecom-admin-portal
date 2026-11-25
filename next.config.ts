import type { NextConfig } from "next"
import { PHASE_DEVELOPMENT_SERVER } from "next/constants"
import { securityHeaders } from "./security-headers.mjs"

const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080"

// The Cloudflare build is a static export: the Worker serves the pages and routes /api/* to the
// backend container. Static exports cannot use rewrites or headers(), so scripts/write-static-headers.mjs
// writes the same security headers to out/_headers.
const staticExport = process.env.NEXT_OUTPUT === "export"

export default function nextConfig(phase: string): NextConfig {
  if (staticExport) return { output: "export", poweredByHeader: false }
  return {
    poweredByHeader: false,
    async headers() {
      return [
        {
          // Pages only: API responses come from the backend, which sets its own headers.
          source: "/((?!api(?:/|$)).*)",
          headers: securityHeaders({ dev: phase === PHASE_DEVELOPMENT_SERVER }),
        },
      ]
    },
    async rewrites() {
      return [
        {
          source: "/api/:path*",
          destination: `${backendUrl}/api/:path*`,
        },
      ]
    },
  }
}
