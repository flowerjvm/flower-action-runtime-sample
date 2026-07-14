# Report Action Control Sample

This Spring Boot sample shows the direct `DefaultActionRuntime` boundary.

Every UI, REST, batch, AI, or MCP request becomes an `ActionProposal`. The caller then waits for one synchronous Java call to pass through the shared governance pipeline:

```text
caller
  -> record-proposal
  -> reserve-duplicate
  -> resolve-action
  -> validate-input
  -> evaluate-policy
  -> execute-action
  -> record-result
  -> ActionExecutionResult
```

There is no Flower Engine, Worker, or Flow in this sample. The caller thread owns execution until the runtime returns `SUCCEEDED`, `DENIED`, `VALIDATION_FAILED`, `FAILED`, or `PENDING_APPROVAL`.

Use the separate `report-action-workflow` sample when the surrounding business process must outlive the request, wait for external events, or expose live Flow/Step position through Flower.

## Run

From the sample repository root:

```powershell
.\gradlew.bat :samples:report-action-control:bootRun
```

Open [http://localhost:8080](http://localhost:8080).

## Scenarios

- User creates a report: every gate passes and the action succeeds.
- AI proposes report creation: policy returns `PENDING_APPROVAL`; approve or reject it from the inbox.
- Invalid input: validation stops the pipeline before policy and execution.
- Executor failure: validation and policy pass, then the domain executor fails.
- Duplicate request: the idempotency policy returns the existing result.
- Critical delete: default policy requires approval.
- Unknown action: registry resolution denies the proposal.

The screen keeps the action catalog and run history available, but the selected run pipeline is the primary view. Its fixed seven-stage rail shows exactly where the direct call passed, waited, stopped, or failed.
