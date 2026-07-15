package io.github.flowerjvm.flower.action.runtime.samples.report;

import io.github.flowerjvm.flower.action.runtime.ActionProposal;
import io.github.flowerjvm.flower.action.runtime.DefaultActionRuntime;
import io.github.flowerjvm.flower.action.runtime.ExecutionContext;
import io.github.flowerjvm.flower.action.runtime.action.ActionRegistry;
import io.github.flowerjvm.flower.action.runtime.action.InMemoryActionRegistry;
import io.github.flowerjvm.flower.action.runtime.approval.ApprovalGate;
import io.github.flowerjvm.flower.action.runtime.approval.ApprovalRequest;
import io.github.flowerjvm.flower.action.runtime.audit.AuditSink;
import io.github.flowerjvm.flower.action.runtime.duplicate.InMemoryDuplicateActionPolicy;
import io.github.flowerjvm.flower.action.runtime.policy.DefaultPolicyGate;
import io.github.flowerjvm.flower.action.runtime.run.RunStore;
import io.github.flowerjvm.flower.action.runtime.validation.ActionInputValidator;
import io.github.flowerjvm.flower.action.runtime.validation.ValidationResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SampleRuntimeConfig {
    @Bean
    SampleState sampleState() {
        return new SampleState();
    }

    @Bean
    RecordingRunStore runStore() {
        return new RecordingRunStore();
    }

    @Bean
    RecordingAuditSink auditSink() {
        return new RecordingAuditSink();
    }

    @Bean
    ActionRegistry actionRegistry(SampleState state) {
        return new InMemoryActionRegistry(List.of(
                new ReportViewAction(state),
                new ReportCreateAction(state),
                new ReportSubmitAction(state),
                new ProjectDeleteAction(state),
                new NotificationSendAction(state),
                new PaymentRefundAction(state)));
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
                        "riskLevel", definition.riskLevel().name(),
                        "effect", definition.effect().name()));
    }

    @Bean
    DefaultActionRuntime actionRuntime(
            ActionRegistry actionRegistry,
            ActionInputValidator inputValidator,
            ApprovalGate approvalGate,
            RecordingRunStore runStore,
            RecordingAuditSink auditSink) {
        return new DefaultActionRuntime(
                actionRegistry,
                inputValidator,
                new DefaultPolicyGate(),
                approvalGate,
                new InMemoryDuplicateActionPolicy(),
                auditSink,
                null,
                runStore);
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
