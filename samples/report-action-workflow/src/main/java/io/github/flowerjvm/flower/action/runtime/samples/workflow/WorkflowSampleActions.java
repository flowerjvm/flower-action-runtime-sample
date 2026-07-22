package io.github.flowerjvm.flower.action.runtime.samples.workflow;

import io.github.flowerjvm.flower.action.runtime.ActionExecutionResult;
import io.github.flowerjvm.flower.action.runtime.ActionProposerType;
import io.github.flowerjvm.flower.action.runtime.ActionRequestChannel;
import io.github.flowerjvm.flower.action.runtime.action.ActionDefinition;
import io.github.flowerjvm.flower.action.runtime.action.ActionEffect;
import io.github.flowerjvm.flower.action.runtime.action.ActionExecutionContext;
import io.github.flowerjvm.flower.action.runtime.action.ActionRiskLevel;
import io.github.flowerjvm.flower.action.runtime.action.SynchronousActionExecutor;
import java.util.Map;
import java.util.Set;

abstract class WorkflowSampleAction implements SynchronousActionExecutor {
    private static final Set<ActionRequestChannel> ALL_REQUEST_CHANNELS =
            Set.of(
                    ActionRequestChannel.UI,
                    ActionRequestChannel.API,
                    ActionRequestChannel.CLI,
                    ActionRequestChannel.COMMAND);
    private static final Set<ActionProposerType> ALL_PROPOSER_TYPES =
            Set.of(
                    ActionProposerType.USER,
                    ActionProposerType.AI_PLANNER,
                    ActionProposerType.SYSTEM,
                    ActionProposerType.SERVICE);
    private static final Set<ActionProposerType> NON_AI_PROPOSER_TYPES =
            Set.of(ActionProposerType.USER, ActionProposerType.SYSTEM, ActionProposerType.SERVICE);

    private final ActionDefinition definition;

    WorkflowSampleAction(
            String actionId,
            String title,
            String description,
            ActionEffect effect,
            ActionRiskLevel riskLevel,
            Set<ActionRequestChannel> requestChannels,
            Set<ActionProposerType> proposerTypes) {
        this.definition = new ActionDefinition(
                actionId,
                title,
                description,
                effect,
                riskLevel,
                requestChannels,
                proposerTypes,
                Set.of(),
                false,
                false,
                true,
                actionId + ".input",
                actionId + ".output",
                Map.of("sample", "workflow"));
    }

    @Override
    public final ActionDefinition definition() {
        return definition;
    }

    static Set<ActionRequestChannel> allRequestChannels() {
        return ALL_REQUEST_CHANNELS;
    }

    static Set<ActionProposerType> allProposerTypes() {
        return ALL_PROPOSER_TYPES;
    }

    static Set<ActionProposerType> nonAiProposerTypes() {
        return NON_AI_PROPOSER_TYPES;
    }

    static String text(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }
}

final class WorkflowReportCreateAction extends WorkflowSampleAction {
    private final WorkflowSampleState state;

    WorkflowReportCreateAction(WorkflowSampleState state) {
        super(
                "report.create",
                "Create report",
                "Create a draft report.",
                ActionEffect.WRITE,
                ActionRiskLevel.MEDIUM,
                allRequestChannels(),
                allProposerTypes());
        this.state = state;
    }

    @Override
    public ActionExecutionResult execute(ActionExecutionContext context) {
        WorkflowSampleState.ReportRecord report = state.createReport(
                text(context.input().get("title"), "Untitled report"),
                context.executionContext().userId());
        return ActionExecutionResult.succeeded(Map.of(
                "reportId", report.id(),
                "title", report.title()));
    }
}

final class WorkflowNotificationSendAction extends WorkflowSampleAction {
    private final WorkflowSampleState state;

    WorkflowNotificationSendAction(WorkflowSampleState state) {
        super(
                "notification.send",
                "Send notification",
                "External send; fail=true triggers executor failure.",
                ActionEffect.EXTERNAL_SEND,
                ActionRiskLevel.MEDIUM,
                allRequestChannels(),
                nonAiProposerTypes());
        this.state = state;
    }

    @Override
    public ActionExecutionResult execute(ActionExecutionContext context) {
        if (Boolean.TRUE.equals(context.input().get("fail"))) {
            throw new RuntimeException("notification provider failed");
        }
        Map<String, Object> notification = Map.of(
                "channel", text(context.input().get("channel"), "email"),
                "message", text(context.input().get("message"), "Workflow sample completed"));
        state.recordNotification(notification);
        return ActionExecutionResult.succeeded(notification);
    }
}
