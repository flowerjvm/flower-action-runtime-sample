package io.github.parkkevinsb.flower.action.runtime.samples.report;

import io.github.parkkevinsb.flower.action.runtime.audit.AuditEvent;
import io.github.parkkevinsb.flower.action.runtime.audit.AuditSink;
import io.github.parkkevinsb.flower.action.runtime.run.ActionRun;
import io.github.parkkevinsb.flower.action.runtime.run.ActionRunStatus;
import io.github.parkkevinsb.flower.action.runtime.run.RunStore;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

final class RecordingRunStore implements RunStore {
    private final ConcurrentMap<String, ActionRun> byId = new ConcurrentHashMap<>();

    @Override
    public ActionRun create(ActionRun run) {
        byId.put(run.runId(), run);
        return run;
    }

    @Override
    public Optional<ActionRun> find(String runId) {
        return Optional.ofNullable(byId.get(runId));
    }

    @Override
    public void update(ActionRun run) {
        byId.put(run.runId(), run);
    }

    @Override
    public List<ActionRun> findResumable(String tenantId) {
        return all(tenantId).stream()
                .filter(run -> !run.status().isTerminal())
                .toList();
    }

    List<ActionRun> all(String tenantId) {
        return byId.values().stream()
                .filter(run -> tenantId == null || tenantId.isBlank() || run.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(ActionRun::updatedAt).reversed())
                .toList();
    }

    List<ActionRun> waitingApprovals(String tenantId) {
        return all(tenantId).stream()
                .filter(run -> run.status() == ActionRunStatus.WAITING_APPROVAL)
                .toList();
    }
}

final class RecordingAuditSink implements AuditSink {
    private final CopyOnWriteArrayList<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(AuditEvent event) {
        events.add(event);
    }

    List<AuditEvent> all(String runId) {
        return events.stream()
                .filter(event -> runId == null || runId.isBlank() || event.runId().equals(runId))
                .sorted(Comparator.comparing(AuditEvent::occurredAt))
                .toList();
    }
}
