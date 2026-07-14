package io.github.parkkevinsb.flower.action.runtime.samples.report;

import io.github.parkkevinsb.flower.action.runtime.ActionExecutionResult;
import io.github.parkkevinsb.flower.action.runtime.ActionOrigin;
import io.github.parkkevinsb.flower.action.runtime.action.ActionDefinition;
import io.github.parkkevinsb.flower.action.runtime.action.ActionEffect;
import io.github.parkkevinsb.flower.action.runtime.action.ActionExecutionContext;
import io.github.parkkevinsb.flower.action.runtime.action.ActionExecutor;
import io.github.parkkevinsb.flower.action.runtime.action.ActionRiskLevel;
import java.util.Map;
import java.util.Set;

abstract class SampleAction implements ActionExecutor {
    private static final Set<ActionOrigin> ALL_ORIGINS =
            Set.of(ActionOrigin.USER, ActionOrigin.UI, ActionOrigin.API, ActionOrigin.AI_PLANNER);

    private final ActionDefinition definition;

    SampleAction(
            String actionId,
            String title,
            String description,
            ActionEffect effect,
            ActionRiskLevel riskLevel,
            Set<ActionOrigin> origins,
            boolean dryRunSupported,
            boolean approvalRequiredByDefault) {
        this.definition = new ActionDefinition(
                actionId,
                title,
                description,
                effect,
                riskLevel,
                origins,
                Set.of(),
                dryRunSupported,
                approvalRequiredByDefault,
                true,
                actionId + ".input",
                actionId + ".output",
                Map.of("sample", true));
    }

    @Override
    public final ActionDefinition definition() {
        return definition;
    }

    static Set<ActionOrigin> allOrigins() {
        return ALL_ORIGINS;
    }

    protected static String string(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    protected static int number(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}

final class ReportViewAction extends SampleAction {
    private final SampleState state;

    ReportViewAction(SampleState state) {
        super("report.view", "View reports", "Read the current in-memory reports.",
                ActionEffect.READ_ONLY, ActionRiskLevel.LOW, allOrigins(), false, false);
        this.state = state;
    }

    @Override
    public ActionExecutionResult execute(ActionExecutionContext context) {
        return ActionExecutionResult.succeeded(Map.of("reports", state.reports()));
    }
}

final class ReportCreateAction extends SampleAction {
    private final SampleState state;

    ReportCreateAction(SampleState state) {
        super("report.create", "Create report", "Create a draft report.",
                ActionEffect.WRITE, ActionRiskLevel.MEDIUM, allOrigins(), false, false);
        this.state = state;
    }

    @Override
    public ActionExecutionResult execute(ActionExecutionContext context) {
        SampleState.ReportRecord report = state.createReport(
                string(context.input().get("title"), "Untitled report"),
                context.executionContext().userId());
        return ActionExecutionResult.succeeded(Map.of("reportId", report.id(), "title", report.title()));
    }
}

final class ReportSubmitAction extends SampleAction {
    private final SampleState state;

    ReportSubmitAction(SampleState state) {
        super("report.submit", "Submit report", "Submit a report; approval is required by default.",
                ActionEffect.WRITE, ActionRiskLevel.MEDIUM, allOrigins(), false, true);
        this.state = state;
    }

    @Override
    public ActionExecutionResult execute(ActionExecutionContext context) {
        int reportId = number(context.input().get("reportId"), 1001);
        return ActionExecutionResult.succeeded(Map.of("submittedReportId", state.submitReport(reportId)));
    }
}

final class ProjectDeleteAction extends SampleAction {
    private final SampleState state;

    ProjectDeleteAction(SampleState state) {
        super("project.delete", "Delete project", "Critical action; default policy requires approval.",
                ActionEffect.PRODUCTION_CHANGE, ActionRiskLevel.CRITICAL,
                Set.of(ActionOrigin.USER, ActionOrigin.UI, ActionOrigin.API), false, false);
        this.state = state;
    }

    @Override
    public ActionExecutionResult execute(ActionExecutionContext context) {
        String projectId = string(context.input().get("projectId"), "project-1");
        state.deleteProject(projectId);
        return ActionExecutionResult.succeeded(Map.of("deletedProjectId", projectId));
    }
}

final class NotificationSendAction extends SampleAction {
    private final SampleState state;

    NotificationSendAction(SampleState state) {
        super("notification.send", "Send notification", "External send; fail=true triggers executor failure.",
                ActionEffect.EXTERNAL_SEND, ActionRiskLevel.MEDIUM,
                Set.of(ActionOrigin.USER, ActionOrigin.UI, ActionOrigin.API), false, false);
        this.state = state;
    }

    @Override
    public ActionExecutionResult execute(ActionExecutionContext context) {
        if (Boolean.TRUE.equals(context.input().get("fail"))) {
            throw new RuntimeException("notification provider failed");
        }
        Map<String, Object> notification = Map.of(
                "channel", string(context.input().get("channel"), "email"),
                "message", string(context.input().get("message"), "Inspection report is ready"));
        state.recordNotification(notification);
        return ActionExecutionResult.succeeded(notification);
    }
}

final class PaymentRefundAction extends SampleAction {
    private final SampleState state;

    PaymentRefundAction(SampleState state) {
        super("payment.refund", "Refund payment", "High-risk financial action.",
                ActionEffect.FINANCIAL, ActionRiskLevel.HIGH,
                Set.of(ActionOrigin.USER, ActionOrigin.UI, ActionOrigin.API), true, false);
        this.state = state;
    }

    @Override
    public ActionExecutionResult dryRun(ActionExecutionContext context) {
        return ActionExecutionResult.succeeded(Map.of(
                "dryRun", true,
                "amount", number(context.input().get("amount"), 100),
                "currency", string(context.input().get("currency"), "KRW")));
    }

    @Override
    public ActionExecutionResult execute(ActionExecutionContext context) {
        Map<String, Object> refund = Map.of(
                "amount", number(context.input().get("amount"), 100),
                "currency", string(context.input().get("currency"), "KRW"),
                "refundId", "refund-" + System.currentTimeMillis());
        state.recordRefund(refund);
        return ActionExecutionResult.succeeded(refund);
    }
}
