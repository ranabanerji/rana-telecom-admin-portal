import { defineConfig, devices } from "@playwright/test"

// Both servers bind to 127.0.0.1 only, so "localhost" could resolve to ::1 and miss them.
const frontendUrl = process.env.FRONTEND_URL ?? "http://127.0.0.1:3000"
const backendUrl = process.env.BACKEND_URL ?? "http://127.0.0.1:8080"

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  // No retries anywhere: a test that only passes on a second attempt is a bug to fix, not to hide.
  retries: 0,
  workers: process.env.CI ? 2 : undefined,
  // CI uploads the JUnit report with the other test results.
  reporter: [
    ["html", { open: "never" }],
    ["list"],
    ["junit", { outputFile: "test-results/e2e-junit.xml" }],
  ],
  use: {
    baseURL: frontendUrl,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    // Logs in through the UI once per role and saves the session for the other tests.
    {
      name: "setup",
      testMatch: /.*\.setup\.ts/,
      use: { ...devices["Desktop Chrome"] },
    },
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"], storageState: "playwright/.auth/admin.json" },
      dependencies: ["setup"],
    },
  ],
  webServer: [
    {
      command: "pnpm dev:backend",
      url: `${backendUrl}/api/health`,
      reuseExistingServer: !process.env.CI,
      timeout: 120000,
    },
    {
      command: "pnpm dev:frontend",
      url: frontendUrl,
      reuseExistingServer: !process.env.CI,
      timeout: 120000,
    },
  ],
})
