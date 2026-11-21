# Plan and requirement traceability

Status legend: ✅ done · 🟡 partial / alternative chosen (see DECISIONS.md) · ❌ not done.

## Final-stage gap analysis (21 Nov 2026)
The team's code (session auth, three portals, four priority strategies, two assignment strategies, SLA monitor, escalation, notifications, audit, duplicate/category suggestions, 165 tests) was extended rather than rewritten. Work order: state machine → exceptions → strategies → SMS adapter → escalation chain → intelligence → search/export → frontend → seed data → docs → Docker/CI.

## Traceability matrix (prompt bullet → implementation)
| Prompt requirement | Status | Where |
|---|---|---|
| Java/Spring Boot/Maven, PostgreSQL + Flyway | ✅ (Java 25 / Boot 4) | `backend/`, `db/migration` |
| JWT auth | 🟡 sessions + CSRF instead (D1) | `auth/` |
| BCrypt, role-based authorization, ownership checks | ✅ | `SecurityConfig`, services |
| React + Vite; TypeScript + Tailwind | 🟡 React JS + CSS (D3) | `frontend/` |
| Recharts dashboards | ✅ | `Insights.jsx` |
| STOMP/WebSocket live notifications | ❌ polling/refresh instead | – |
| springdoc / Swagger UI | ❌ endpoints listed in README | README "API" |
| JUnit 5, Mockito, MockMvc, JaCoCo | ✅ (JaCoCo via `-Pcoverage`) | `backend/src/test`, `pom.xml` |
| Dockerfile, docker-compose (Postgres + app + MailHog), GitHub Actions | ✅ (single app image serves UI, D4) | `Dockerfile`, `docker-compose.yml`, `.github/workflows/ci.yml` |
| Layered architecture, controllers without logic, DTOs | ✅ | ARCHITECTURE.md |
| ≥3 roles with different UI | ✅ Student / Technician / Admin, distinct colour, badge, icons and home page each (D21) | `pages/*`, `HomePanels.jsx` |
| Employee adds additional information anytime | ✅ (D23) | `IncidentService.addInformation`, `StudentIncidentActions.jsx` |
| Register / login | ✅ students self-register; staff created by admin (D5) | `AuthController.register`, `App.jsx` |
| TEAM_LEAD role | 🟡 performed by admins; level exists in escalation (D6) | – |
| User inheritance, factory for subtype | ✅ `UserFactory` | `user/` |
| Incident fields incl. INC id, SLA, notes, resolution, feedback, history | ✅ (`INC-1001` label) | `Incident`, `IncidentLabel` |
| Impact/urgency 1–4, affected users count | ✅ three levels; affected users > 100 raises priority one level (D7) | `AffectedUsersRule`, PRIORITY_ALGORITHM.md |
| Admin-configurable categories/services with criticality | ✅ (rename/enable/criticality; new codes need code, D8) | `CatalogService` |
| **State pattern**, one class per state, InvalidStateTransitionException, events on every transition | ✅ | `incident/state/*` |
| AWAITING_INFO with paused SLA | ✅ | `AwaitingInfoState`, `Incident#requestInformation` |
| Reopen on failed verification | ✅ RESOLVED → IN_PROGRESS | `Incident#issueStillExists` |
| Auto-close after 72 h | ✅ | `AutoCloseScheduler` |
| Priority Strategy (weighted + rule-based + 2 more) | ✅ 4 strategies | `priority/*` |
| Priority algorithm doc with worked examples | ✅ | PRIORITY_ALGORITHM.md |
| Assignment strategies: skill, load, round-robin, priority-based, runtime switch | ✅ | `assignment/*` |
| Smart recommendation top 3 with explanations | ✅ | `SmartRecommendation`, `IncidentAssist.jsx` |
| Fallback to next strategy, audited | ✅ | `AssignmentStrategies.assignWithFallback`, history `ASSIGNMENT_FALLBACK` |
| SLA policies per priority, MissingSlaConfigurationException, response/resolution tracking | ✅ | `SlaPolicies`, `Incident#captureSla` |
| SLA policy per service | ✅ per-service SLA factor (D22) | `CatalogEntry.slaFactor`, `SlaPolicies.capture` |
| Background SLA monitor, warning, violations, ON_TRACK/AT_RISK/BREACHED/MET | ✅ (80 % warning, check every 5 s) | `sla/SlaMonitor` |
| Observer notifications; channels in-app / email / SMS (Adapter); channel factory | ✅ | `event/`, `notification/` |
| Audit trail, per-incident timeline, admin filter | ✅ | `audit/`, `AdminManagement.jsx` |
| Search by ≥8 combinable criteria | ✅ 9 criteria in API and UI: ID/keyword, status, priority, category, service, technician, SLA status, date from/to (D12) | `IncidentService.SearchCriteria`, `MyIncidents.jsx` |
| Dashboards: overview, workload, service, trend, SLA %, MTTA/MTTR, categories, feedback | ✅ | `InsightsService.analytics`, `Insights.jsx` |
| Export CSV and PDF | ✅ CSV endpoint; PDF via print stylesheet | `/api/admin/reports/incidents.csv` |
| A Duplicate detection (TF-IDF) | ✅ Jaccard + synonyms for duplicates, TF-IDF for suggestions (D13) | `intelligence/` |
| B Correlation → major incident + admin alert | ✅ | `IncidentCorrelator`, `CorrelationMonitor` |
| C Escalation (Chain of Responsibility) | ✅ | `escalation/EscalationHandler` |
| D Smart recommendation | ✅ | `SmartRecommendation` |
| E SLA risk detection | ✅ | `SlaRiskPredictor` |
| F Auto-categorisation | ✅ category (keyword rules); service suggestion ❌ | `CategorySuggester` |
| G Suggested resolution | ✅ | `ResolutionSuggester` |
| Intelligence behind interfaces; optional LLM adapter | 🟡 `SimilarityModel` interface; no LLM adapter (D14) | – |
| Custom exception hierarchy + consistent JSON error | ✅ all 12 brief situations thrown in real code paths (DESIGN_PATTERNS.md) | `exception/` |
| Frontend friendly errors + network retry | ✅ one retry for reads | `api.js` |
| Bean Validation, no secrets in code | ✅ | DTO records, env vars |
| Rate-limited login, restricted CORS | ❌ / n.a. (same origin) | SECURITY.md |
| Seed data: users, ~60 incidents over 30 days | ✅ (4 students, 4 technicians, 1 admin) | `DemoUserSeeder`, `DemoIncidentSeeder` |
| ≥30 automated tests, TEST_CASES.md | ✅ ≈ 222 automated, 51 documented | TEST_CASES.md |
| Docs 1–10, UML (.puml + PNG), report PDF | ✅ | `docs/` |
| Demo mode | 🟡 via SLA policy minutes + dev profile timers (D16) | DEMO_SCRIPT.md |
| Service dependency graph | ❌ | – |
| Chatbot-style reporting | ❌ | – |

## Verification performed in the final stage
* 136 plain unit tests (after merging the team's automatic-classification commit) compiled and run against framework stand-ins: **all pass**.
* All nine Flyway migrations (incl. the team's V7) applied to PostgreSQL 16; constraints inspected; demo seeder executed against it.
* Frontend: `npm test` (5/5) and `npm run build` pass; Insights page, all three signed-in portals (desktop and 390 px), login and registration (merged with the team's latest commit) rendered in headless Chromium with no console errors and no horizontal scroll at 390 px.
* **Not run here:** the full Maven build (Spring context, MockMvc, Hibernate validation, PostgreSQL integration tests), because Maven Central was unreachable. Run `./mvnw verify` (and with `BITSOLVE_DB_TESTS=true`) before submission.
