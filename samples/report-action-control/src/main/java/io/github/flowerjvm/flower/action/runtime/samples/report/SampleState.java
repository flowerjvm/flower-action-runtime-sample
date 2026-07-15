package io.github.flowerjvm.flower.action.runtime.samples.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

final class SampleState {
    private final AtomicInteger reportIds = new AtomicInteger(1000);
    private final Map<Integer, ReportRecord> reports = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> notifications = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> refunds = new CopyOnWriteArrayList<>();
    private final List<String> deletedProjects = new CopyOnWriteArrayList<>();

    SampleState() {
        createReport("Existing inspection report", "seed");
    }

    ReportRecord createReport(String title, String createdBy) {
        int id = reportIds.incrementAndGet();
        ReportRecord record = new ReportRecord(id, title, createdBy, "DRAFT", Instant.now());
        reports.put(id, record);
        return record;
    }

    List<ReportRecord> reports() {
        return reports.values().stream()
                .sorted(Comparator.comparing(ReportRecord::id))
                .toList();
    }

    int submitReport(int reportId) {
        ReportRecord existing = reports.get(reportId);
        if (existing == null) {
            throw new IllegalArgumentException("report not found: " + reportId);
        }
        reports.put(reportId, new ReportRecord(
                existing.id(), existing.title(), existing.createdBy(), "SUBMITTED", existing.createdAt()));
        return reportId;
    }

    void deleteProject(String projectId) {
        deletedProjects.add(projectId);
    }

    void recordNotification(Map<String, Object> notification) {
        notifications.add(Map.copyOf(notification));
    }

    void recordRefund(Map<String, Object> refund) {
        refunds.add(Map.copyOf(refund));
    }

    Map<String, Object> snapshot() {
        return Map.of(
                "reports", new ArrayList<>(reports()),
                "notifications", new ArrayList<>(notifications),
                "refunds", new ArrayList<>(refunds),
                "deletedProjects", new ArrayList<>(deletedProjects));
    }

    record ReportRecord(int id, String title, String createdBy, String status, Instant createdAt) {
    }
}
