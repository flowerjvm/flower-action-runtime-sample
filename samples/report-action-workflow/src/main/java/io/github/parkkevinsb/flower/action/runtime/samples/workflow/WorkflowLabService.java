package io.github.parkkevinsb.flower.action.runtime.samples.workflow;

import io.github.parkkevinsb.flower.action.runtime.ActionExecutionResult;
import io.github.parkkevinsb.flower.action.runtime.ActionOrigin;
import io.github.parkkevinsb.flower.action.runtime.ActionProposal;
import io.github.parkkevinsb.flower.action.runtime.DefaultActionRuntime;
import io.github.parkkevinsb.flower.action.runtime.ExecutionContext;
import io.github.parkkevinsb.flower.action.runtime.approval.ApprovalDecision;
import io.github.parkkevinsb.flower.action.runtime.audit.AuditEvent;
import io.github.parkkevinsb.flower.action.runtime.run.ActionRun;
import io.github.parkkevinsb.flower.action.runtime.run.ActionRunStatus;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.engine.EngineDump;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
final class WorkflowLabService {
    private static final String TENANT_ID = "demo";
    private static final List<String> BUSINESS_STEP_IDS = List.of(
            "prepare-report",
            "wait-document-upload",
            "submit-controlled-action",
            "wait-controlled-action",
            "publish-report");
    private static final List<String> ACTION_STAGE_IDS = List.of(
            "record-proposal",
            "reserve-duplicate",
            "resolve-action",
            "validate-input",
            "evaluate-policy",
            "execute-action",
            "record-result");

    private final ReportPublicationFlowFactory flowFactory;
    private final DefaultActionRuntime resumableRuntime;
    private final WorkflowRunStore runStore;
    private final WorkflowAuditSink auditSink;
    private final WorkflowFlowEvents flowEvents;
    private final WorkflowSampleState sampleState;
    private final Engine engine;
    private final ConcurrentMap<String, LabRun> labs = new ConcurrentHashMap<>();
    private volatile String currentWorkflowRunId;

    WorkflowLabService(
            io.github.parkkevinsb.flower.action.runtime.workflow.WorkflowActionRuntime workflowRuntime,
            DefaultActionRuntime resumableRuntime,
            WorkflowRunStore runStore,
            WorkflowAuditSink auditSink,
            WorkflowFlowEvents flowEvents,
            WorkflowSampleState sampleState,
            Engine engine) {
        this.flowFactory = new ReportPublicationFlowFactory(engine, workflowRuntime, runStore, sampleState);
        this.resumableRuntime = resumableRuntime;
        this.runStore = runStore;
        this.auditSink = auditSink;
        this.flowEvents = flowEvents;
        this.sampleState = sampleState;
        this.engine = engine;
    }

    synchronized WorkflowView start(String scenarioName) {
        Scenario scenario = scenario(scenarioName);
        String workflowRunId = "workflow-" + UUID.randomUUID();
        String actionRunId = "action-" + UUID.randomUUID();
        ActionProposal proposal = new ActionProposal(
                null,
                scenario.actionId(),
                scenario.origin(),
                scenario.origin().name().toLowerCase() + "-sample",
                scenario.reason(),
                scenario.origin() == ActionOrigin.AI_PLANNER ? 0.82d : 1.0d,
                scenario.input(),
                null,
                Map.of("sample", "report-action-workflow", "scenario", scenario.name()));
        ExecutionContext actionContext = new ExecutionContext(
                TENANT_ID,
                proposal.requesterId(),
                actionRunId,
                workflowRunId + "-trace",
                Map.of(
                        "workflowRunId", workflowRunId,
                        "sample", "report-action-workflow",
                        "scenario", scenario.name()));
        ReportPublicationFlowFactory.ReportWorkflowCommand command =
                new ReportPublicationFlowFactory.ReportWorkflowCommand(
                        workflowRunId,
                        actionRunId,
                        proposal,
                        actionContext);

        sampleState.startWorkflow(workflowRunId, actionRunId, scenario.name());
        LabRun lab = new LabRun(workflowRunId, actionRunId, scenario, proposal);
        labs.put(workflowRunId, lab);
        currentWorkflowRunId = workflowRunId;

        Flow businessFlow = flowFactory.create(command);
        engine.submit(ReportPublicationFlowFactory.REPORT_WORKER, businessFlow, DuplicatePolicy.REJECT);
        return view(lab);
    }

