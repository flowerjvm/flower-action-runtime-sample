# Flower Action Runtime Samples

Runnable Spring Boot applications for
[`flower-action-runtime`](https://github.com/flowerjvm/flower-action-runtime).

These samples make the controlled-action boundary visible:

```text
ActionProposal
 -> registry
 -> validation
 -> policy
 -> optional approval
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
- The runtime repository checked out next to this repository

Until the runtime artifacts are published, the Gradle helper modules compile
the public sources from the neighboring checkout:

```text
git/
  flower-action-runtime/
  flower-action-runtime-sample/
```

Clone both repositories into that shape:

```bash
git clone https://github.com/flowerjvm/flower-action-runtime.git
git clone https://github.com/flowerjvm/flower-action-runtime-sample.git
```

The helper modules are local development bridges only. The sample applications
use the public `flower-action-runtime-core` and
`flower-action-runtime-workflow` APIs.

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
