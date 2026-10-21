package com.devin.internshiptracker.controller;

import com.devin.internshiptracker.entity.ApplicationStatus;
import com.devin.internshiptracker.entity.JobApplication;
import com.devin.internshiptracker.service.JobApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class JobApplicationController {

    private final JobApplicationService service;

    public JobApplicationController(JobApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public List<JobApplication> getApplications(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String search) {
        return service.findApplications(status, company, search);
    }

    @GetMapping("/{id}")
    public JobApplication getApplication(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobApplication createApplication(
            @Valid @RequestBody JobApplication application) {
        return service.create(application);
    }

    @PutMapping("/{id}")
    public JobApplication updateApplication(
            @PathVariable Long id,
            @Valid @RequestBody JobApplication application) {
        return service.update(id, application);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteApplication(@PathVariable Long id) {
        service.delete(id);
    }
}
