# Report Action Workflow Sample

This Spring Boot sample makes the difference between the direct and workflow backends visible.

The direct backend runs the governance pipeline inside one `handle(...)` call. The workflow sample instead submits a long-running business Flow and returns immediately. Flower Workers own the remaining execution.

```text
HTTP start request
  -> submit ReportPublicationFlow
  -> return to caller

ReportPublicationFlow                    Controlled Action Flow
  prepare-report
  wait-document-upload  <--- UI event
  submit-controlled-action ------------> record-proposal
  wait-controlled-action                 reserve-duplicate
                                         resolve-action
                                         validate-input
                                         evaluate-policy
                                           \-> optional approval
                                         execute-action
                                         record-result
  publish-report        <--------------- action result
```

Two Flower Workers run independently at a deliberately visible 2000ms interval:

- `report-workflow` owns the long-running business process.
- `action-workflow` owns the controlled action gates.

While `wait-document-upload` returns `StepResult.stay()`, no HTTP request thread is held. A document upload updates domain state and publishes an in-JVM event. The next Worker tick observes the fact and advances the Flow.

The AI scenario also demonstrates a second wait boundary: the action pipeline returns `WAITING_APPROVAL`, while the parent business Flow remains active at `wait-controlled-action`. Approval resumes through `DefaultActionRuntime.resume(...)`, which applies the run-state, approval-id, and duplicate-execution guards before the parent Flow continues.

## Run

From the sample repository root:

```powershell
.\gradlew.bat :samples:report-action-workflow:bootRun
```

Open [http://localhost:8080](http://localhost:8080).

## Try

1. Start `user report`.
2. Notice that the business Flow is active at `wait-document-upload`, while no action Flow exists yet.
3. Click `Upload document event`.
4. Watch the business Worker submit a separate controlled action Flow.
5. Start `AI report`, upload the document, then approve or reject the pending write action.

The page polls `Engine.dump()`, `RunStore`, Flower listener events, and the action audit sink. The domain state remains the source of truth; the Flower event only notifies the waiting Step that a fact may have changed.
