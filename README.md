# Flower Action Runtime Samples

Runnable Spring Boot applications for
[`flower-action-runtime`](https://github.com/flowerjvm/flower-action-runtime).

These samples make the controlled-action boundary visible:

```text
ActionProposal
 -> registry
 -> validation
 -> policy
 -> duplicate reservation
 -> optional approval
 -> pre-execution check
 -> execution
 -> ActionRun
 -> audit trail
```

## Samples

| Sample | Execution model | What it shows |
| --- | --- | --- |
| `samples/report-action-control` | Direct and synchronous | Success, approval, denial, validation failure, executor failure, duplicate handling, run records, and audit events through `DefaultActionRuntime`. |
| `samples/report-action-workflow` | Worker-owned and asynchronous | A long-running report Flow waits for a document event, submits a separate controlled action Flow, waits for its result or approval, and then completes the business process. |

The two applications intentionally produce the same controlled-action semantics
through different execution models:

```text
Direct sample
caller -> ActionPipeline -> result

Workflow sample
caller -> submit business Flow -> return
             |
             +-> wait for event
             +-> submit controlled action Flow
             +-> wait for result or approval
             +-> complete business Flow
```

## Prerequisites

- Java 21

The sample applications resolve `flower-action-runtime-core:0.3.2` and
`flower-action-runtime-workflow:0.3.2` directly from Maven Central. A neighboring
runtime source checkout and `mavenLocal()` are not required.

The workflow sample also declares `flower-core:0.1.2` directly because its
application code imports Flower Engine, Flow, and Worker APIs. This both keeps
the direct dependency explicit and verifies Action Runtime `0.3.2` compatibility
with Flower `0.1.2`.

## Run The Direct Sample

```bash
./gradlew :samples:report-action-control:bootRun
```

On Windows:

```powershell
.\gradlew.bat :samples:report-action-control:bootRun
```

Open [http://localhost:8080](http://localhost:8080).

## Run The Workflow Sample

```bash
./gradlew :samples:report-action-workflow:bootRun --args="--server.port=18182"
```

On Windows:

```powershell
.\gradlew.bat :samples:report-action-workflow:bootRun --args="--server.port=18182"
```

Open [http://localhost:18182](http://localhost:18182).

## Build

```bash
./gradlew clean test
```

The sample stores are intentionally in-memory. They are suitable for learning
and local validation, not production persistence.
