package io.github.flowerjvm.flower.action.runtime.samples.report;

import io.github.flowerjvm.flower.action.runtime.ActionExecutionResult;
import io.github.flowerjvm.flower.action.runtime.ActionExecutionStatus;
import io.github.flowerjvm.flower.action.runtime.ActionProposerType;
import io.github.flowerjvm.flower.action.runtime.ActionProposal;
import io.github.flowerjvm.flower.action.runtime.ActionRequestChannel;
import io.github.flowerjvm.flower.action.runtime.DefaultActionRuntime;
import io.github.flowerjvm.flower.action.runtime.ExecutionContext;
import io.github.flowerjvm.flower.action.runtime.action.ActionDefinition;
import io.github.flowerjvm.flower.action.runtime.action.ActionRegistry;
import io.github.flowerjvm.flower.action.runtime.approval.ApprovalDecision;
import io.github.flowerjvm.flower.action.runtime.audit.AuditEvent;
import io.github.flowerjvm.flower.action.runtime.run.ActionRun;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class SampleApiController {
    private static final String TENANT_ID = "demo";

    private final DefaultActionRuntime runtime;
    private final ActionRegistry registry;
    private final RecordingRunStore runStore;
    private final RecordingAuditSink auditSink;
    private final SampleState state;

    SampleApiController(
            DefaultActionRuntime runtime,
            ActionRegistry registry,
            RecordingRunStore runStore,
            RecordingAuditSink auditSink,
            SampleState state) {
        this.runtime = runtime;
        this.registry = registry;
        this.runStore = runStore;
        this.auditSink = auditSink;
        this.state = state;
    }

    @GetMapping("/actions")
    List<ActionDefinition> actions() {
        return registry.definitions();
    }

    @PostMapping("/actions/{actionId}/propose")
    ActionResponse propose(@PathVariable String actionId, @RequestBody ProposeRequest request) {
        String runId = UUID.randomUUID().toString();
        ActionRequestChannel requestChannel = request.requestChannel() == null
                ? ActionRequestChannel.UI
                : request.requestChannel();
        ActionProposerType proposerType = request.proposerType() == null
                ? ActionProposerType.USER
                : request.proposerType();
        String requesterId = blank(request.requesterId())
                ? proposerType.name().toLowerCase() + "-sample"
                : request.requesterId();
        ActionProposal proposal = ActionProposal.builder(actionId)
                .requestChannel(requestChannel)
                .proposerType(proposerType)
                .requesterId(requesterId)
                .reason(blank(request.reason()) ? "sample proposal" : request.reason())
                .confidence(request.confidence() == null ? 1.0d : request.confidence())
                .input(request.input() == null ? Map.of() : request.input())
                .idempotencyKey(request.idempotencyKey())
                .metadata(Map.of("sample", "report-action-control"))
                .build();
        ExecutionContext context = new ExecutionContext(
                TENANT_ID,
                requesterId,
                runId,
                runId + "-trace",
                Map.of(
                        "requestChannel", requestChannel.name(),
                        "proposerType", proposerType.name(),
                        "sample", "report-action-control"));

        ActionExecutionResult result = runtime.handle(proposal, context);
        return ActionResponse.from(runId, result);
    }

    @GetMapping("/runs")
    List<ActionRun> runs(@RequestParam(defaultValue = TENANT_ID) String tenantId) {
        return runStore.all(tenantId);
    }

    @GetMapping("/runs/{runId}")
    ResponseEntity<RunDetails> run(@PathVariable String runId) {
        return runStore.find(runId)
                .map(run -> ResponseEntity.ok(new RunDetails(run, auditSink.all(runId))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/approvals")
    List<ActionRun> approvals(@RequestParam(defaultValue = TENANT_ID) String tenantId) {
        return runStore.waitingApprovals(tenantId);
    }

    @PostMapping("/approvals/{runId}/approve")
    ActionResponse approve(@PathVariable String runId, @RequestBody ApprovalBody body) {
        ActionRun run = runStore.find(runId).orElseThrow();
        ActionExecutionResult result = runtime.resume(
                runId,
                ApprovalDecision.approved(run.approvalId(), blank(body.resolvedBy()) ? "manager" : body.resolvedBy()));
        return ActionResponse.from(runId, result);
    }

    @PostMapping("/approvals/{runId}/reject")
    ActionResponse reject(@PathVariable String runId, @RequestBody ApprovalBody body) {
        ActionRun run = runStore.find(runId).orElseThrow();
        ActionExecutionResult result = runtime.resume(
                runId,
                ApprovalDecision.rejected(
                        run.approvalId(),
                        blank(body.resolvedBy()) ? "manager" : body.resolvedBy(),
                        blank(body.reason()) ? "rejected from sample UI" : body.reason()));
        return ActionResponse.from(runId, result);
    }

    @GetMapping("/audit")
    List<AuditEvent> audit(@RequestParam(required = false) String runId) {
        return auditSink.all(runId);
    }

    @GetMapping("/state")
    Map<String, Object> state() {
        return state.snapshot();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    record ProposeRequest(
            ActionRequestChannel requestChannel,
            ActionProposerType proposerType,
            String requesterId,
            String reason,
            Double confidence,
            String idempotencyKey,
            Map<String, Object> input) {
    }

    record ApprovalBody(String resolvedBy, String reason) {
    }

    record ActionResponse(
            String runId,
            ActionExecutionStatus status,
            String message,
            Map<String, Object> output) {
        static ActionResponse from(String runId, ActionExecutionResult result) {
            return new ActionResponse(runId, result.status(), result.message(), result.output());
        }
    }

    record RunDetails(ActionRun run, List<AuditEvent> audit) {
    }
}
