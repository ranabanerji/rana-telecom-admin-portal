#!/usr/bin/env node
/** Checks the local toolchain against the versions pinned in .tool-versions. */
import { existsSync } from "node:fs"
import { spawnSync } from "node:child_process"

function run(command, args) {
  const result = spawnSync(command, args, { encoding: "utf8" })
  if (result.error) return null
  return `${result.stdout ?? ""}${result.stderr ?? ""}`.trim()
}

const failures = []
const checks = [
  {
    name: "Node.js",
    expect: "22.x",
    actual: () => process.versions.node,
    ok: (v) => v.split(".")[0] === "22",
    fix: "mise install (reads .tool-versions) or install Node 22",
  },
  {
    name: "pnpm",
    expect: "10.x (package.json pins pnpm@10.33.4)",
    actual: () => run("pnpm", ["--version"]),
    ok: (v) => v.split(".")[0] === "10",
    fix: "corepack enable, which activates the pnpm version from package.json",
  },
  {
    name: "Java",
    expect: "21",
    actual: () => run("java", ["-version"])?.match(/version "([^"]+)"/)?.[1] ?? null,
    ok: (v) => v.split(".")[0] === "21",
    fix: "mise install (reads .tool-versions) or install a Java 21 JDK",
  },
  {
    name: "Python",
    expect: "3.10+",
    actual: () => run("python3", ["--version"])?.replace("Python ", "") ?? null,
    ok: (v) => {
      const [major, minor] = v.split(".").map(Number)
      return major === 3 && minor >= 10
    },
    fix: "mise install (reads .tool-versions) or install Python 3.10+",
  },
]

for (const check of checks) {
  const actual = check.actual()
  if (actual === null || actual === undefined || actual === "") {
    failures.push(check)
    console.log(`MISSING ${check.name}: not found. Fix: ${check.fix}`)
  } else if (!check.ok(actual)) {
    failures.push(check)
    console.log(`WRONG   ${check.name}: want ${check.expect}, found ${actual}. Fix: ${check.fix}`)
  } else {
    console.log(`OK      ${check.name} ${actual}`)
  }
}

if (!existsSync(".venv")) {
  console.log('NOTE    .venv is missing; "pnpm bootstrap" creates it and seeds the database')
}

if (failures.length > 0) {
  process.exitCode = 1
}