    synchronized WorkflowView uploadCurrentDocument() {
        LabRun lab = currentLab();
        sampleState.markDocumentUploaded(lab.workflowRunId());
        engine.eventBus().publish(new ReportPublicationFlowFactory.DocumentUploaded(
                lab.workflowRunId(),
                "document-user"));
        return view(lab);
    }

    synchronized WorkflowView approveCurrent() {
        LabRun lab = currentLab();
        ActionRun run = runStore.find(lab.actionRunId()).orElse(null);
        if (run != null
                && run.status() == ActionRunStatus.WAITING_APPROVAL
                && activeSnapshot(lab.actionRunId()).isEmpty()
                && !run.approvalId().isBlank()) {
            resumableRuntime.resume(
                    lab.actionRunId(),
                    ApprovalDecision.approved(run.approvalId(), "manager-sample"));
        }
        return view(lab);
    }

    synchronized WorkflowView rejectCurrent() {
        LabRun lab = currentLab();
        ActionRun run = runStore.find(lab.actionRunId()).orElse(null);
        if (run != null
                && run.status() == ActionRunStatus.WAITING_APPROVAL
                && activeSnapshot(lab.actionRunId()).isEmpty()
                && !run.approvalId().isBlank()) {
            resumableRuntime.resume(
                    lab.actionRunId(),
                    ApprovalDecision.rejected(
                            run.approvalId(),
                            "manager-sample",
                            "Rejected from sample UI"));
        }
        return view(lab);
    }

    WorkflowView current() {
        LabRun lab = currentWorkflowRunId == null ? null : labs.get(currentWorkflowRunId);
        return lab == null ? WorkflowView.emptyView() : view(lab);
    }

    private LabRun currentLab() {
        if (currentWorkflowRunId == null || !labs.containsKey(currentWorkflowRunId)) {
            start("success");
        }
        return labs.get(currentWorkflowRunId);
    }

    private WorkflowView view(LabRun lab) {
        WorkflowSampleState.WorkflowRecord process = sampleState.workflow(lab.workflowRunId());
        ActionRun actionRun = runStore.find(lab.actionRunId()).orElse(null);
        Optional<FlowSnapshot> businessActive = activeSnapshot(lab.workflowRunId());
        Optional<FlowSnapshot> actionActive = activeSnapshot(lab.actionRunId());
        ActionExecutionResult result = actionRun == null ? null : actionRun.result();

        boolean businessTerminal = process != null
                && ("COMPLETED".equals(process.status()) || "FAILED".equals(process.status()));
        boolean actionTerminal = actionRun != null
                && (actionRun.status().isTerminal() || actionRun.status() == ActionRunStatus.WAITING_APPROVAL);

        return new WorkflowView(
                false,
                lab.workflowRunId(),
                lab.actionRunId(),
                lab.scenario().name(),
                controlContext(lab, actionRun),
                engineView(),
                flowTrack(
                        "Business Flow",
                        lab.workflowRunId(),
                        businessActive,
                        businessFlowState(process, businessActive),
                        BUSINESS_STEP_IDS,
                        businessTerminal,
                        true),
                flowTrack(
                        "Controlled Action Flow",
                        lab.actionRunId(),
                        actionActive,
                        actionFlowState(actionRun, actionActive),
                        ACTION_STAGE_IDS,
                        actionTerminal,
                        false),
                process == null ? "SUBMITTED" : process.status(),
                actionRun == null ? "NOT_SUBMITTED" : actionRun.status().name(),
                actionRun != null
                        && actionRun.status() == ActionRunStatus.WAITING_APPROVAL
                        && actionActive.isEmpty(),
                result == null ? null : result.status().name(),
                result == null ? "" : result.message(),
                result == null ? Map.of() : result.output(),
                combinedFlowEvents(lab),
                auditSink.all(lab.actionRunId()),
                sampleState.snapshot());
    }

