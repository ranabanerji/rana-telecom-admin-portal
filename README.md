# Telecom Demo Admin Portal

A telecom user administration system built with Next.js and Spring Boot.

> **Demo use only:** This project uses generated sample data and in-memory demo logins.
> Do not use real customer data or expose the application to the public internet.

## Features

- **Dashboard** - KPI cards and charts showing business metrics
- **Customer Management** - CRUD operations for subscriber accounts
- **Plan Management** - Service plan catalog (Basic, Standard, Premium, Unlimited, Family)
- **Device Inventory** - Track devices and SIM cards with assignment status
- **Usage Records** - View call, data, and SMS usage history
- **Support Tickets** - Customer service ticket management

## Tech Stack

**Frontend:**

- Next.js 16 with App Router
- React 19
- Tailwind CSS 4
- shadcn/ui components
- Recharts (via shadcn/ui)
- TypeScript

**Backend:**

- Spring Boot 4.0
- Java 21
- Gradle
- SQLite (auto-seeded with demo data)

## Getting Started

### Prerequisites

- Node.js 22+
- Java 21+
- pnpm
- Python 3.10+ with venv/pip support

### Setup

```bash
pnpm run setup # Install JS/Python deps, migrate schema, then seed an empty database
```

`pnpm setup` is pnpm's built-in shell setup command; use **`pnpm run setup`** for this repository script.

Or install without seeding:

```bash
pnpm install
```

### Database Seeding

The Python seeder (`scripts/seed-database.py`) generates production-like data:

| Entity          | Count    |
| --------------- | -------- |
| Plans           | 8        |
| Customers       | 500      |
| Devices         | 600      |
| Usage Records   | ~220,000 |
| Support Tickets | 250      |

```bash
pnpm migrate:db # Apply backend Flyway migrations without starting the server
pnpm setup:db   # Migrate, then insert sample data only when the database is empty
```

The setup command creates `.venv` with the pinned seeder dependency. To install it separately:

```bash
pnpm setup:python
pnpm test:database # Conversion and deterministic UTC seeding regressions
```

### Schema and timestamp upgrades

The backend's `backend/src/main/resources/db/migration/V1__baseline.sql` is the single schema definition. Flyway runs at startup and through `pnpm migrate:db`; Hibernate never creates tables, including in tests. SQLite foreign keys are enforced on every JDBC connection. The six query indexes live in that migration.

All timestamp columns contain **UTC epoch milliseconds**. Date-only usage filters describe UTC calendar days: `dateFrom` is inclusive and `dateTo` includes that day using an exclusive next-midnight bound. The API returns ISO-8601 instants; the UI uses the browser's local timezone. The seed uses a single aware UTC clock, seed 42 and caps generated timestamps at that clock. Repeating setup leaves existing data alone.

Existing databases are deliberately **not automatically baselined**: naive text timestamps cannot be interpreted safely without knowing their source timezone. Choose one of these paths before upgrading:

- **Disposable generated data:** stop the backend, move `backend/app.db` (and any SQLite sidecar files) to a backup location, then run `pnpm setup:db` and `pnpm dev`. Keep the backup until verification is complete. No reset command deletes data automatically.
- **Retained data:** stop writers, keep a backup and inventory first. The converter creates a new file, uses a consistent source snapshot, preserves all application rows and IDs, checks row counts and foreign keys, leaves integer milliseconds untouched, and refuses ambiguous/nonexistent local times, unsupported storage or unrecognized tables. It removes incomplete output on failure. It never changes the source or overwrites an existing target.

```bash
python3 scripts/migrate-legacy-database.py backend/app.db
# Set LEGACY_SOURCE_TIMEZONE to the confirmed IANA timezone of the old text writer.
# Do not infer it from the server's current timezone. Offset-aware text needs no assumption.
python3 scripts/migrate-legacy-database.py backend/app.db \
  --target /tmp/telecom-converted.db --source-timezone "$LEGACY_SOURCE_TIMEZONE"
```

Review the inventories, counts and representative instants in both files. If old text came from multiple timezones, resolve those rows explicitly before conversion; a single zone cannot repair them safely. Orphaned references must also be repaired explicitly in a copy; the converter never drops dependents. Re-converting a canonical copy leaves timestamps unchanged and requires a new target filename.

Only after verifying the converted copy, explicitly adopt its V1 schema and start against it:

```bash
export TELECOM_JDBC_URL='jdbc:sqlite:/tmp/telecom-converted.db?foreign_keys=true&date_class=INTEGER&date_precision=MILLISECONDS'
(cd backend && ./gradlew migrateDatabase -PadoptLegacy=true)
pnpm dev
```

Use `TELECOM_DB_PATH` with the corresponding file path if invoking the seeder against a custom migrated database. Keep writers stopped until the chosen database is verified and switched over. Roll back by stopping the new backend and restoring the original database **with the prior application revision**, retaining the converted copy as well. Never run the old writer against converted data.

