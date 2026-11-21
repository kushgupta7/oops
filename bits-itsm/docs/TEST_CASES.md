# Test cases

**Automated suite:** the team's suite (165 backend tests, last full run 16 Nov 2026 on PostgreSQL) plus **64 new unit tests** added in the final stage (state machine, strategies, intelligence, SMS adapter, exports), giving about 230 automated tests, plus the team's automatic-classification tests. Run `./mvnw verify` for unit + MockMvc tests and `BITSOLVE_DB_TESTS=true ./mvnw verify` to add the PostgreSQL integration tests. Frontend: `npm test` (API client).

"Actual" column:
* **Pass**: re-run after the final changes (136 plain unit tests).
* **Re-run before submission**: Spring context / MockMvc / PostgreSQL tests that passed in the team's 16 Nov run but need a fresh `./mvnw verify` on a machine with Maven access.

| ID | Description | Category | Preconditions | Input | Expected result | Actual | Automated test |
|---|---|---|---|---|---|---|---|
| TC01 | Full lifecycle NEW→…→CLOSED records timestamps | Normal | Classified network incident, available tech | classify, assign, ack, start, resolve, verify, close | Status CLOSED; ack/resolved/verified/closed times set | Pass | `IncidentWorkflowTests.completesOrderedLifecycleAndTimestamps` |
| TC02 | NEW → CLOSED is rejected | Business rule / exception | NEW incident | `close()` | `InvalidStateTransitionException`, status stays NEW | Pass | `IncidentStateMachineTest.newToClosedIsRejectedOnTheEntity` |
| TC03 | All 71 illegal edges rejected, 10 legal edges accepted | Business rule | – | every (from,to) pair | exactly the documented edges succeed; others → 409 code `INVALID_STATE_TRANSITION` | Pass | `IncidentStateMachineTest.everyEdgeNotInTheTableIsRejectedWithInvalidStateTransition` |
| TC04 | Each state allows only its actors | Authorization | – | 12 (from,to,actor) combinations | allowed / `UnauthorizedOperationException` | Pass | `IncidentStateMachineTest.eachStateDecidesWhichActorsMayMoveIt` |
| TC05 | AWAITING_INFO pauses SLA and extends deadline by the pause | Business rule | P1 incident IN_PROGRESS | request info, reply 600 s later | deadline +600 s, pausedSeconds = 600 | Pass | `IncidentStateMachineTest.awaitingInfoPausesTheResolutionClockAndExtendsTheDeadlineByThePause` |
| TC06 | Paused clock does not degrade | Boundary | paused at 10/30 min | evaluate 1 h after original deadline | ON_TRACK | Pass | `…pausedClockIsFrozenSoTheStatusDoesNotDegradeWhileWaiting` |
| TC07 | Zero-length pause | Boundary | IN_PROGRESS | request + reply at same instant | deadline unchanged | Pass | `…zeroLengthPauseLeavesTheDeadlineUnchanged` |
| TC08 | Cannot resolve while awaiting info | Invalid input | AWAITING_INFO | `resolve()` | 409 | Pass | `…cannotResolveOrRequestTwiceWhileAwaitingInfo` |
| TC09 | Unavailable / disabled technician cannot be assigned | Exception | classified incident | assign unavailable, then disabled tech | `TechnicianUnavailableException` | Pass | `IncidentWorkflowTests.rejectsUnavailableAndDisabledTechnicians` |
| TC10 | Blank title / empty resolution rejected | Invalid input | – | title " ", summary " " | `IllegalArgumentException`, status unchanged | Pass | `IncidentWorkflowTests.rejectsBlankContentAndEmptyResolution` |
| TC11 | Feedback only after verification, once, 1–5 | Business rule / boundary | resolved incident | rating 6; second feedback | rejected | Pass | `IncidentWorkflowTests.feedbackRequiresVerificationAndIsSingleSubmission` |
| TC12 | Multiplicative priority thresholds | Normal / boundary | – | score 18, 12, 4, 3 | P1, P2, P3, P4 | Re-run before submission | `PriorityStrategyTests` |
| TC13 | Missing service criticality blocks priority | Exception | unconfigured service | classify | 409, no default MEDIUM | Re-run before submission | `PriorityStrategyTests`, `PostgresManagementTests.unconfiguredServiceIsNeverDefaultedAndRequiresAdministratorConfiguration` |
| TC14 | Skill-based picks lowest-load qualified tech | Normal | 3 techs | network incident | qualified tech with lowest load | Pass | `AssignmentStrategyTests.skillStrategyChoosesLowestQualifiedLoadAndStableId` |
| TC15 | No strategy bypasses mandatory skill / capacity | Business rule | security incident, only full/unqualified techs | SMART + fallback | no assignment | Pass | `NewAssignmentStrategiesTest.noStrategyCanBypassMandatorySkillOrCapacity`, `AssignmentStrategyTests.excludesDisabledBusyUnavailableAndFullAccounts` |
| TC16 | Round-robin rotation | Normal | 2 network + 1 software tech | 4 assignments | 1,2,1,2 | Pass | `NewAssignmentStrategiesTest.roundRobinRotatesThroughEligibleTechniciansInIdOrder` |
| TC17 | Priority-based: P1 to most experienced senior | Normal | techs with 1/5/9 resolved | P1 | veteran | Pass | `…priorityBasedSendsP1ToTheMostExperiencedSeniorTechnician` |
| TC18 | Fallback when no senior is free is reported | Business rule | only a junior tech | P1, PRIORITY_BASED | junior assigned, `fallback=true` | Pass | `…priorityBasedFindsNobodyWhenNoSeniorIsFreeAndTheRegistryFallsBack` |
| TC19 | Smart recommendation ranking + reasons | Normal | 3 techs | rank | specialist > overloaded > generalist, reasons mention skill and service | Pass | `…smartRecommendationRanksBySkillLoadExperienceAndServiceHistoryWithReasons` |
| TC20 | SLA warning at 80 %, breach strictly after deadline | Boundary | P1 incident | t = 79 %, 80 %, deadline, deadline+1 ms | ON_TRACK, AT_RISK, AT_RISK, BREACHED | Pass | `SlaCalculatorTest`, `SlaClockTests` |
| TC21 | SLA monitor publishes each warning/breach once | Normal | – | repeated monitor passes | one event per phase | Pass | `SlaMonitorTest` |
| TC22 | Escalation chain levels and timing | Normal / boundary | unacknowledged P1 | now = deadline, +1 ms, +1 step, +2 steps | none, Team Lead, System Admin, IT Manager (each once) | Pass | `EscalationServiceTest` |
| TC23 | Acknowledged P1 never escalates | Business rule | acknowledged P1 | far past deadline | no escalation | Pass | `EscalationServiceTest` |
| TC24 | Duplicate detection threshold & same service | Normal / boundary | open incidents | similar text, different service | match only on same service ≥ threshold | Pass | `DuplicateDetectorTest` |
| TC25 | Auto-categorisation by keywords | Normal | – | "wifi not connecting" | NETWORK suggested with explanation | Pass | `CategorySuggesterTest` |
| TC26 | Correlation: 3 reports in 30 min → major incident | Normal / boundary | Wi-Fi incidents at 0,10,30,90 min | correlate | one group {1,2,3}; 31 min excluded | Pass | `IntelligentFeaturesTest.threeReportsOnOneServiceWithinThirtyMinutesFormAMajorIncident`, `…windowBoundaryIsInclusiveButOneMinuteLaterIsNot` |
| TC27 | SLA risk rises with elapsed time / workload / MTTR | Normal | P2 incident | early vs late | LOW vs HIGH | Pass | `IntelligentFeaturesTest.slaRiskRisesWithElapsedTimeWorkloadAndHistory` |
| TC28 | Missed deadline = risk 1.0 | Boundary | – | now > deadline | HIGH, score 1.0 | Pass | `…missedDeadlineIsAlwaysHighRiskAndUnassignedCountsAsPressure` |
| TC29 | Suggested resolutions top-3 by TF-IDF | Normal | 5 past fixes | hostel Wi-Fi drop | most similar Wi-Fi fix first, printer fix excluded | Pass | `…suggestedResolutionsReturnTheMostSimilarFixesFirstAndAtMostThree` |
| TC30 | SMS adapter converts vendor checked exception | Exception / third-party | – | non-Indian number | `ExternalServiceException` (502) wrapping `SmsDeliveryException` | Pass | `SmsAdapterTest.adapterConvertsTheVendorsCheckedExceptionIntoExternalServiceException` |
| TC31 | SMS failure never breaks the business action | Exception | failing gateway | SLA breach notification | recorded as not delivered, no exception | Pass | `SmsAdapterTest.providerFailureIsRecordedButNeverThrown` |
| TC32 | Channel factory honours preferences | Normal | in-app + SMS | INCIDENT_CREATED vs SLA_BREACHED | [in-app] vs [in-app, SMS] | Pass | `SmsAdapterTest.factoryReturnsOnlyChannelsThatAcceptTheNotification` |
| TC33 | Observer failure rolls back | Exception | failing observer | publish | exception propagated after other observers ran | Pass | `EventBusAuditTest` |
| TC34 | Notifications never go to the actor; per-role recipients | Normal | – | each event type | correct recipients | Re-run before submission | `NotificationServiceTest` |
| TC35 | CSV export is escaped and formula-safe | Security | – | `=HYPERLINK(1)`, quotes | `'=HYPERLINK(1)`, doubled quotes | Pass | `CsvExportTest.cellsAreQuotedEscapedAndFormulaSafe` |
| TC36 | Login success for each role, session restored | Normal / authorization | seeded users | correct credentials | 200 + role | Re-run before submission | `BitsolveBackendApplicationTests.authenticatesAllRolesAndRestoresSession` |
| TC37 | Wrong / unknown / disabled login | Invalid input / security | – | bad password | same generic 401 message | Re-run before submission | `BitsolveBackendApplicationTests.wrongUnknownAndDisabledAccountsReturnSameGenericError` |
| TC38 | Role boundaries on URLs | Authorization | each role | admin/technician/student URLs | 403 for other roles | Re-run before submission | `BitsolveBackendApplicationTests.enforcesRoleBoundariesAndRequiresAuthentication` |
| TC39 | CSRF required for login/logout | Security | – | POST without token | 403 | Re-run before submission | `BitsolveBackendApplicationTests.requiresCsrfForLoginAndLogout` |
| TC40 | Student cannot assign; technician cannot auto-assign | Authorization | classified incident | POST auto-assign as technician | 403 | Re-run before submission | `PostgresAssignmentTests.automaticStrategiesPersistAndPermissionsHold` |
| TC41 | Student sees only own incidents; other student cannot reply | Authorization | two students | GET / reply | 403 | Re-run before submission | `PostgresIncidentTests.creationUsesSessionIdentityAndRestrictsReads`, `…lifecyclePersistsHistoryAndFeedbackAndRejectsUnauthorizedActions` |
| TC42 | Validation errors on incident payload | Invalid input | – | missing/oversized fields | 400 `VALIDATION_FAILED` | Re-run before submission | `PostgresIncidentTests.validatesPayloadAndRole` |
| TC43 | Concurrent assignments cannot exceed capacity | Boundary / concurrency | tech with 1 free slot | two parallel assigns | exactly one succeeds | Re-run before submission | `PostgresAssignmentTests.concurrentAssignmentsCannotConsumeTheSameLastSlot` |
| TC44 | Migrations preserve data V1→V7 | Persistence | legacy DB | Flyway upgrade | rows and captured deadlines unchanged | Re-run before submission | `PostgresMigrationTests` |
| TC45 | API client: 401 expires session, network failure message | Frontend | – | mocked fetch | session-expired event; friendly message | Pass | `frontend/src/api.test.js` |
| TC46 | All 12 brief error situations have distinct codes and statuses | Exception | – | construct each | 401/403/400/409/409/409/404/409/409/500/502/400, 12 distinct codes | Pass | `ExceptionHandlingTest.everyBriefSituationHasAStableCodeAndHttpStatus` |
| TC47 | Database failure is wrapped without leaking SQL | Exception / security | – | `DataAccessException("…constraint users_email")` | 500 `PERSISTENCE_FAILURE`, message has no SQL detail | Pass | `ExceptionHandlingTest.databaseFailuresAreWrappedWithoutLeakingDetails` |
| TC48 | Invalid incident text names the field | Invalid input | – | description "too short" | `InvalidIncidentException` "Description must contain 20–2000 characters." | Pass | `ExceptionHandlingTest.invalidIncidentFieldsRaiseInvalidIncidentWithTheFieldName` |
| TC51 | UserFactory builds the right subtype | Normal | – | each role | Student / Technician / Administrator; missing student ID rejected | Pass | `UserFactoryTest.factoryCreatesTheSubtypeForEachRole` |
| TC52 | More than 100 affected people raises priority one level | Business rule / boundary | – | (P3,150), (P2,101), (P1,5000), (P4,100), (P3,1) | P2, P1, P1, P4, P3 | Pass | `AffectedUsersAndServiceSlaTest.moreThanOneHundredAffectedUsersRaisesPriorityOneLevel` |
| TC53 | Affected-user count validated and fixed after classification | Invalid input | NEW incident | 0, 100001, then change after classify | `InvalidIncidentException`; `InvalidStateTransitionException` | Pass | `AffectedUsersAndServiceSlaTest.reporterCanStateAffectedUsersOnlyWhileNewAndWithinBounds` |
| TC54 | Service SLA factor scales both targets | Normal | P2 policy 15/120 | factor 0.5 | 8 min / 60 min, deadline = created + 60 min | Pass | `AffectedUsersAndServiceSlaTest.serviceFactorScalesBothSlaTargets` |
| TC55 | Factor bounds and 1-minute floor | Boundary | – | 0.1, 5.0; P1 1/2 min × 0.25 | rejected; response stays 1 min | Pass | `…serviceFactorOutsideRangeIsRejected`, `…defaultFactorKeepsPolicyTargetsAndTinyTargetsStayAtLeastOneMinute` |

Postgres* tests run only with `BITSOLVE_DB_TESTS=true` against a disposable PostgreSQL database.

## Coverage report

`./mvnw -Pcoverage verify` writes the JaCoCo HTML report to `backend/target/site/jacoco/index.html`.
