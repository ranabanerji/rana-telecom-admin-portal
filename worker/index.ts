import { DurableObject } from "cloudflare:workers"

interface Env {
  ASSETS: Fetcher
  BACKEND: DurableObjectNamespace<Backend>
  DEMO_ADMIN_PASSWORD?: string
  DEMO_AGENT_PASSWORD?: string
}

const BACKEND_PORT = 8080
const INACTIVITY_TIMEOUT_MS = 15 * 60 * 1000
// Spring Boot on a 1/4 vCPU instance can take most of a minute to start.
const READY_TIMEOUT_MS = 3 * 60 * 1000
const DEMO_LOGIN_SECRETS = ["DEMO_ADMIN_PASSWORD", "DEMO_AGENT_PASSWORD"] as const

/** Runs the Spring Boot backend in a Container and forwards API requests to it. */
export class Backend extends DurableObject<Env> {
  private starting: Promise<void> | undefined
  private monitoring: Promise<void> | undefined

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env)
    const container = ctx.container
    if (container?.running) {
      // The Durable Object restarted while its container kept running.
      void ctx.blockConcurrencyWhile(() => container.setInactivityTimeout(INACTIVITY_TIMEOUT_MS))
      this.observeExit(container)
    }
  }

  async fetch(request: Request): Promise<Response> {
    this.starting ??= this.startAndWaitUntilReady().finally(() => {
      this.starting = undefined
    })
    await this.starting

    const url = new URL(request.url)
    url.protocol = "http:"
    url.host = "container"
    const forwarded = new Request(url, request)
    forwarded.headers.delete("host")
    return this.requireContainer().getTcpPort(BACKEND_PORT).fetch(forwarded)
  }

  private async startAndWaitUntilReady(): Promise<void> {
    const container = this.requireContainer()
    if (!container.running) {
      container.start({ enableInternet: false, env: this.demoLoginEnv() })
    }
    this.observeExit(container)
    await container.setInactivityTimeout(INACTIVITY_TIMEOUT_MS)

    const port = container.getTcpPort(BACKEND_PORT)
    const deadline = Date.now() + READY_TIMEOUT_MS
    let lastError: unknown
    while (Date.now() < deadline) {
      try {
        const response = await port.fetch("http://container/api/health", {
          signal: AbortSignal.timeout(2000),
        })
        await response.body?.cancel()
        if (response.ok) return
        lastError = new Error(`Health check returned ${response.status}`)
      } catch (error) {
        lastError = error
      }
      await scheduler.wait(500)
    }
    throw new Error(`Backend did not become ready on port ${BACKEND_PORT}`, { cause: lastError })
  }

  private demoLoginEnv(): Record<string, string> {
    const env: Record<string, string> = {}
    for (const name of DEMO_LOGIN_SECRETS) {
      const value = this.env[name]
      if (value) env[name] = value
    }
    return env
  }

  private requireContainer(): Container {
    const container = this.ctx.container
    if (!container) throw new Error("The Backend Durable Object has no container configured")
    return container
  }

  private observeExit(container: Container): void {
    if (this.monitoring) return
    this.monitoring = container
      .monitor()
      .then(() => console.log("Backend container exited"))
      .catch((error: unknown) => console.error("Backend container failed:", error))
      .finally(() => {
        this.monitoring = undefined
      })
    this.ctx.waitUntil(this.monitoring)
  }
}

export default {
  async fetch(request, env): Promise<Response> {
    const { pathname } = new URL(request.url)
    if (pathname === "/api" || pathname.startsWith("/api/")) {
      // One instance: sessions live in the backend's memory and the data in its SQLite file.
      return env.BACKEND.getByName("primary").fetch(request)
    }
    return env.ASSETS.fetch(request)
  },
} satisfies ExportedHandler<Env>