### API business rules and bounded lists

- Customer deletion conflicts with dependent tickets, usage or devices. Plans with customers must be deactivated instead. Conflicts return 409 Problem Details and remain visible in the delete dialog.
- Device `ASSIGNED` requires an existing customer; `AVAILABLE`, `LOST` and `DAMAGED` clear both customer and assignment time. Omitted status infers assignment from the supplied customer ID. Unknown customer IDs are rejected even with an unassigned status.
- Ticket descriptions are optional: omitted/null input is normalized to an empty string, and forms send an empty string when cleared. Reopening clears resolution time; transitions between resolved/closed preserve it.
- Customers/devices/plans and customer usage retain bounded array responses, sorted by ID, with `X-Total-Count`. The three list pages show a truncation notice. Full server paging remains FAC-27; do not calculate complete aggregates from a truncated list. Dashboard plan aggregates identify plans by ID, allowing duplicate display names.

### Demo logins

The portal and every `/api/**` endpoint except `/api/health` require a login. The backend creates two
demo accounts at startup from environment variables. Passwords have **no default** and are never committed:
an account whose password variable is unset is disabled, and the backend logs a warning.

| Variable              | Default             | Purpose                    |
| --------------------- | ------------------- | -------------------------- |
| `DEMO_ADMIN_USERNAME` | `admin`             | Admin login name           |
| `DEMO_ADMIN_PASSWORD` | _(none, required)_  | Admin password             |
| `DEMO_ADMIN_NAME`     | `Demo Admin`        | Name shown in the sidebar  |
| `DEMO_ADMIN_EMAIL`    | `admin@example.com` | Email shown in the sidebar |
| `DEMO_AGENT_USERNAME` | `agent`             | Agent login name           |
| `DEMO_AGENT_PASSWORD` | _(none, required)_  | Agent password             |
| `DEMO_AGENT_NAME`     | `Demo Agent`        | Name shown in the sidebar  |
| `DEMO_AGENT_EMAIL`    | `agent@example.com` | Email shown in the sidebar |

Pick your own passwords and export them in the shell that starts the backend (or `pnpm dev`), for example:

```bash
export DEMO_ADMIN_PASSWORD="$(openssl rand -base64 18)"
export DEMO_AGENT_PASSWORD="$(openssl rand -base64 18)"
echo "admin / $DEMO_ADMIN_PASSWORD"
echo "agent / $DEMO_AGENT_PASSWORD"
pnpm dev
```

Then open http://localhost:3000 and log in as `admin` or `agent` with the password you set.
Accounts live in memory, so changing a variable takes effect on the next backend restart.

| Role    | Can do                                                                                   |
| ------- | ---------------------------------------------------------------------------------------- |
| `ADMIN` | Everything: create, edit and delete customers, plans, devices and tickets                |
| `AGENT` | Read every page and API; create and update support tickets (no deletes, no other writes) |

The UI hides the buttons a role cannot use, and the API enforces the same rules: requests without a
login get `401`, and a logged-in user without permission gets `403`.

#### How authentication works

- Login is a form POST to `/api/auth/login` (`username`, `password`). It returns the user profile and sets
  an HTTP-only `JSESSIONID` session cookie. `GET /api/auth/me` returns the current user and
  `POST /api/auth/logout` ends the session.
- CSRF protection uses a double-submit cookie: `GET /api/auth/csrf` issues a readable `XSRF-TOKEN` cookie,
  and every `POST`, `PUT` and `DELETE` (login and logout included) must send its value in an
  `X-XSRF-TOKEN` header. The frontend's `apiFetch` helper (`lib/api.ts`) does this automatically.

Example with curl:

```bash
curl -c jar -b jar -s http://localhost:8080/api/auth/csrf
TOKEN=$(awk '$6 == "XSRF-TOKEN" {print $7}' jar)
curl -c jar -b jar -H "X-XSRF-TOKEN: $TOKEN" -d username=admin --data-urlencode "password=$DEMO_ADMIN_PASSWORD" \
  http://localhost:8080/api/auth/login
curl -b jar http://localhost:8080/api/customers/1
```

### Development

Run both frontend and backend:

```bash
pnpm dev
```

- Frontend: http://localhost:3000
- Backend: http://localhost:8080

Or run separately:

```bash
pnpm dev:frontend   # Frontend only
pnpm dev:backend    # Backend only
```

### Testing

```bash
pnpm test           # All tests
pnpm test:frontend  # Vitest
pnpm test:backend   # Gradle test
pnpm test:e2e       # Playwright (needs DEMO_ADMIN_PASSWORD and DEMO_AGENT_PASSWORD)
```

The Playwright suite starts the backend and frontend, logs in through the login page as both demo users
(`e2e/auth.setup.ts`), and reuses those sessions in the other tests. Export the demo login variables first;
the backend it starts reads the same values. Backend role tests generate random passwords per run.

### Linting

