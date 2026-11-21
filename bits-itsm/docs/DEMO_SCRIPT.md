# Demo video script (15–20 minutes)

> **File name:** `[GROUP_NO]_OOP_S1_2025-2026_Project` (exactly as required in the brief, e.g. `7_OOP_S1_2025-2026_Project.mp4`). Every member must appear, introduce themselves and explain at least one module.

## Before recording (10 minutes of setup)
1. `docker compose up --build` (or the local run in README). Demo profile seeds 9 users and 60 historical incidents.
2. Sign in as **admin01**. Under **SLA Policies** set **P1 = 2 min response / 6 min resolution** and **P2 = 3 / 10** (demo mode: 1 minute ≈ 1 hour). Policies apply only to incidents classified afterwards.
3. Under **IT Services** make sure Campus Wi-Fi is enabled with **HIGH** criticality.
4. Open three browser profiles (or one normal + two private windows): student01, technician04 (Network), admin01. Open MailHog at http://localhost:8025.
5. `docker compose logs -f app | grep "SIMULATED SMS"` in a terminal, to show SMS.

## 0:00 – 2:00 · Introductions and problem (all members, ~30 s each)
Each member: name, role, the module they own (see TEAM.md). [NAME1] states the problem in one sentence and shows the architecture diagram (`docs/diagrams/architecture.png`).

## 2:00 – 6:00 · Workflow 1 (note the three portals' different colours and home pages: teal student, indigo technician, navy/amber admin): report → auto-categorise + prioritise → assign → notify ([NAME1], [NAME3])
1. On the login page choose **Student Login → Create a student account** and register a new student (institutional e-mail, password of 10+ characters with a letter and a number); show that a Gmail address is rejected. Sign in as the new student (or student01).
2. **student01** → *Report Incident*: "Wi-Fi down in library reading room", description "Wireless keeps disconnecting on the 2nd floor, nobody can connect", service *Campus Wi-Fi*, impact HIGH, urgency HIGH, **people affected 250**. Submit. Point out that the incident is **classified automatically** from its text (service, impact, urgency), and that 250 people raised the priority by one level.
3. Point out the incident status **New** and the student's in-app notification.
4. **admin01** → *All Incidents* → open it. Show the **suggested category** (Network) with its explanation and the **related incidents** panel.
5. *Classify*: show priority **P1** and captured deadlines. [NAME3] explains the multiplicative strategy, 3×3×3 = 27 ≥ 18 (PRIORITY_ALGORITHM.md), and that the strategy can be switched at runtime under *Assignment Strategies*.
6. Show **Smart technician recommendation**: top 3 with reasons. Then *Assignment Strategies* → select **Smart Recommendation**, back to the incident → **Automatically Assign**.
7. Show notifications: technician bell, student timeline, MailHog e-mail "incident received", audit log entries (*Audit Logs*).

## 6:00 – 11:00 · Workflow 2: investigate → info request (SLA paused) → resolve → verify → feedback → close ([NAME2], [NAME4])
1. **technician04** → *Acknowledge* (response SLA met) → *Start Investigation*.
2. Show **Suggested resolutions**: three similar past Wi-Fi fixes (TF-IDF) and their steps.
3. *Request Information*: "Which SSID and which device?" Status becomes **Awaiting Info**, and the detail view shows **Resolution SLA … (clock paused)**. [NAME4] explains the State pattern (`AwaitingInfoState.slaClockRunning() == false`) and that the deadline is extended by the paused time.
4. **student01** sees "The technician needs more information" → reply. (Outside AWAITING_INFO the student can also use **Add information** at any time; the technician is notified.) Status returns to **In Progress**.
5. Wait for the 80 % mark: an **SLA warning** notification appears (P2/P1 demo policy).
6. *Provide Resolution* (summary + steps) → **Resolved**; the student gets an in-app notification and an e-mail in MailHog.
7. **student01** → *Verify* → *Feedback 5/5*. **admin01** → *Close Incident*. Show the incident history timeline (who did what, when) and the admin *Audit Logs* page.
8. [NAME2] tries an illegal action (e.g. student calls close via the API, or close on a NEW incident) and shows the JSON error `{"code":"INVALID_STATE_TRANSITION", …}`.

## 11:00 – 16:00 · Workflow 3: similar reports → duplicates + major incident → P1 escalation ([NAME4], [NAME3])
1. Using student01, student02 and student03, report three Wi-Fi problems within a minute, all with service *Campus Wi-Fi* and category *Network* (duplicate detection compares incidents with the same service and category) ("Wi-Fi not working in hostel", "Wireless down in Ram Bhawan", "Cannot connect to campus Wi-Fi in hostel").
2. **admin01** notifications: **Possible duplicate incident** ("… may duplicate INC-… (xx % similar)").
3. Within 30 s, **Major incident detected** (correlation: ≥ 3 reports on one service in 30 min). Open **Insights & Analytics** → *Major incidents* table; the terminal shows the **simulated SMS** (Adapter).
4. Classify one as **P1** and assign it, but **do not acknowledge**. After the response deadline (2 min) the **escalation chain** fires: *Technician → Team Lead*, then one step later *→ System Administrator*, then *→ IT Manager*. Show *Escalations* and the notifications/SMS. [NAME4] explains Chain of Responsibility (`EscalationHandler`) and that each step is stored once in `scheduled_events`.
5. Show **SLA risk prediction** on the Insights page (HIGH for the unacknowledged P1, with reasons).

## 16:00 – 18:30 · Insights, code and tests ([NAME1], [NAME2], [NAME4])
1. *All Incidents* filter bar: combine **Technician**, **SLA status = At Risk** and a date range (9 criteria). *IT Services*: show the **SLA factor** (e.g. Student Portal ×0.5 for registration week).
2. Insights page: KPIs, trend, per-service, workload, category charts; **Export CSV**; **Export PDF (print)**.
3. Code walk-through (IDE): `Incident.move()` → `IncidentState`; `AssignmentStrategies.assignWithFallback`; `EventBus` observers; `SmsGatewayAdapter`; `BitsolveException` hierarchy.
4. Run tests: `./mvnw verify` (show the green summary) and open `docs/TEST_CASES.md`.

## 18:30 – 20:00 · Wrap-up (all)
Design patterns list (DESIGN_PATTERNS.md), what is not done (DECISIONS.md / SECURITY.md "not implemented"), how it would scale (TECH_STACK.md Q9). Thank the evaluators.
