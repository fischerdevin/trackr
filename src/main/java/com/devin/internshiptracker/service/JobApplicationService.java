package com.devin.internshiptracker.service;

import com.devin.internshiptracker.entity.ApplicationStatus;
import com.devin.internshiptracker.entity.JobApplication;
import com.devin.internshiptracker.repository.JobApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class JobApplicationService {

    private final JobApplicationRepository repository;
    private final AccountService accounts;

    public JobApplicationService(JobApplicationRepository repository, AccountService accounts) {
        this.repository = repository;
        this.accounts = accounts;
    }

    public List<JobApplication> findApplications(
            ApplicationStatus status, String company, String search) {
        Long ownerId = accounts.currentUser().getId();
        if (status != null) {
            return repository.findByOwnerIdAndStatus(ownerId, status);
        }
        if (company != null && !company.isBlank()) {
            return repository.findByOwnerIdAndCompanyNameContainingIgnoreCase(ownerId, company);
        }
        if (search != null && !search.isBlank()) {
            return repository.findByOwnerIdAndJobTitleContainingIgnoreCase(ownerId, search);
        }
        return repository.findByOwnerId(ownerId);
    }

    public JobApplication findById(Long id) {
        return repository.findByIdAndOwnerId(id, accounts.currentUser().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Application not found"));
    }

    public JobApplication create(JobApplication application) {
        application.setOwner(accounts.currentUser());
        return repository.save(application);
    }

    public JobApplication update(Long id, JobApplication updatedApplication) {
        JobApplication existingApplication = findById(id);
        existingApplication.setCompanyName(updatedApplication.getCompanyName());
        existingApplication.setJobTitle(updatedApplication.getJobTitle());
        existingApplication.setStatus(updatedApplication.getStatus());
        existingApplication.setLocation(updatedApplication.getLocation());
        existingApplication.setJobUrl(updatedApplication.getJobUrl());
        existingApplication.setDateApplied(updatedApplication.getDateApplied());
        existingApplication.setInterviewDate(updatedApplication.getInterviewDate());
        return repository.save(existingApplication);
    }

    public void delete(Long id) {
        repository.delete(findById(id));
    }
}
