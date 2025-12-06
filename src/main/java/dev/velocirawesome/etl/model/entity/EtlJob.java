package dev.velocirawesome.etl.model.entity;

import java.time.LocalDateTime;

public class EtlJob {
    private Long jobId;
    private String sourceUrl;
    private JobStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long recordsExtracted;
    private Long recordsTransformed;
    private Long recordsLoaded;
    private String errorMessage;

    public EtlJob() {
    }

    public EtlJob(Long jobId, String sourceUrl, JobStatus status, LocalDateTime startTime) {
        this.jobId = jobId;
        this.sourceUrl = sourceUrl;
        this.status = status;
        this.startTime = startTime;
        this.recordsExtracted = 0L;
        this.recordsTransformed = 0L;
        this.recordsLoaded = 0L;
    }

    // Getters and Setters
    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Long getRecordsExtracted() {
        return recordsExtracted;
    }

    public void setRecordsExtracted(Long recordsExtracted) {
        this.recordsExtracted = recordsExtracted;
    }

    public Long getRecordsTransformed() {
        return recordsTransformed;
    }

    public void setRecordsTransformed(Long recordsTransformed) {
        this.recordsTransformed = recordsTransformed;
    }

    public Long getRecordsLoaded() {
        return recordsLoaded;
    }

    public void setRecordsLoaded(Long recordsLoaded) {
        this.recordsLoaded = recordsLoaded;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