    private Optional<FlowSnapshot> activeSnapshot(String runId) {
        return engine.dump().workers().stream()
                .flatMap(worker -> worker.flows().stream())
                .filter(flow -> runId.equals(flow.executionContext().runId().orElse("")))
                .findFirst();
    }

    private EngineView engineView() {
        EngineDump dump = engine.dump();
        List<WorkerView> workers = dump.workers().stream()
                .map(worker -> new WorkerView(
                        worker.name(),
                        worker.state().name(),
                        worker.driveMode().name(),
                        worker.intervalMillis(),
                        worker.flows().size()))
                .toList();
        return new EngineView(dump.engineState().name(), workers);
    }

    private FlowTrack flowTrack(
            String title,
            String runId,
            Optional<FlowSnapshot> active,
            String state,
            List<String> stepIds,
            boolean terminal,
            boolean businessFlow) {
        String currentStep = active.map(FlowSnapshot::currentStepId).orElse("");
        int currentStepIndex = active.map(FlowSnapshot::currentStepIndex).orElse(-1);
        List<String> visited = flowEvents.visitedSteps(runId);
        List<StepView> steps = new ArrayList<>();
        for (int i = 0; i < stepIds.size(); i++) {
            String stepId = stepIds.get(i);
            String stepState;
            if (i == currentStepIndex && !currentStep.isBlank()) {
                stepState = "current";
            } else if (visited.contains(stepId)) {
                stepState = "done";
            } else if (terminal) {
                stepState = "skipped";
            } else {
                stepState = "pending";
            }
            steps.add(new StepView(
                    i,
                    stepId,
                    stepState,
                    businessFlow ? businessStepDescription(stepId) : actionStageDescription(stepId)));
        }
        return new FlowTrack(
                title,
                runId,
                state,
                currentStep,
                active.isPresent(),
                steps);
    }

    private List<WorkflowFlowEvents.FlowEvent> combinedFlowEvents(LabRun lab) {
        List<WorkflowFlowEvents.FlowEvent> events = new ArrayList<>();
        events.addAll(flowEvents.all(lab.workflowRunId()));
        events.addAll(flowEvents.all(lab.actionRunId()));
        events.sort(Comparator.comparingInt(WorkflowFlowEvents.FlowEvent::no));
        return events;
    }

    private static String businessFlowState(
            WorkflowSampleState.WorkflowRecord process,
            Optional<FlowSnapshot> active) {
        if (active.isPresent()) {
            return active.get().state().name();
        }
        if (process == null) {
            return "NOT_SUBMITTED";
        }
        return switch (process.status()) {
            case "COMPLETED" -> "FINISHED";
            case "FAILED" -> "FAILED";
            default -> "SUBMITTED";
        };
    }

    private static String actionFlowState(ActionRun run, Optional<FlowSnapshot> active) {
        if (active.isPresent()) {
            return active.get().state().name();
        }
        if (run == null) {
            return "NOT_SUBMITTED";
        }
        if (run.status() == ActionRunStatus.WAITING_APPROVAL) {
            return "FLOW_FINISHED / WAITING_APPROVAL";
        }
        return run.status().isTerminal() ? "FINISHED" : "SUBMITTED";
    }

    private static ControlContext controlContext(LabRun lab, ActionRun run) {
        return new ControlContext(
                lab.scenario().title(),
                lab.proposal().actionId(),
                lab.proposal().origin().name(),
                lab.proposal().requesterId(),
                lab.proposal().input(),
                lab.scenario().controlExpectation(),
                run == null || run.policyDecisionType() == null ? "" : run.policyDecisionType().name(),
                run == null ? "" : run.policyReason(),
                run == null ? "" : run.approvalId());
    }

    private static String businessStepDescription(String stepId) {
        return switch (stepId) {
            case "prepare-report" -> "Prepare the long-running report publication process.";
            case "wait-document-upload" -> "Return STAY until an external document-upload event arrives; no request thread waits here.";
            case "submit-controlled-action" -> "Submit a separate governance Flow to the action Worker.";
            case "wait-controlled-action" -> "Keep the business Flow active while validation, policy, execution, or approval completes.";
            case "publish-report" -> "Publish the business result only after the controlled action succeeds.";
            default -> "";
        };
    }

