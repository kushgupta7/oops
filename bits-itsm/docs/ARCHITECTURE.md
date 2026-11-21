# System architecture

![Architecture](diagrams/architecture.png)

```mermaid
flowchart TB
  subgraph Browser["Browser — React 19 + Vite + Recharts"]
    SP[Student portal]
    TP[Technician portal]
    AP[Admin portal + Insights]
  end
  subgraph App["Spring Boot 4 application (one JAR serves UI + /api)"]
    SEC["Security filter chain<br/>session + CSRF + role URLs"]
    CTRL["REST controllers<br/>(HTTP ↔ DTO only)"]
    subgraph Services["Service layer"]
      IS[IncidentService]
      AS[AssignmentService]
      PS[PriorityService]
      INS[InsightsService]
    end
    subgraph Domain["Domain model"]
      INC["Incident + IncidentState<br/>(State pattern)"]
      STR["Priority / Assignment<br/>Strategies"]
    end
    subgraph Intel["intelligence"]
      DUP[DuplicateDetector]
      COR[IncidentCorrelator]
      RISK[SlaRiskPredictor]
      SUG["ResolutionSuggester<br/>TF-IDF"]
      CAT[CategorySuggester]
    end
    subgraph Bg["@Scheduled background jobs"]
      SLA[SlaMonitor]
      ESC["EscalationService<br/>Chain of Responsibility"]
      CM[CorrelationMonitor]
      AC[AutoCloseScheduler]
    end
    BUS(("EventBus<br/>Observer subject"))
    AUD[AuditLogger]
    NOT["NotificationService<br/>+ ChannelFactory"]
    MAIL["EmailChannel<br/>outbox + retry"]
    SMS["SmsChannel → SmsGatewayAdapter<br/>→ ThirdPartySmsApi"]
    REPO["Spring Data repositories<br/>+ JdbcTemplate"]
  end
  DB[("PostgreSQL 16<br/>Flyway V1–V9")]
  SMTP[["SMTP / MailHog"]]
  Browser -->|"JSON over HTTPS, cookie session"| SEC --> CTRL --> Services
  Services --> Domain
  Services --> Intel
  Services --> BUS
  Bg --> BUS
  BUS --> AUD & NOT & MAIL
  NOT --> SMS
  MAIL --> SMTP
  Services --> REPO
  Bg --> REPO
  AUD --> REPO
  REPO --> DB
```

## Layers and responsibilities
| Layer | Package(s) | Rule |
|---|---|---|
| Presentation | `frontend/src` | Role-specific navigation; no business rules; friendly errors |
| API | `*Controller`, `api/`, `exception/GlobalApiErrors`, `incident/IncidentApiErrors` | Maps HTTP ↔ DTO records; **no business logic**; entities never returned directly (`IncidentData.View` etc.) |
| Service | `incident/IncidentService`, `assignment/AssignmentService`, `priority/PriorityService`, `api/InsightsService` | Transactions, role + ownership checks, orchestration |
| Domain | `incident/` (+ `state/`), `user/`, `assignment/*Strategy`, `priority/*Strategy`, `escalation/` | Invariants, state machine, algorithms. Plain Java, unit-testable |
| Intelligence | `intelligence/` | Pure algorithms behind `SimilarityModel` and plain classes |
| Events | `event/`, `audit/`, `notification/` | Observer: audit, notifications, e-mail, SMS |
| Persistence | `*Repository`, `sla/ScheduledEvents`, Flyway | JPA + pessimistic locks; SQL only where idempotency needs `on conflict` |

## Key interactions
1. **Request path**: Browser → security filter (session, CSRF, URL role) → controller → service (role + ownership) → domain method (state check) → repository → commit. Events are published inside the transaction, so audit/notification rows commit atomically with the change.
2. **Background path**: `SlaMonitor`, `CorrelationMonitor`, `AutoCloseScheduler` read immutable `IncidentSnapshot`s and claim each event key in `scheduled_events` before publishing, so restarts never repeat alerts.
3. **E-mail**: `EmailChannel` queues reporter e-mails in the same transaction; a separate worker sends committed rows with bounded retries.
