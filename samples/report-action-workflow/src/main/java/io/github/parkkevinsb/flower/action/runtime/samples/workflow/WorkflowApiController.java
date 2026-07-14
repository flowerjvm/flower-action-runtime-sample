package io.github.parkkevinsb.flower.action.runtime.samples.workflow;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow")
class WorkflowApiController {
    private final WorkflowLabService service;

    WorkflowApiController(WorkflowLabService service) {
        this.service = service;
    }

    @GetMapping
    WorkflowLabService.WorkflowView current() {
        return service.current();
    }

    @PostMapping("/start/{scenario}")
    WorkflowLabService.WorkflowView start(@PathVariable String scenario) {
        return service.start(scenario);
    }

    @PostMapping("/document-uploaded")
    WorkflowLabService.WorkflowView documentUploaded() {
        return service.uploadCurrentDocument();
    }

    @PostMapping("/approve")
    WorkflowLabService.WorkflowView approve() {
        return service.approveCurrent();
    }

    @PostMapping("/reject")
    WorkflowLabService.WorkflowView reject() {
        return service.rejectCurrent();
    }
}
