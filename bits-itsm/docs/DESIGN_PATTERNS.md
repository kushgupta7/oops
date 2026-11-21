# Design patterns in BITSolve

Seven Gang-of-Four / enterprise patterns are implemented in production code (not just tests). Each entry gives the problem, the participating classes, where the code lives and why the pattern was chosen. Diagrams: `uml/class-patterns-1.png`, `uml/class-patterns-2.png`, `uml/state.png`.

Paths are relative to `backend/src/main/java/com/bits/itsm/`.

## 1. State: incident lifecycle

| | |
|---|---|
| **Problem** | An incident moves through nine states. Each state allows different next states, different actors, and behaves differently (SLA clock running, counting toward workload). Spreading `if (status == …)` checks across services is fragile and lets illegal jumps such as NEW → CLOSED slip through. |
| **Participants** | `IncidentState` (interface), `AbstractIncidentState` (shared table logic), nine concrete states (`NewState` … `ClosedState`, including `AwaitingInfoState`), `IncidentLifecycle` (registry/flyweight), context = `Incident` |
| **Code** | `incident/state/*`, `incident/Incident.java#move` |
| **How it works** | `Incident.move(next)` calls `state().requireTransition(next)`. Each state declares its edges and permitted actors in its constructor with `allow(target, actors…)`, stored in an `EnumMap`. Illegal edges throw `InvalidStateTransitionException` (409). Legal edges by the wrong actor throw `UnauthorizedOperationException` (403). `AwaitingInfoState.slaClockRunning()` returns false, which is how the SLA pause is expressed. |
| **Why** | Each state's rules are in one small class, the transition table is testable exhaustively (`IncidentStateMachineTest` checks all 81 from/to pairs), and the `/api/incidents/lifecycle` endpoint and UI table are generated from the same objects, so documentation cannot drift from code. |

## 2. Strategy: priority calculation

| | |
|---|---|
| **Problem** | Campus IT wants to experiment with different prioritisation policies without redeploying. |
| **Participants** | `PriorityStrategy` + `MultiplicativePriorityStrategy`, `WeightedPriorityStrategy`, `MatrixPriorityStrategy`, `RuleBasedPriorityStrategy`; context `PriorityService` |
| **Code** | `priority/*` |
| **Why** | The admin switches strategy at runtime (persisted in `priority_settings`). Existing incidents keep their captured priority. See `PRIORITY_ALGORITHM.md`. |

## 3. Strategy + Factory: technician assignment

| | |
|---|---|
| **Problem** | Different assignment policies (skills, load, fairness, seniority, weighted ranking) must be switchable at runtime with no redesign, and a failure of one policy must not leave the incident unassigned if another could succeed. |
| **Participants** | `AssignmentStrategy` + `SkillBasedAssignment`, `LoadBasedAssignment`, `RoundRobinAssignment`, `PriorityBasedAssignment`, `SmartRecommendation`; factory/registry `AssignmentStrategies` (`EnumMap<Mode, AssignmentStrategy>`); context `AssignmentService` |
| **Code** | `assignment/*` |
| **How it works** | `AssignmentStrategies.assignWithFallback(selected, …)` runs the selected strategy, then the others in a fixed order, and returns an `Outcome(technician, used, fallback)`. A fallback is written to the incident history as `ASSIGNMENT_FALLBACK`. Every strategy goes through `Candidate.eligible()`, so none can bypass a mandatory skill, availability or capacity. |
| **Why** | Open/Closed: a sixth strategy is one new class plus one enum constant. Strategy objects are created once so stateful ones (round-robin cursors) keep state. `PriorityBasedAssignment` uses composition: it delegates routine work to a `LoadBasedAssignment` it holds. |

## 4. Observer: notifications and audit

| | |
|---|---|
| **Problem** | Many things must happen when an incident changes (audit, in-app notification, e-mail, SMS) and the business services should not know about any of them. |
| **Participants** | Subject `EventBus`; observer interface `IncidentObserver`; observers `AuditLogger`, `NotificationService`, `EmailChannel`; event `IncidentEvent` (immutable record); helper `IncidentEventPublisher` |
| **Code** | `event/*`, `audit/AuditLogger.java`, `notification/*` |
| **Why** | Hand-written rather than Spring events so the pattern is visible. Spring injects every `IncidentObserver` bean into `EventBus`, so a new observer needs no change elsewhere. Observers run inside the business transaction: if auditing fails, the change rolls back, so there is never an un-audited change. |

## 5. Factory: users, notifications, channels

* **`NotificationFactory.create(event, recipient)`** builds the right title and wording per `EventType`.
* **`NotificationChannelFactory.channelsFor(notification)`** returns the channels whose `accepts()` preference matches. For example, SMS is used only for SLA breaches, escalations and major incidents (configurable `itsm.sms.events`).
* **`UserFactory.create(role, …)`** is the single place that instantiates `Student`, `Technician` or `Administrator`; admin account creation uses it. Clients cannot set a role directly (`User.getRole()` is abstract).

## 6. Adapter: SMS gateway (and the persistence snapshot)

