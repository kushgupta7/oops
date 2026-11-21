# Priority algorithm

Priority is always calculated in Java (never trusted from the browser) when an administrator **classifies** a NEW incident. Inputs:

* **Impact** and **Urgency**: chosen by the student: LOW / MEDIUM / HIGH.
* **Business criticality**: read from the selected IT service's database record (LOW / MEDIUM / HIGH, set by an admin under *IT Services*). A service with no criticality configured cannot receive incidents.

The administrator picks one of four strategies at runtime (*Assignment Strategies → Priority Strategy*). The choice is persisted in `priority_settings`, audited as `PRIORITY_CONFIGURATION_CHANGED`, and applies to **future** classifications only: captured priorities and SLA deadlines never change retroactively. The resulting priority selects the SLA policy (`sla_policies`) whose targets are captured on the incident.

## 1. Multiplicative (default)

`score = impact × urgency × criticality`, with LOW/MEDIUM/HIGH = 1/2/3, so the score ranges from 1 to 27.

| Threshold (inclusive, admin-configurable) | Priority |
|---|---|
| score ≥ 18 | **P1 Critical** |
| 12 ≤ score < 18 | **P2 High** |
| 4 ≤ score < 12 | **P3 Medium** |
| score < 4 | **P4 Low** |

**Rationale.** Multiplication means one LOW factor pulls the score down sharply: a high-impact problem on a low-criticality service is not a P1. 18 is the smallest product that needs at least two HIGH factors and one MEDIUM (3·3·2).

| Example | I | U | C | Score | Priority |
|---|---|---|---|---|---|
| Campus Wi-Fi down for a hostel block | HIGH | HIGH | HIGH | 27 | P1 |
| Fee payment fails (portal criticality MEDIUM) | HIGH | HIGH | MEDIUM | 18 | P1 |
| Library e-journals unreachable | MEDIUM | MEDIUM | HIGH | 12 | P2 |
| One lab PC will not boot | HIGH | LOW | MEDIUM | 6 | P3 |
| Email signature formatting | LOW | LOW | MEDIUM | 2 | P4 |

## 2. Weighted

Levels are normalised to 0 / 0.5 / 1, then `score = 100 × (wᵢ·impact + wᵤ·urgency + w꜀·criticality)`. Default weights are 0.4 / 0.4 / 0.2 (must be non-negative and sum to 1). Thresholds: P1 ≥ 75, P2 ≥ 50, P3 ≥ 25, otherwise P4.

| I | U | C | Score | Priority |
|---|---|---|---|---|
| HIGH | HIGH | LOW | 100·(0.4+0.4+0) = 80 | P1 |
| MEDIUM | HIGH | MEDIUM | 100·(0.2+0.4+0.1) = 70 | P2 |
| LOW | MEDIUM | HIGH | 100·(0+0.2+0.2) = 40 | P3 |
| LOW | LOW | MEDIUM | 100·(0.1) = 10 | P4 |

## 3. Priority matrix

A 3×3 table (rows = impact, columns = urgency, default below). A HIGH-criticality service promotes the result by `critical_boost` levels (default 1, range 0–3), capped at P1.

| Impact \ Urgency | LOW | MEDIUM | HIGH |
|---|---|---|---|
| **LOW** | P4 | P3 | P2 |
| **MEDIUM** | P3 | P2 | P1 |
| **HIGH** | P2 | P1 | P1 |

Example: MEDIUM/MEDIUM gives P2, and on a HIGH-criticality service it becomes **P1**.

## 4. Rule-based

An ordered list of predefined conditions; the first match wins, otherwise the fallback applies. Default:

1. `ALL_HIGH` → P1
2. `HIGH_IMPACT_AND_URGENCY` → P2
3. `CRITICAL_SERVICE` → P2
4. fallback → P3

Admins can reorder or remove rules (`HIGH_IMPACT`, `HIGH_URGENCY` are also available) and choose each result.

## Number of affected users (applies to every strategy)

The student can optionally answer "How many people are affected?" (1–100 000) when reporting. After the active strategy has produced a priority, `AffectedUsersRule` applies one adjustment:

| Affected users | Adjustment |
|---|---|
| not stated, or 1–100 | none |
| more than 100 | priority raised one level (P4→P3, P3→P2, P2→P1; P1 stays P1) |

**Rationale.** Impact describes *how badly* each person is affected; the user count describes *how many*. A medium-impact problem that hits a whole hostel block deserves faster handling than the same problem for one student. Raising by one level (rather than multiplying the score) works identically for all four strategies, including the matrix and rule-based ones that have no numeric score. The raise is recorded in the incident history ("Priority raised from P3 to P2: 250 people affected").

**Worked example.** "Student portal slow during registration": impact MEDIUM, urgency MEDIUM, Student Portal criticality MEDIUM → multiplicative 2·2·2 = 8 → **P3**. The student enters 400 affected people → **P2** (15 min response, 2 h resolution).

## SLA targets per priority (defaults, editable under *SLA Policies*) and per service

| Priority | Response | Resolution |
|---|---|---|
| P1 | 5 min | 30 min |
| P2 | 15 min | 2 h |
| P3 | 1 h | 8 h |
| P4 | 4 h | 24 h |

**Different services, different SLAs.** Each IT service has an *SLA factor* (default ×1, range ×0.25–×4, set under *IT Services*). At classification the priority targets are multiplied by the service's factor and rounded (minimum 1 minute), and the factor is stored on the incident. Example: during registration week the admin sets Student Portal to ×0.5, so a P2 portal incident gets 8 min response (7.5 rounded) and 1 h resolution instead of 15 min / 2 h. Incidents already classified keep their captured targets.

If no policy exists for a priority, classification fails with `MissingSlaConfigurationException` (HTTP 409) and the incident stays NEW.

## Differences from the original prompt (see DECISIONS.md)

* Impact/urgency use three levels (LOW/MEDIUM/HIGH) instead of 1–4. Students found a 4-point scale hard to apply consistently, and three levels keep the matrix readable.
* The affected-users factor is a one-level raise above 100 people rather than the prompt's ×1.0/×1.2/×1.5 score multiplier, so that it works for all four strategies (two of which have no numeric score).
* "Security category + high impact ⇒ P1" and "Payment service down ⇒ P1" are expressed with the configurable rules (`HIGH_IMPACT_AND_URGENCY`, `CRITICAL_SERVICE`) together with service criticality, rather than hard-coded category names.