```bash
pnpm lint
```

## Deploying to Cloudflare

The `Deploy` workflow (`.github/workflows/deploy.yml`) publishes the portal to
https://telecom.working.ranabanerji.com after CI passes on `main`, and can also be run by hand from the
Actions tab. Cloudflare Access protects every `*.working.ranabanerji.com` hostname, so only people the Access
policy allows can reach the login page. The Worker's `workers.dev` and preview URLs are turned off because
they would bypass Access.

How it fits together (`wrangler.jsonc`):

- `pnpm build:cloudflare` builds the frontend as a static export into `out/`. The Worker serves it as static
  assets, so no Next.js server runs on Cloudflare.
- The Worker (`worker/index.ts`) sends `/api/*` to a single `Backend` Durable Object, which starts the Spring
  Boot backend in a [Cloudflare Container](https://developers.cloudflare.com/containers/) and forwards the
  request to it. The first request after an idle period waits for the backend to start; the container stops
  after 15 minutes without requests.
- The container image (`backend/Dockerfile`, built from the repository root) ships a migrated and seeded
  SQLite database. **Data is not kept**: every container start, including after each deploy, begins with
  fresh demo data, and logins end when the container stops.

One-time setup:

1. The Cloudflare account needs the Workers Paid plan, which Containers require.
2. Create an API token from the **Edit Cloudflare Workers** template, add **Account > Containers > Edit**, and
   limit it to this account and the `ranabanerji.com` zone.
3. Add repository secrets `CLOUDFLARE_API_TOKEN`, `DEMO_ADMIN_PASSWORD` and `DEMO_AGENT_PASSWORD`. Deploys
   upload the two passwords as Worker secrets, and the backend reads them when its container starts.

To deploy from a machine with Docker running instead: `CLOUDFLARE_API_TOKEN=... pnpm deploy:cloudflare`.

CI's `Cloudflare build` job runs `scripts/smoke-test-cloudflare.sh`. It starts the Worker, static assets and
backend container locally with `wrangler dev` and checks pages, the API and a login through the Worker. Run
it yourself with Docker running, after `pnpm build:cloudflare`, with the demo login variables exported.

## Application Pages

| Page      | URL          | Description           |
| --------- | ------------ | --------------------- |
| Login     | `/login`     | Sign in               |
| Dashboard | `/dashboard` | KPIs and charts       |
| Customers | `/customers` | Subscriber management |
| Plans     | `/plans`     | Service plan catalog  |
| Devices   | `/devices`   | Device/SIM inventory  |
| Usage     | `/usage`     | Usage records         |
| Tickets   | `/tickets`   | Support tickets       |

## API Endpoints

| Endpoint           | Methods                | Description          | Access                                  |
| ------------------ | ---------------------- | -------------------- | --------------------------------------- |
| `/api/health`      | GET                    | Health check         | Public                                  |
| `/api/auth/csrf`   | GET                    | Issue CSRF cookie    | Public                                  |
| `/api/auth/login`  | POST                   | Log in               | Public (CSRF token required)            |
| `/api/auth/logout` | POST                   | Log out              | Logged in                               |
| `/api/auth/me`     | GET                    | Current user         | Logged in                               |
| `/api/customers`   | GET, POST, PUT, DELETE | Customer CRUD        | GET: any role; writes: ADMIN            |
| `/api/plans`       | GET, POST, PUT, DELETE | Plan CRUD            | GET: any role; writes: ADMIN            |
| `/api/devices`     | GET, POST, PUT, DELETE | Device CRUD          | GET: any role; writes: ADMIN            |
| `/api/usage`       | GET                    | Usage records        | Any role                                |
| `/api/tickets`     | GET, POST, PUT, DELETE | Ticket CRUD          | GET, POST, PUT: any role; DELETE: ADMIN |
| `/api/dashboard/*` | GET                    | Dashboard statistics | Any role                                |

## API Documentation

Start the backend, then open the Swagger UI at
`http://localhost:8080/swagger-ui.html`.

## Project Structure

```
├── app/                    # Next.js pages
│   ├── dashboard/          # Dashboard with KPIs/charts
│   ├── customers/          # Customer management
│   ├── plans/              # Plan management
│   ├── devices/            # Device inventory
│   ├── usage/              # Usage records
│   └── tickets/            # Support tickets
├── backend/                # Spring Boot backend
│   └── src/main/java/com/example/telecom/
│       ├── model/          # JPA entities
│       ├── repository/     # Spring Data repos
│       ├── security/       # Spring Security config, roles, demo users
│       └── controller/     # REST controllers
├── scripts/                # Utility scripts
│   ├── seed-database.py    # Production-like data seeder
│   └── requirements.txt    # Python dependencies
├── components/             # React components
│   └── ui/                 # shadcn/ui components
├── hooks/                  # Custom React hooks
└── lib/                    # Utilities
```

## License

No open-source license is granted. See `LICENSE`.