| | |
|---|---|
| **Problem** | A third-party SMS SDK has its own method names, number format, response object and a checked exception. |
| **Participants** | Target `SmsGateway`; Adapter `SmsGatewayAdapter`; Adaptee `ThirdPartySmsApi` (simulated vendor; logs instead of sending); client `SmsChannel` |
| **Code** | `notification/sms/*` |
| **How it works** | The adapter converts "+91 90123 45678" to MSISDN "919012345678", truncates to one 160-character segment, adds the sender id, and translates the checked `SmsDeliveryException` into the unchecked `ExternalServiceException`. |
| **Why** | Changing SMS vendor (Twilio, MSG91) means writing one new adapter. A second adapter, `RepositoryIncidentLookup`, adapts JPA entities to immutable `IncidentSnapshot`s for the SLA and intelligence code. |

## 7. Chain of Responsibility: automatic escalation

| | |
|---|---|
| **Problem** | An unacknowledged P1 must climb Technician → Team Lead → System Administrator → IT Manager, each level with its own timer, without one long if/else. |
| **Participants** | `EscalationHandler` (abstract handler with `next`), the levels built by `EscalationHandler.technician()` and `EscalationHandler.timed(level, delay)`, client `EscalationService` |
| **Code** | `escalation/*` |
| **How it works** | `handle()` asks the handler whether its timer has expired. If not, the chain stops. If so, it escalates (persisted once via `ScheduledEvents.publishOnce`) and passes the request on. Team Lead fires at the response deadline, System Administrator one step later, IT Manager two steps later. |
| **Why** | Adding a level (e.g. Dean of Student Welfare) is one more link. Each escalation is idempotent across restarts. |

## 8. Repository

Spring Data JPA interfaces (`IncidentRepository`, `UserRepository`, `SlaPolicyRepository` …) hide SQL behind collection-like methods, including a pessimistic-lock finder (`findForUpdate`) that serialises concurrent lifecycle actions on one incident.

## OOP concepts: where to point the examiner

| Concept | Where |
|---|---|
| Encapsulation | `Incident`: private fields, no status/priority/deadline setters, invariants enforced in `classify`, `captureSla`, `resolve` (text length), `giveFeedback` (1–5, once). `Technician.configureAssignment` validates skills and capacity. |
| Abstraction | `User` (abstract `getRole()`), `IncidentState`, `AssignmentStrategy`, `SimilarityModel`, `BitsolveException` |
| Inheritance (is-a only) | `User` → `Student` / `Technician` / `Administrator`; `AbstractIncidentState` → 9 states; `AssignmentRequest` → `TransferRequest` / `CollaborationRequest`; `BitsolveException` → 12 subclasses |
| Polymorphism | `state().requireTransition()`, `strategy.assign()`, `observer.onEvent()`, `channel.accepts()`, one `@ExceptionHandler` for every `BitsolveException` through `code()` / `httpStatus()` |
| Interfaces | 10+ (see the class diagrams) |
| Composition | `Incident` owns notes, history, feedback, collaborators (cascade persist, no independent life); `PriorityBasedAssignment` contains a `LoadBasedAssignment` |
| Association | `Incident` → reporter `Student`, → assigned `Technician` |
| Aggregation | Audit entries reference incidents by id and outlive them; `EventBus` aggregates observers |
| Exception handling | Custom hierarchy (`exception/`), checked `SmsDeliveryException` at the external boundary, `GlobalApiErrors` / `IncidentApiErrors` produce `{timestamp, status, code, message, path}`. See the table below. |

## Exception handling: the brief's twelve situations

| Brief situation | Exception (HTTP) | Thrown where |
|---|---|---|
| Invalid login | `InvalidLoginException` (401) | `AuthController` on any authentication failure (same message for wrong, unknown, disabled) |
| Unauthorized operation | `UnauthorizedOperationException` (403) | State transition by the wrong role; insights for the wrong role |
| Invalid incident | `InvalidIncidentException` (400) | `Incident` title/description/resolution length rules |
| Invalid state transition | `InvalidStateTransitionException` (409) | Every `Incident` status change via `IncidentState` |
| Technician unavailable | `TechnicianUnavailableException` (409) | `Incident.assign/reassign`; automatic assignment finds nobody |
| Workload exceeded | `WorkloadExceededException` (409) | Manual assignment / transfer to a technician at capacity |
| Service not found | `ServiceNotFoundException` (404) | `CatalogService.get` for an unknown IT service |
| Missing SLA configuration | `MissingSlaConfigurationException` (409) | `SlaPolicies.capture` at classification |
| Duplicate incident | `DuplicateIncidentException` (409) | Same report resubmitted (idempotency key); similar reports raise a *warning* event instead |
| Database failure | `PersistenceException` (500, generic message) | `GlobalApiErrors` wraps any Spring `DataAccessException` |
| Network failure | `ExternalServiceException` (502) + frontend retry | SMS adapter; `api.js` retries reads once and shows a friendly message |
| Invalid user input | `ValidationException` (400) + Bean Validation | Search dates, DTO constraints |
| Collections | `EnumMap` (state transition tables, strategy registry, per-service correlation), `EnumSet` (actors per edge, SMS event preferences), `ConcurrentHashMap` + `AtomicInteger` (round-robin cursors), `ConcurrentLinkedDeque` (bounded SMS history), `LinkedHashMap` (30-day trend in calendar order, ordered recipients), `TreeMap` (sorted chart categories) |
