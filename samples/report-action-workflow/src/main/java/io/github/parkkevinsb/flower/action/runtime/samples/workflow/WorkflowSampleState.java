package io.github.parkkevinsb.flower.action.runtime.samples.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

final class WorkflowSampleState {
    private final AtomicInteger reportIds = new AtomicInteger(1000);
    private final CopyOnWriteArrayList<ReportRecord> reports = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Map<String, Object>> notifications = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String, WorkflowRecord> workflows = new ConcurrentHashMap<>();

    void startWorkflow(String workflowRunId, String actionRunId, String scenario) {
        workflows.put(workflowRunId, new WorkflowRecord(
                workflowRunId,
                actionRunId,
                scenario,
                "SUBMITTED",
                false,
                false,
                "",
                Instant.now()));
    }

    void markWorkflowStatus(String workflowRunId, String status) {
        workflows.computeIfPresent(workflowRunId, (ignored, current) -> current.withStatus(status));
    }

    void markDocumentUploaded(String workflowRunId) {
        workflows.computeIfPresent(workflowRunId, (ignored, current) -> current.withDocumentUploaded());
    }

    boolean documentUploaded(String workflowRunId) {
        WorkflowRecord record = workflows.get(workflowRunId);
        return record != null && record.documentUploaded();
    }

    void markActionSubmitted(String workflowRunId) {
        workflows.computeIfPresent(workflowRunId, (ignored, current) -> current.withActionSubmitted());
    }

    boolean actionSubmitted(String workflowRunId) {
        WorkflowRecord record = workflows.get(workflowRunId);
        return record != null && record.actionSubmitted();
    }

    void markWorkflowFailed(String workflowRunId, String reason) {
        workflows.computeIfPresent(workflowRunId, (ignored, current) -> current.withFailure(reason));
    }

    WorkflowRecord workflow(String workflowRunId) {
        return workflows.get(workflowRunId);
    }

    ReportRecord createReport(String title, String createdBy) {
        ReportRecord record = new ReportRecord(reportIds.incrementAndGet(), title, createdBy, Instant.now());
        reports.add(record);
        return record;
    }

    void recordNotification(Map<String, Object> notification) {
        notifications.add(notification);
    }

    Map<String, Object> snapshot() {
        return Map.of(
                "workflows", new ArrayList<>(workflows.values()),
                "reports", new ArrayList<>(reports),
                "notifications", new ArrayList<>(notifications));
    }

    record WorkflowRecord(
            String workflowRunId,
            String actionRunId,
            String scenario,
            String status,
            boolean documentUploaded,
            boolean actionSubmitted,
            String failureReason,
            Instant updatedAt) {

        WorkflowRecord withStatus(String nextStatus) {
            return new WorkflowRecord(
                    workflowRunId,
                    actionRunId,
                    scenario,
                    nextStatus,
                    documentUploaded,
                    actionSubmitted,
                    failureReason,
                    Instant.now());
        }

        WorkflowRecord withDocumentUploaded() {
            return new WorkflowRecord(
                    workflowRunId,
                    actionRunId,
                    scenario,
                    "DOCUMENT_RECEIVED",
                    true,
                    actionSubmitted,
                    failureReason,
                    Instant.now());
        }

        WorkflowRecord withActionSubmitted() {
            return new WorkflowRecord(
                    workflowRunId,
                    actionRunId,
                    scenario,
                    "ACTION_SUBMITTED",
                    documentUploaded,
                    true,
                    failureReason,
                    Instant.now());
        }

        WorkflowRecord withFailure(String reason) {
            return new WorkflowRecord(
                    workflowRunId,
                    actionRunId,
                    scenario,
                    "FAILED",
                    documentUploaded,
                    actionSubmitted,
                    reason == null ? "" : reason,
                    Instant.now());
        }
    }

    record ReportRecord(int id, String title, String createdBy, Instant createdAt) {
    }
}
