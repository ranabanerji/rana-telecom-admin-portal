import type { NextConfig } from "next"

const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080"

// The Cloudflare build is a static export: the Worker serves the pages and routes /api/* to the
// backend container, and static exports cannot use rewrites.
const staticExport = process.env.NEXT_OUTPUT === "export"

const nextConfig: NextConfig = staticExport
  ? { output: "export" }
  : {
      async rewrites() {
        return [
          {
            source: "/api/:path*",
            destination: `${backendUrl}/api/:path*`,
          },
        ]
      },
    }

export default nextConfig
