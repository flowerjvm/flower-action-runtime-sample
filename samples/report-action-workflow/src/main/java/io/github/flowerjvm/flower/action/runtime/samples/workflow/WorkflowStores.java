package io.github.flowerjvm.flower.action.runtime.samples.workflow;

import io.github.flowerjvm.flower.action.runtime.audit.AuditEvent;
import io.github.flowerjvm.flower.action.runtime.audit.AuditSink;
import io.github.flowerjvm.flower.action.runtime.run.ActionRun;
import io.github.flowerjvm.flower.action.runtime.run.RunStore;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

final class WorkflowRunStore implements RunStore {
    private final ConcurrentMap<String, ActionRun> byId = new ConcurrentHashMap<>();

    @Override
    public ActionRun create(ActionRun run) {
        if (byId.putIfAbsent(run.runId(), run) != null) {
            throw new IllegalStateException("Action run already exists: " + run.runId());
        }
        return run;
    }

    @Override
    public Optional<ActionRun> find(String runId) {
        return Optional.ofNullable(byId.get(runId));
    }

    @Override
    public boolean compareAndSet(ActionRun expected, ActionRun updated) {
        if (!expected.runId().equals(updated.runId())) {
            throw new IllegalArgumentException("compare-and-set run ids must match");
        }
        if (updated.version() != expected.version() + 1L) {
            throw new IllegalArgumentException("updated run version must be expected version + 1");
        }
        AtomicBoolean changed = new AtomicBoolean(false);
        byId.computeIfPresent(expected.runId(), (runId, current) -> {
            if (current.version() != expected.version()) {
                return current;
            }
            changed.set(true);
            return updated;
        });
        return changed.get();
    }

    @Override
    public List<ActionRun> findResumable(String tenantId) {
        return all().stream()
                .filter(run -> tenantId == null || tenantId.isBlank() || run.tenantId().equals(tenantId))
                .filter(run -> !run.status().isTerminal())
                .toList();
    }

    List<ActionRun> all() {
        return byId.values().stream()
                .sorted(Comparator.comparing(ActionRun::updatedAt).reversed())
                .toList();
    }
}

final class WorkflowAuditSink implements AuditSink {
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
