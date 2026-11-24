# Security Policy

Telecom Demo is an unauthenticated local demonstration application. Do not use
real customer data or expose the application to the public internet.

By default the API and the frontend listen on `127.0.0.1` only, and Swagger UI is
enabled only in the `dev` profile. See "Network Access" in `README.md` before
changing either. Binding to other interfaces exposes the unauthenticated API to
everyone who can reach the machine.

Report suspected vulnerabilities through the repository host's private security
reporting channel. Do not include credentials or personal data in public issues.
