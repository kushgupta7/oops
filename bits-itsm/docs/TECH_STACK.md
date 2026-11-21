# Technology stack

| Layer | Technology | Version | Used for |
|---|---|---|---|
| Language | Java | 25 (runs on 17+) | Backend, domain model |
| Framework | Spring Boot | 4.1.2 | Web MVC, Data JPA, Security, Validation, Mail, Scheduling |
| ORM | Hibernate (via Spring Data JPA) | 7.x | Entities, repositories, pessimistic locks |
| Database | PostgreSQL | 16+ (tested 16 and 18) | Persistent store, JSONB for strategy settings |
| Migrations | Flyway | 11.x | Versioned schema V1–V9 |
| Security | Spring Security, BCrypt | 7.x | Sessions, CSRF, role URLs |
| Mail | Spring Mail / Jakarta Mail | – | Reporter e-mail outbox (Gmail SMTP or MailHog) |
| SMS | Simulated vendor via Adapter | – | Urgent alerts (no external cost) |
| Frontend | React | 19 | Role-specific portals |
| Build tool (UI) | Vite | 8 | Dev server, production bundle served by Spring |
| Charts | Recharts | 3.2 | Insights & Analytics dashboards |
| Tests | JUnit 5, Mockito, Spring Boot Test (MockMvc), Spring Security Test, node:test | – | Unit, integration, API-client tests |
| Coverage | JaCoCo (Maven profile `coverage`) | 0.8.14 | Coverage report |
| Diagrams | PlantUML, Mermaid | – | UML and architecture docs |
| Packaging | Docker (multi-stage), docker-compose | – | Image; PostgreSQL + MailHog + app |
| CI | GitHub Actions | – | Build + test on push |
| Hosting | Railway (Dockerfile) | – | Cloud deployment |

## The nine questions

**1. Why this language? (Java)** It is the course language, and its type system expresses the OOP concepts the project is graded on: abstract classes, interfaces, sealed enums, records for immutable DTOs and events, and checked/unchecked exceptions. Java also has the most mature enterprise ecosystem for transactions, ORM and security.

**2. Why this frontend? (React + Vite)** A component model fits three role-specific portals that share incident views (`MyIncidents`, `IncidentManagementActions`). Vite gives instant dev reloads and a small production bundle that Spring serves from the same origin. Recharts adds declarative SVG charts.

**3. Why this backend? (Spring Boot)** Dependency injection makes the patterns natural: every `IncidentObserver` and `NotificationChannel` bean is injected automatically. Spring also supplies declarative transactions, `@Scheduled` jobs for SLA monitoring, Bean Validation, Spring Security's session/CSRF protection, and MockMvc for integration tests.

**4. Why this database? (PostgreSQL)** Incidents, users and assignments are highly relational and need integrity: foreign keys, check constraints mirroring enums, partial unique indexes ("one pending transfer per incident"), and row locks (`SELECT … FOR UPDATE`) for capacity checks. JSONB stores the flexible strategy configuration. `INSERT … ON CONFLICT DO NOTHING` provides exactly-once background events. Firebase or another document store would make these guarantees much harder.

**5. Why this architecture? (layered monolith + event bus)** For one campus and a five-person team, a layered monolith is simple to build, test and deploy. Layers (controller → service → domain → repository) keep responsibilities separate. The internal EventBus gives event-driven decoupling (audit, notifications, SMS) without operating a message broker.

**6. Alternatives considered**
| Choice | Alternative | Why not (now) |
|---|---|---|
| Spring Boot | Jakarta EE / Quarkus / Node.js | Less course familiarity; Node lacks the OOP emphasis |
| PostgreSQL | MySQL, MongoDB, Firebase | Weaker check/partial-index support (MySQL); no relational integrity (Mongo/Firebase) |
| Sessions + CSRF | JWT | Revocation and safe token storage are harder for a browser-only client (SECURITY.md) |
| React | Angular, Thymeleaf | Angular is heavier for this size; server templates give a weaker interactive UX |
| In-process EventBus | Kafka / RabbitMQ | Operational overhead not justified at campus scale (see Q9) |
| Rule-based intelligence | Paid LLM APIs | Cost, privacy of student data, non-determinism in a graded demo |

**7. Advantages and limitations**
* *Advantages:* strong consistency and auditability; patterns make features pluggable; one deployable; all intelligence explainable and testable offline; low cost.
* *Limitations:* single instance (in-memory round-robin cursor and SMS history); analytics computed on request; no mobile push; SMS simulated; no login rate limiting; the UI is JavaScript rather than TypeScript.

**8. How the stack supports object-oriented design** JPA maps the class hierarchy (`User` → subclasses, single-table inheritance) and composition (cascade on notes/history/feedback) directly. Spring's DI injects interfaces (`List<IncidentObserver>`, `List<NotificationChannel>`, `SmsGateway`), so the code depends on abstractions (Dependency Inversion). Strategy, State and Chain objects stay plain Java and are unit-testable without Spring. Records give immutable value objects (`IncidentEvent`, `IncidentSnapshot`, DTOs).

**9. Evolution at much larger scale (e.g. all BITS campuses)**
* **Horizontal scaling:** run several stateless app instances behind a load balancer; move sessions to Spring Session + Redis; move the round-robin cursor and SMS history to Redis; elect one scheduler leader (ShedLock), with `scheduled_events` already guaranteeing exactly-once alerts.
* **Caching:** cache catalog, SLA policies and strategy settings (Caffeine/Redis, evicted on admin change); materialise daily analytics.
* **Message queue:** replace the in-process EventBus with Kafka/RabbitMQ topics (`incident-events`). Audit, notifications, e-mail and SMS become independent consumers with retries and dead-letter queues; use the transactional outbox pattern (the e-mail outbox already works this way).
* **Microservice split** along the existing packages: Identity, Incident (lifecycle + SLA), Assignment, Notification, Analytics/Intelligence (read model fed by events), each with its own schema.
* **Search and ML:** OpenSearch for full-text search; replace `SimilarityModel` with sentence embeddings and a vector index; train the SLA-risk model on the accumulated history.
* **Database:** read replicas for analytics, partition `audit_events` and `notification_deliveries` by month.
