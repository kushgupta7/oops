# Requirements specification

## User roles
| Role | Summary |
|---|---|
| **Student** (reporter) | Reports incidents, tracks own incidents, answers information requests, verifies or reopens resolutions, gives feedback |
| **Technician** | Works assigned incidents: acknowledge, investigate, notes, request info, resolve; requests transfer/collaboration; sees own workload, suggested resolutions and SLA risk |
| **Administrator / IT Manager** | Classifies, assigns, configures strategies, SLA policies, services, categories, users and technicians; approves transfers; insights, audit, closure |
| **System** (scheduler) | SLA monitoring, escalation, correlation, auto-close, notifications |

## Functional requirements
| ID | Requirement | Status |
|---|---|---|
| FR0 | Students self-register with institutional e-mail and a password of 10+ characters containing a letter and a number | Done |
| FR1 | Users sign in with institutional e-mail + password; sessions expire after 30 min idle | Done |
| FR2 | Admin creates, edits, enables/disables users (Student/Technician/Admin) | Done |
| FR3 | Admin manages technician skills, availability and maximum workload | Done |
| FR4 | Admin manages IT services (label, enabled, business criticality, SLA factor) and incident categories | Done |
| FR5 | Student reports an incident (title, description, category, service, impact, urgency, optional number of people affected) | Done |
| FR5a | Student adds information to an open incident at any time; technician is notified | Done |
| FR6 | System suggests a category from the text and warns about possible duplicates | Done |
| FR7 | Admin classifies: system calculates priority with the active strategy (raised one level if > 100 people affected) and captures SLA deadlines scaled by the service's SLA factor | Done |
| FR8 | Admin selects priority strategy (4) and assignment strategy (5) at runtime | Done |
| FR9 | Incident assigned manually or automatically; fallback to another strategy is audited | Done |
| FR10 | Admin sees top-3 technician recommendations with explanations | Done |
| FR11 | Technician acknowledges, starts, adds internal/student-visible notes | Done |
| FR12 | Technician requests information → AWAITING_INFO, SLA paused; student replies → IN_PROGRESS | Done |
| FR13 | Technician records resolution summary and steps; sees 3 similar past resolutions | Done |
| FR14 | Student verifies or reopens; gives 1–5 feedback once | Done |
| FR15 | Admin closes verified incidents; system auto-closes after 72 h without response | Done |
| FR16 | Technician requests transfer/collaboration; admin approves/rejects; requester may cancel | Done |
| FR17 | SLA monitor issues warnings (80 %) and breaches, once each, persisted across restarts | Done |
| FR18 | Unacknowledged P1 escalates Technician → Team Lead → System Admin → IT Manager | Done |
| FR19 | ≥3 incidents on one service within 30 min are correlated into a major incident and admins alerted | Done |
| FR20 | Notifications for created, classified, priority change, assigned, acknowledged, info requested, resolved, closed, SLA warning/breach, escalation, duplicate, major incident | Done |
| FR21 | Channels: in-app (persisted, read state), e-mail (reporter lifecycle events, outbox with retry), SMS (simulated, urgent events) | Done |
| FR22 | Immutable audit trail of every action with actor, previous/new state; admin can filter | Done |
| FR23 | Search/filter by ID/keyword, status, category, priority, service, technician, date range, SLA status | Done (API and UI) |
| FR24 | Dashboards: overview KPIs, trend, per-service, category, workload, SLA compliance, MTTA/MTTR, feedback; role-specific home pages (admin live overview, technician "work on these first", student welcome) | Done |
| FR25 | Export incidents to CSV; print-to-PDF of the analytics page | Done |
| FR26 | SLA risk prediction per open incident (admin all; technician own) | Done |

## Non-functional requirements
| ID | Requirement |
|---|---|
| NFR1 Security | BCrypt, session rotation, CSRF, role + ownership checks, no secrets in code (SECURITY.md) |
| NFR2 Integrity | Pessimistic locks for lifecycle/capacity changes; DB constraints mirror domain enums; Flyway-versioned schema |
| NFR3 Reliability | Background events deduplicated in the DB; observer failures roll back; SMS failures never block work |
| NFR4 Performance | Typical API calls < 300 ms at campus scale (≤ 10k incidents); analytics computed on request |
| NFR5 Usability | Role-specific navigation; friendly error messages; one automatic retry for reads on network failure; mobile layout |
| NFR6 Maintainability | Layered architecture, SOLID, patterns documented; new strategy/channel/state = one class |
| NFR7 Testability | Pure domain/intelligence classes testable without Spring; ≥ 30 automated tests (≈ 222) |
| NFR8 Portability | Docker image; docker-compose for PostgreSQL + MailHog; environment-variable configuration |
| NFR9 Auditability | Every state change and configuration change audited with actor and timestamp |

## Business rules
1. Only the transitions in `uml/state.png` are permitted; each edge has permitted actors.
2. Priority is computed by the server; the client never supplies priority, criticality or deadlines.
3. A service without configured criticality cannot receive incidents.
4. SLA targets (priority policy × service factor) are captured at classification; later policy or factor edits never change existing deadlines.
4a. More than 100 affected people raises the calculated priority by one level.
5. The resolution clock is paused in AWAITING_INFO; the deadline moves by the paused time.
6. A technician must be enabled, AVAILABLE, have spare capacity and the mandatory skill for the category.
7. Only the primary technician may acknowledge, start, request info or resolve; collaborators may add notes.
8. Feedback is allowed once, after verification, rating 1–5.
9. Warnings, breaches, escalations and major-incident alerts are sent at most once per incident and phase.
10. Notifications are never sent to the user who caused the event.
11. RESOLVED/VERIFIED incidents with no activity for 72 h are verified/closed by the system.
