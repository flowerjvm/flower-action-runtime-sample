package io.github.parkkevinsb.flower.action.runtime.samples.workflow;

import io.github.parkkevinsb.flower.action.runtime.ActionExecutionResult;
import io.github.parkkevinsb.flower.action.runtime.ActionOrigin;
import io.github.parkkevinsb.flower.action.runtime.action.ActionDefinition;
import io.github.parkkevinsb.flower.action.runtime.action.ActionEffect;
import io.github.parkkevinsb.flower.action.runtime.action.ActionExecutionContext;
import io.github.parkkevinsb.flower.action.runtime.action.ActionExecutor;
import io.github.parkkevinsb.flower.action.runtime.action.ActionRiskLevel;
import java.util.Map;
import java.util.Set;

abstract class WorkflowSampleAction implements ActionExecutor {
    private static final Set<ActionOrigin> ALL_ORIGINS =
            Set.of(ActionOrigin.USER, ActionOrigin.UI, ActionOrigin.API, ActionOrigin.AI_PLANNER);

    private final ActionDefinition definition;

    WorkflowSampleAction(
            String actionId,
            String title,
            String description,
            ActionEffect effect,
            ActionRiskLevel riskLevel,
            Set<ActionOrigin> origins) {
        this.definition = new ActionDefinition(
                actionId,
                title,
                description,
                effect,
                riskLevel,
                origins,
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

    static Set<ActionOrigin> allOrigins() {
        return ALL_ORIGINS;
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
                allOrigins());
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
                Set.of(ActionOrigin.USER, ActionOrigin.UI, ActionOrigin.API));
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
