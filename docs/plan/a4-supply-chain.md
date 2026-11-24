# A4. Supply chain and CI

- **Track:** A (hardened demo)
- **Branch:** `plan/a4-supply-chain`
- **Depends on:** nothing.

## Goal

Keep dependencies patched automatically and catch insecure code and leaked secrets in CI.

## Evidence

- The repository had 37 npm advisories and 17 vulnerable backend packages before
  `ranabanerji/dependabot-security-fixes`.
- There is no `.github/dependabot.yml`, CodeQL workflow or secret scanning.
- GitHub reported 50 alerts on `main`, but local scans account for about 40. Without lockfiles or
  dependency-graph submission, Dependabot only sees Gradle versions written in `build.gradle`.

## Tasks

1. Merge `ranabanerji/dependabot-security-fixes`.
2. Add `.github/dependabot.yml` for `npm`, `gradle` (directory `/backend`), `pip` (directory
   `/scripts`) and `github-actions`. Weekly schedule; group minor and patch updates per ecosystem.
3. Add a CodeQL workflow for `java-kotlin` and `javascript-typescript` on pull requests and weekly.
4. Add gitleaks to CI (or enable GitHub secret scanning with push protection).
5. Enable Gradle dependency locking (`dependencyLocking { lockAllConfigurations() }`) and commit the
   lockfiles.
6. Submit the Gradle dependency graph with `gradle/actions/dependency-submission` so Dependabot sees
   transitive Java libraries.
7. Add `pnpm audit --audit-level high` to the frontend CI job.

## Done when

- Dependabot opens grouped PRs for every ecosystem.
- CodeQL and gitleaks run on every PR and pass on `main`.
- The Dependabot alert count matches local `pnpm audit` and OSV results.

## Out of scope

- Container image scanning and signing (see B7).
