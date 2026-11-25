import type { NextConfig } from "next"
import { PHASE_DEVELOPMENT_SERVER } from "next/constants"
import { securityHeaders } from "./security-headers.mjs"

const backendUrl = process.env.BACKEND_URL ?? "http://127.0.0.1:8080"

export default function nextConfig(phase: string): NextConfig {
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
