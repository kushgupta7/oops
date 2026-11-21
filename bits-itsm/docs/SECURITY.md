# Security: threats and mitigations

| # | Threat | Mitigation in BITSolve | Where |
|---|---|---|---|
| S1 | Password theft from the database | BCrypt hashes only; `User` rejects non-BCrypt values; hash never serialised (`@JsonIgnore`) | `user/User.java`, `auth/` |
| S2 | Account enumeration | Wrong, unknown and disabled accounts get the identical generic error | `AuthService`, test `wrongUnknownAndDisabledAccountsReturnSameGenericError` |
| S3 | Session fixation | Session id **and** CSRF token rotated on login; session invalidated and cookie deleted on logout | `SecurityConfig.sessionAuthenticationStrategy`, test `rotatesSessionAndCsrfThenInvalidatesSessionOnLogout` |
| S4 | Cross-site request forgery | Server-side CSRF tokens required on every mutation (including login/logout); cookie `HttpOnly`, `SameSite=Lax`, `Secure` behind HTTPS | `SecurityConfig`, `api.js#apiSend` |
| S5 | Privilege escalation by URL | `/api/admin/**` = ADMIN, `/api/technician/**` = TECHNICIAN, `/api/student/**` = STUDENT, everything else authenticated | `SecurityConfig` |
| S6 | Privilege escalation inside shared endpoints | Every service method re-checks the role (`actor(email, Role…)`) **and ownership**: students see only their own incidents, technicians only incidents they own or collaborate on | `IncidentService.access`, `AssignmentService`, `InsightsService` |
| S7 | Illegal workflow manipulation | State pattern validates every edge **and the actor** for that edge (409 / 403); rolls back the transaction | `incident/state/*`, `IncidentService.finish` |
| S8 | Disabled user keeps working | Enabled flag checked on every request | `EnabledAccountFilter` |
| S9 | Injection | JPA/JPQL parameters and `JdbcTemplate` placeholders only; no string-built SQL with user input | repositories, `ScheduledEvents` |
| S10 | Mass assignment / forged fields | DTO records with Bean Validation (`@NotNull`, `@Size`, `@Min/@Max`); role, priority, criticality and deadlines are never accepted from the client | `IncidentData`, `AssignmentData` |
| S11 | Information leakage in errors | Consistent `{timestamp,status,code,message,path}`; 5xx messages replaced with a generic text; SMTP errors reduced to class names; private notes never e-mailed | `GlobalApiErrors`, `EmailChannel` |
| S12 | CSV/spreadsheet formula injection in exports | Cells starting `= + - @` are prefixed with `'` and all cells are quoted | `InsightsService.cell`, `CsvExportTest` |
| S13 | Secrets in source control | DB, SMTP and bootstrap credentials only via environment variables; demo seeding requires the `dev` profile **and** an explicit flag **and** a password variable | `application.properties`, `DemoUserSeeder` |
| S14 | Lost/duplicated background actions after restart | SLA warnings, breaches, escalations and major-incident alerts are claimed in `scheduled_events` with a unique key before publishing | `ScheduledEvents.publishOnce` |
| S16 | Abuse of self-registration | Only STUDENT accounts are created regardless of client fields; institutional e-mail only; 10–72 byte passwords with a letter and a number, confirmed twice; BCrypt; duplicate e-mail → 409. Trade-off: the duplicate response reveals that an e-mail is registered (acceptable for public campus addresses) | `AuthController.register` |
| S17 | Double submission / replay of a report | One-time `Idempotency-Key` per report form; reuse → `DuplicateIncidentException` | `IncidentService.create` |
| S15 | Notification spam by SMS | SMS restricted to urgent event types; delivery failures never roll back business actions | `SmsChannel` |

## Deliberate design choice: sessions instead of JWT

The original prompt suggested stateless JWT. The team chose **server-side sessions + CSRF** for a browser-only application served from the same origin. This removes token storage in `localStorage` (an XSS target), allows immediate revocation (disabled accounts lose access on their next request), and needs no refresh-token logic. JWT would be preferable for third-party/mobile API clients; see DECISIONS.md D1.

## Not implemented (known limitations)

* **Login rate limiting / lockout.** Brute force is slowed only by BCrypt cost. Recommended next step: a per-IP + per-account token bucket (e.g. Bucket4j) in front of `/api/auth/login`.
* **Multi-factor authentication.**
* **CORS configuration** is unnecessary because the React build is served by the same Spring Boot origin. A split-origin deployment would need an explicit allow-list.
* **HTTPS** is terminated by the hosting platform (Railway) and not by the application; `SESSION_COOKIE_SECURE=true` must be set there.
* **Security headers** beyond Spring Security defaults (CSP) are not tuned.
* **Real SMS delivery.** The SMS vendor is simulated (logs only).
