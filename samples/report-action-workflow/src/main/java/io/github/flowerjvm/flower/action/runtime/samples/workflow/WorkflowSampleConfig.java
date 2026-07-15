package io.github.flowerjvm.flower.action.runtime.samples.workflow;

import io.github.flowerjvm.flower.action.runtime.ActionProposal;
import io.github.flowerjvm.flower.action.runtime.DefaultActionRuntime;
import io.github.flowerjvm.flower.action.runtime.action.ActionRegistry;
import io.github.flowerjvm.flower.action.runtime.action.InMemoryActionRegistry;
import io.github.flowerjvm.flower.action.runtime.approval.ApprovalGate;
import io.github.flowerjvm.flower.action.runtime.approval.ApprovalRequest;
import io.github.flowerjvm.flower.action.runtime.audit.TraceSink;
import io.github.flowerjvm.flower.action.runtime.duplicate.InMemoryDuplicateActionPolicy;
import io.github.flowerjvm.flower.action.runtime.policy.DefaultPolicyGate;
import io.github.flowerjvm.flower.action.runtime.validation.ActionInputValidator;
import io.github.flowerjvm.flower.action.runtime.validation.ValidationResult;
import io.github.flowerjvm.flower.action.runtime.workflow.WorkflowActionRuntime;
import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.EventBus;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.time.Clock;
import io.github.flowerjvm.flower.core.time.SystemClock;
import io.github.flowerjvm.flower.core.worker.Worker;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class WorkflowSampleConfig {
    @Bean
    WorkflowSampleState sampleState() {
        return new WorkflowSampleState();
    }

    @Bean
    WorkflowRunStore runStore() {
        return new WorkflowRunStore();
    }

    @Bean
    WorkflowAuditSink auditSink() {
        return new WorkflowAuditSink();
    }

    @Bean
    WorkflowFlowEvents flowEvents() {
        return new WorkflowFlowEvents();
    }

    @Bean
    EventBus eventBus() {
        return InMemoryEventBus.create();
    }

    @Bean
    Clock flowerClock() {
        return SystemClock.INSTANCE;
    }

    @Bean
    ActionRegistry actionRegistry(WorkflowSampleState state) {
        return new InMemoryActionRegistry(List.of(
                new WorkflowReportCreateAction(state),
                new WorkflowNotificationSendAction(state)));
    }

    @Bean
    ActionInputValidator inputValidator() {
        return (proposal, definition, context) -> validate(proposal);
    }

    @Bean
    ApprovalGate approvalGate() {
        return (proposal, definition, context, decision) -> new ApprovalRequest(
                null,
                proposal.proposalId(),
                decision.reason(),
                Instant.now(),
                Map.of(
                        "actionId", proposal.actionId(),
                        "origin", proposal.origin().name(),
                        "stage", "evaluate-policy"));
    }

    @Bean
    InMemoryDuplicateActionPolicy duplicateActionPolicy() {
        return new InMemoryDuplicateActionPolicy();
    }

    @Bean
    DefaultActionRuntime resumableRuntime(
            ActionRegistry actionRegistry,
            ActionInputValidator inputValidator,
            ApprovalGate approvalGate,
            InMemoryDuplicateActionPolicy duplicateActionPolicy,
            WorkflowRunStore runStore,
            WorkflowAuditSink auditSink) {
        return new DefaultActionRuntime(
                actionRegistry,
                inputValidator,
                new DefaultPolicyGate(),
                approvalGate,
                duplicateActionPolicy,
                auditSink,
                TraceSink.noop(),
                runStore);
    }

    @Bean
    WorkflowActionRuntime workflowRuntime(
            ActionRegistry actionRegistry,
            ActionInputValidator inputValidator,
            ApprovalGate approvalGate,
            InMemoryDuplicateActionPolicy duplicateActionPolicy,
            WorkflowRunStore runStore,
            WorkflowAuditSink auditSink,
            Clock flowerClock,
            EventBus eventBus) {
        return new WorkflowActionRuntime(
                actionRegistry,
                inputValidator,
                new DefaultPolicyGate(),
                approvalGate,
                duplicateActionPolicy,
                auditSink,
                TraceSink.noop(),
                flowerClock,
                eventBus,
                64,
                runStore);
    }

    @Bean(destroyMethod = "stop")
    Engine workflowEngine(Clock flowerClock, EventBus eventBus, WorkflowFlowEvents flowEvents) {
        Engine engine = Engine.builder()
                .clock(flowerClock)
                .eventBus(eventBus)
                .worker(Worker.builder("report-workflow").intervalMillis(2000).build())
                .worker(Worker.builder("action-workflow").intervalMillis(2000).build())
                .listener(flowEvents)
                .build();
        engine.start();
        return engine;
    }

    private static ValidationResult validate(ActionProposal proposal) {
        if ("report.create".equals(proposal.actionId()) && blank(proposal.input().get("title"))) {
            return ValidationResult.invalid("title is required");
        }
        return ValidationResult.ok();
    }

    private static boolean blank(Object value) {
        return value == null || value.toString().isBlank();
    }
}
