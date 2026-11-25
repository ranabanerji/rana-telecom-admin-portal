import { readFileSync } from "node:fs"
import path from "node:path"
import { describe, expect, it } from "vitest"

const packageJson = JSON.parse(
  readFileSync(path.resolve(__dirname, "../package.json"), "utf8")
) as {
  scripts: Record<string, string>
}

describe("frontend listen address", () => {
  it.each(["dev:frontend", "start:frontend"])("%s binds to 127.0.0.1", (script) => {
    expect(packageJson.scripts[script]).toMatch(/\s-H 127\.0\.0\.1(\s|$)/)
  })
})
