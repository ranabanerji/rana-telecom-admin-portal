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

### Setup

```bash
pnpm setup    # Install deps + seed database with production-like data
```

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
pnpm setup:db   # Run seeder
pnpm reset:db   # Delete DB + re-seed
```

Create a Python virtual environment and install the pinned seeder dependency:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r scripts/requirements.txt
```

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