    private static String actionStageDescription(String stepId) {
        return switch (stepId) {
            case "record-proposal" -> "Record who requested the action and start the audit trail.";
            case "reserve-duplicate" -> "Reserve the idempotency key so the same request cannot run twice.";
            case "resolve-action" -> "Load action risk and effect metadata from the registry.";
            case "validate-input" -> "Validate input before policy or execution.";
            case "evaluate-policy" -> "Decide allow, deny, dry-run, or approval.";
            case "execute-action" -> "Run the domain action only after every control gate passes.";
            case "record-result" -> "Finalize run and duplicate bookkeeping.";
            default -> "";
        };
    }

    private static Scenario scenario(String name) {
        return switch (name == null ? "success" : name) {
            case "ai-approval" -> new Scenario(
                    "ai-approval",
                    "AI proposes report creation",
                    "report.create",
                    ActionOrigin.AI_PLANNER,
                    Map.of("title", "AI generated inspection draft"),
                    "AI prepared a report after document upload.",
                    "The business Flow waits for the document. Then AI_PLANNER + WRITE reaches policy and waits for human approval.");
            case "validation-failure" -> new Scenario(
                    "validation-failure",
                    "Invalid report request",
                    "report.create",
                    ActionOrigin.USER,
                    Map.of(),
                    "The uploaded document has no report title.",
                    "After the external event, validation rejects the action and the parent business Flow fails safely.");
            case "execution-failure" -> new Scenario(
                    "execution-failure",
                    "Notification provider failure",
                    "notification.send",
                    ActionOrigin.USER,
                    Map.of("fail", true),
                    "Publish a completion notification after document upload.",
                    "Validation and policy pass, then execute-action captures the provider failure and the parent Flow observes it.");
            default -> new Scenario(
                    "success",
                    "User publishes an inspection report",
                    "report.create",
                    ActionOrigin.USER,
                    Map.of("title", "Site inspection report"),
                    "Create the report only after its source document arrives.",
                    "The business Flow waits asynchronously, then submits a governed write action and waits for its result.");
        };
    }

    private record Scenario(
            String name,
            String title,
            String actionId,
            ActionOrigin origin,
            Map<String, Object> input,
            String reason,
            String controlExpectation) {
    }

    private record LabRun(
            String workflowRunId,
            String actionRunId,
            Scenario scenario,
            ActionProposal proposal) {
    }

    record WorkflowView(
            boolean empty,
            String workflowRunId,
            String actionRunId,
            String scenario,
            ControlContext controlContext,
            EngineView engine,
            FlowTrack businessFlow,
            FlowTrack actionFlow,
            String businessStatus,
            String actionRunStatus,
            boolean approvalReady,
            String resultStatus,
            String resultMessage,
            Map<String, Object> resultOutput,
            List<WorkflowFlowEvents.FlowEvent> flowEvents,
            List<AuditEvent> audit,
            Map<String, Object> domainState) {

        static WorkflowView emptyView() {
            return new WorkflowView(
                    true,
                    "",
                    "",
                    "",
                    null,
                    new EngineView("NONE", List.of()),
                    FlowTrack.empty("Business Flow"),
                    FlowTrack.empty("Controlled Action Flow"),
                    "NONE",
                    "NONE",
                    false,
                    null,
                    "",
                    Map.of(),
                    List.of(),
                    List.of(),
                    Map.of());
        }
    }

    record ControlContext(
            String title,
            String actionId,
            String origin,
            String requester,
            Map<String, Object> input,
            String expectedControl,
            String policyDecision,
            String policyReason,
            String approvalId) {
    }

    record EngineView(String state, List<WorkerView> workers) {
    }

    record WorkerView(String name, String state, String driveMode, long intervalMillis, int activeFlows) {
    }

    record FlowTrack(
            String title,
            String runId,
            String state,
            String currentStep,
            boolean active,
            List<StepView> steps) {
        static FlowTrack empty(String title) {
            return new FlowTrack(title, "", "NONE", "", false, List.of());
        }
    }

    record StepView(int index, String stepId, String state, String description) {
    }
}
