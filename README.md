# Telecom Demo Admin Portal

A telecom user administration system built with Next.js and Spring Boot.

> **Demo use only:** This project uses generated sample data and an unauthenticated local API.
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

### Development

Run both frontend and backend:

```bash
pnpm dev
```

- Frontend: http://127.0.0.1:3000
- Backend: http://127.0.0.1:8080

Or run separately:

```bash
pnpm dev:frontend   # Frontend only
pnpm dev:backend    # Backend only (dev profile, Swagger UI enabled)
```

### Network Access

Both servers listen on `127.0.0.1` only, so other machines on your network cannot
reach them. This applies to `pnpm dev` and `pnpm start`.

The API has no authentication. Anyone who can reach port 8080 can read, change, and
delete every customer, device, plan, and ticket. Binding to other interfaces on a shared
network (office or café Wi-Fi, a VPN, a cloud VM) exposes that data to everyone on it.
Only do it on a network you trust, and only with demo data.

To accept connections from other machines on purpose:

```bash
SERVER_ADDRESS=0.0.0.0 pnpm dev:backend   # Backend on all interfaces
pnpm dev:frontend -H 0.0.0.0              # Frontend on all interfaces
pnpm start:frontend -H 0.0.0.0            # Production frontend on all interfaces
```

Run the servers separately to do this. `pnpm dev` and `pnpm start` always use the
loopback defaults. Use a specific interface address instead of `0.0.0.0` where you can.

Other notes:

- **Containers:** a server that binds to `127.0.0.1` inside a container cannot be reached
  from outside it, even through a published port. Set `SERVER_ADDRESS=0.0.0.0` (or pass
  `-H 0.0.0.0`) inside the container. Publish the port on the host's loopback only, for
  example `-p 127.0.0.1:8080:8080`. If the frontend runs in a different container, point
  `BACKEND_URL` at the backend's service address.
- **Proxies, tunnels, and port forwarding** (SSH `-R`, ngrok, VS Code port forwarding,
  router rules) expose loopback servers too. Loopback binding does not protect against them.
- Loopback binding limits who can connect. It is not access control. Authentication and
  role checks are still needed before this app can hold real data.

### Testing

```bash
pnpm test           # All tests
pnpm test:frontend  # Vitest
pnpm test:backend   # Gradle test
```

### Linting

```bash
pnpm lint
```

## Application Pages

| Page      | URL          | Description           |
| --------- | ------------ | --------------------- |
| Dashboard | `/dashboard` | KPIs and charts       |
| Customers | `/customers` | Subscriber management |
| Plans     | `/plans`     | Service plan catalog  |
| Devices   | `/devices`   | Device/SIM inventory  |
| Usage     | `/usage`     | Usage records         |
| Tickets   | `/tickets`   | Support tickets       |

## API Endpoints

| Endpoint           | Methods                | Description          |
| ------------------ | ---------------------- | -------------------- |
| `/api/health`      | GET                    | Health check         |
| `/api/customers`   | GET, POST, PUT, DELETE | Customer CRUD        |
| `/api/plans`       | GET, POST, PUT, DELETE | Plan CRUD            |
| `/api/devices`     | GET, POST, PUT, DELETE | Device CRUD          |
| `/api/usage`       | GET                    | Usage records        |
| `/api/tickets`     | GET, POST, PUT, DELETE | Ticket CRUD          |
| `/api/dashboard/*` | GET                    | Dashboard statistics |

## API Documentation

Swagger UI and the OpenAPI docs (`/v3/api-docs`) are enabled only in the `dev` Spring
profile. `pnpm dev` and `pnpm dev:backend` turn it on, so open
`http://127.0.0.1:8080/swagger-ui.html` after starting the backend that way.

The built jar (`pnpm start:backend`) runs without the `dev` profile and returns 404 for
both routes. To turn them on there, set `SPRING_PROFILES_ACTIVE=dev`. Hiding the docs
only makes the endpoints harder to discover. It does not protect them.

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
