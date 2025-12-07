package dev.velocirawesome.etl.model.dto;

import dev.velocirawesome.etl.model.entity.JobStatus;

public class EtlRunResponse {
    private Long jobId;
    private JobStatus status;
    private String message;

    public EtlRunResponse() {
    }

    public EtlRunResponse(Long jobId, JobStatus status, String message) {
        this.jobId = jobId;
        this.status = status;
        this.message = message;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
