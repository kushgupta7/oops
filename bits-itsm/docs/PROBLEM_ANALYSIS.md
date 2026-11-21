# Problem analysis

## Problem
When a campus IT service fails (Wi-Fi in a hostel block, the ERP student portal during registration, a lab licence server), students today report it through e-mails, phone calls and WhatsApp messages to individual staff. Reports are lost or duplicated, nobody knows who owns an issue, urgent problems wait behind trivial ones, there is no record of how long anything took, and the same fix is rediscovered again and again.

## Stakeholders
| Stakeholder | Interest |
|---|---|
| Students (reporters) | Report quickly, know the status, get it fixed, confirm the fix |
| Technicians | A clear, fair queue; context to investigate; past fixes; ability to ask the reporter |
| Administrators / IT manager (CCIT) | Prioritise by business impact, assign sensibly, meet SLAs, see trends, audit decisions |
| Maintenance Division / CCIT leadership | Service-level evidence, workload balance, recurring-problem detection |
| Course evaluators | Clean object-oriented design and working MVP |

## Existing challenges
1. **No single intake**: duplicates and lost reports.
2. **No prioritisation**: first-come-first-served regardless of impact.
3. **Ad-hoc assignment**: overload of the most visible technicians, skills mismatches.
4. **No SLA tracking or escalation**: critical outages can sit unacknowledged.
5. **No accountability**: who changed what, when, is not recorded.
6. **No knowledge reuse**: resolutions live in people's heads.
7. **No visibility**: no data on volume, trends or service health.

## Proposed solution
**BITSolve**, a web-based Intelligent IT Service Management system with three role-specific portals:
* A **controlled lifecycle** (State pattern) with SLA capture and a paused clock while waiting for the reporter.
* **Configurable prioritisation** (four runtime-switchable strategies) using impact, urgency and service criticality.
* **Assignment strategies** (skill, load, round-robin, priority/seniority, smart weighted recommendation) with audited fallback, plus transfer/collaboration approval workflows.
* **Background SLA monitoring**, warnings at 80 %, breaches, and a **Chain-of-Responsibility escalation** for unacknowledged P1s.
* **Event-driven notifications** (in-app, e-mail outbox, simulated SMS) and an **immutable audit trail** (Observer).
* **Intelligence**: auto-categorisation, duplicate detection, major-incident correlation, SLA-risk prediction, smart technician recommendation and suggested resolutions.
* **Analytics**: KPIs, trends, service analysis, workload, CSV/PDF export.

## Assumptions
* All users have institutional `@hyderabad.bits-pilani.ac.in` e-mail addresses. Students may self-register (student role only); technician and administrator accounts are created by an administrator.
* Administrators also perform the team-lead role (approvals, reassignment).
* One campus deployment; a single application instance with PostgreSQL is sufficient for the expected volume (hundreds of incidents per month).
* Students rate impact and urgency on a three-level scale; business criticality is owned by IT per service.
* SMS delivery is simulated (no paid gateway); e-mail goes to a real SMTP server or MailHog in the demo.
* Timestamps are stored in UTC and displayed in the browser's time zone; analytics days use Asia/Kolkata.
