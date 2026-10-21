package com.devin.internshiptracker.repository;
import com.devin.internshiptracker.entity.JobApplication;
import com.devin.internshiptracker.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobApplicationRepository
        extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByOwnerId(Long ownerId);

    java.util.Optional<JobApplication> findByIdAndOwnerId(Long id, Long ownerId);

    List<JobApplication> findByOwnerIdAndStatus(Long ownerId, ApplicationStatus status);

    List<JobApplication> findByOwnerIdAndCompanyNameContainingIgnoreCase(Long ownerId, String companyName);

    List<JobApplication> findByOwnerIdAndJobTitleContainingIgnoreCase(Long ownerId, String jobTitle);
}
