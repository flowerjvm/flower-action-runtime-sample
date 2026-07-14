package io.github.parkkevinsb.flower.action.runtime.samples.workflow;

import io.github.parkkevinsb.flower.action.runtime.ActionExecutionStatus;
import io.github.parkkevinsb.flower.action.runtime.ActionProposal;
import io.github.parkkevinsb.flower.action.runtime.ExecutionContext;
import io.github.parkkevinsb.flower.action.runtime.pipeline.ActionExecutionSession;
import io.github.parkkevinsb.flower.action.runtime.run.ActionRun;
import io.github.parkkevinsb.flower.action.runtime.run.ActionRunStatus;
import io.github.parkkevinsb.flower.action.runtime.workflow.WorkflowActionRuntime;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import java.util.Map;

final class ReportPublicationFlowFactory {
    static final String REPORT_WORKER = "report-workflow";
    static final String ACTION_WORKER = "action-workflow";

    private final Engine engine;
    private final WorkflowActionRuntime actionRuntime;
    private final WorkflowRunStore runStore;
    private final WorkflowSampleState state;

    ReportPublicationFlowFactory(
            Engine engine,
            WorkflowActionRuntime actionRuntime,
            WorkflowRunStore runStore,
            WorkflowSampleState state) {
        this.engine = engine;
        this.actionRuntime = actionRuntime;
        this.runStore = runStore;
        this.state = state;
    }

    Flow create(ReportWorkflowCommand command) {
        return Flow.builder("report-publication", command.workflowRunId())
                .executionContext(io.github.parkkevinsb.flower.core.context.ExecutionContext.builder()
                        .tenantId(command.actionContext().tenantId())
                        .userId(command.actionContext().userId())
                        .runId(command.workflowRunId())
                        .traceId(command.actionContext().traceId())
                        .correlationId(command.actionRunId())
                        .build())
                .step("prepare-report", new PrepareReportStep(command.workflowRunId(), state))
                .step("wait-document-upload", new WaitDocumentUploadStep(command.workflowRunId(), state))
                .step("submit-controlled-action", new SubmitControlledActionStep(command, engine, actionRuntime, state))
                .step("wait-controlled-action", new WaitControlledActionStep(command, runStore, state))
                .step("publish-report", new PublishReportStep(command.workflowRunId(), state))
                .build();
    }

    record ReportWorkflowCommand(
            String workflowRunId,
            String actionRunId,
            ActionProposal proposal,
            ExecutionContext actionContext) {
    }

    record DocumentUploaded(String workflowRunId, String uploadedBy) {
    }

    private static final class PrepareReportStep extends Step {
        private final String workflowRunId;
        private final WorkflowSampleState state;

        private PrepareReportStep(String workflowRunId, WorkflowSampleState state) {
            this.workflowRunId = workflowRunId;
            this.state = state;
        }

        @Override
        protected StepResult onTick(StepContext ctx) {
            state.markWorkflowStatus(workflowRunId, "WAITING_DOCUMENT");
            return StepResult.done();
        }
    }

    private static final class WaitDocumentUploadStep extends Step {
        private final String workflowRunId;
        private final WorkflowSampleState state;

        private WaitDocumentUploadStep(String workflowRunId, WorkflowSampleState state) {
            this.workflowRunId = workflowRunId;
            this.state = state;
        }

        @Override
        protected void onEnter(StepContext ctx) {
            state.markWorkflowStatus(workflowRunId, "WAITING_DOCUMENT");
            ctx.subscribe(DocumentUploaded.class, event -> {
                if (workflowRunId.equals(event.workflowRunId())) {
                    ctx.signal("document-uploaded", event);
                }
            });
        }

        @Override
        protected StepResult onTick(StepContext ctx) {
            if (!state.documentUploaded(workflowRunId)) {
                return StepResult.stay();
            }
            ctx.clearSignal("document-uploaded");
            return StepResult.done();
        }
    }

    private static final class SubmitControlledActionStep extends Step {
        private final ReportWorkflowCommand command;
        private final Engine engine;
        private final WorkflowActionRuntime actionRuntime;
        private final WorkflowSampleState state;

        private SubmitControlledActionStep(
                ReportWorkflowCommand command,
                Engine engine,
                WorkflowActionRuntime actionRuntime,
                WorkflowSampleState state) {
            this.command = command;
            this.engine = engine;
            this.actionRuntime = actionRuntime;
            this.state = state;
        }

        @Override
        protected StepResult onTick(StepContext ctx) {
            if (!state.actionSubmitted(command.workflowRunId())) {
                ActionExecutionSession session = actionRuntime.newSession(command.proposal(), command.actionContext());
                engine.submit(ACTION_WORKER, actionRuntime.createFlow(session), DuplicatePolicy.REJECT);
                state.markActionSubmitted(command.workflowRunId());
            }
            return StepResult.done();
        }
    }

    private static final class WaitControlledActionStep extends Step {
        private final ReportWorkflowCommand command;
        private final WorkflowRunStore runStore;
        private final WorkflowSampleState state;

        private WaitControlledActionStep(
                ReportWorkflowCommand command,
                WorkflowRunStore runStore,
                WorkflowSampleState state) {
            this.command = command;
            this.runStore = runStore;
            this.state = state;
        }

        @Override
        protected void onEnter(StepContext ctx) {
            state.markWorkflowStatus(command.workflowRunId(), "WAITING_ACTION");
        }

        @Override
        protected StepResult onTick(StepContext ctx) {
            ActionRun run = runStore.find(command.actionRunId()).orElse(null);
            if (run == null) {
                return StepResult.stay();
            }
            if (run.status() == ActionRunStatus.WAITING_APPROVAL) {
                state.markWorkflowStatus(command.workflowRunId(), "WAITING_APPROVAL");
                return StepResult.stay();
            }
            if (!run.status().isTerminal()) {
                state.markWorkflowStatus(command.workflowRunId(), "WAITING_ACTION");
                return StepResult.stay();
            }
            if (run.result() != null && run.result().status() == ActionExecutionStatus.SUCCEEDED) {
                state.markWorkflowStatus(command.workflowRunId(), "ACTION_SUCCEEDED");
                return StepResult.done();
            }
            String reason = run.result() == null || run.result().message().isBlank()
                    ? "Controlled action ended with " + run.status()
                    : run.result().message();
            state.markWorkflowFailed(command.workflowRunId(), reason);
            return StepResult.fail(new IllegalStateException(reason));
        }
    }

    private static final class PublishReportStep extends Step {
        private final String workflowRunId;
        private final WorkflowSampleState state;

        private PublishReportStep(String workflowRunId, WorkflowSampleState state) {
            this.workflowRunId = workflowRunId;
            this.state = state;
        }

        @Override
        protected StepResult onTick(StepContext ctx) {
            state.markWorkflowStatus(workflowRunId, "COMPLETED");
            state.recordNotification(Map.of(
                    "type", "REPORT_WORKFLOW_COMPLETED",
                    "workflowRunId", workflowRunId));
            return StepResult.done();
        }
    }
}
