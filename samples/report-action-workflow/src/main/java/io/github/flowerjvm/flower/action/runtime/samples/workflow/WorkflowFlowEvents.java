package io.github.flowerjvm.flower.action.runtime.samples.workflow;

import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.listener.FlowerListener;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

final class WorkflowFlowEvents implements FlowerListener {
    private final AtomicInteger sequence = new AtomicInteger();
    private final CopyOnWriteArrayList<FlowEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void onFlowSubmitted(FlowSnapshot flow) {
        record(flow, "flow-submitted", "");
    }

    @Override
    public void onStepEntered(FlowSnapshot flow, String stepId) {
        record(flow, "step-entered", stepId);
    }

    @Override
    public void onStepExited(FlowSnapshot flow, String stepId) {
        record(flow, "step-exited", stepId);
    }

    @Override
    public void onFlowFinished(FlowSnapshot flow) {
        record(flow, "flow-finished", "");
    }

    @Override
    public void onFlowFailed(FlowSnapshot flow, Throwable cause) {
        record(flow, "flow-failed", "");
    }

    @Override
    public void onFlowCancelled(FlowSnapshot flow) {
        record(flow, "flow-cancelled", "");
    }

    List<FlowEvent> all(String runId) {
        return events.stream()
                .filter(event -> runId == null || runId.isBlank() || event.runId().equals(runId))
                .toList();
    }

    List<String> visitedSteps(String runId) {
        return all(runId).stream()
                .filter(event -> "step-entered".equals(event.type()))
                .map(FlowEvent::stepId)
                .filter(stepId -> stepId != null && !stepId.isBlank())
                .distinct()
                .toList();
    }

    private void record(FlowSnapshot flow, String type, String stepId) {
        String runId = flow.executionContext().runId().orElse("");
        events.add(new FlowEvent(
                sequence.incrementAndGet(),
                runId == null ? "" : runId,
                type,
                stepId == null ? "" : stepId,
                flow.state().name(),
                flow.currentStepId() == null ? "" : flow.currentStepId(),
                Instant.now()));
    }

    record FlowEvent(
            int no,
            String runId,
            String type,
            String stepId,
            String flowState,
            String currentStepId,
            Instant at) {
    }
}
