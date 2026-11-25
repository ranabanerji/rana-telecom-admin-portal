# Security Policy

Telecom Demo is a local demonstration application. Its logins are in-memory demo
accounts configured through environment variables (see "Demo logins" in the
README). Do not use real customer data or expose the application to the public
internet.

Logins are throttled, demo passwords must be at least 12 characters, and pages carry a
Content Security Policy. If you serve the app over HTTPS, set `SESSION_COOKIE_SECURE=true`.
See "Security settings" in `README.md`.

Report suspected vulnerabilities through the repository host's private security
reporting channel. Do not include credentials or personal data in public issues.
