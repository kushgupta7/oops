# Team and responsibilities

> **Fill in before submission:** replace `[GROUP_NO]` and the names below, and adjust module ownership to match what each member actually built. Every member must be able to explain the whole system.

**Group No:** [GROUP_NO]

| Member | Role | Owns (packages / files) | Demo segment |
|---|---|---|---|
| [NAME1] | UI / UX (frontend) | `frontend/src/pages/*`, `MyIncidents.jsx`, `ReportIncident.jsx`, `Insights.jsx`, styling, role-specific navigation | Portals tour, W1 reporting screen |
| [NAME2] | Backend / APIs + security | `auth/`, `incident/IncidentService` + controllers, `api/`, `exception/`, DTOs, SecurityConfig | Login, authorization, error handling, API docs |
| [NAME3] | Domain model / OOP + persistence | `incident/Incident`, `incident/state/*`, `user/*`, `priority/*`, Flyway migrations, ER design | State pattern, priority strategies, database |
| [NAME4] (Person 4) | Services, intelligent features, QA / DevOps | `sla/`, `escalation/`, `notification/` (+ `sms/`), `audit/`, `event/`, `intelligence/`, `assignment/*Strategy`, tests, Docker/CI | SLA, escalation chain, notifications, correlation, recommendations, test suite |
