package dev.velocirawesome.etl.model.dto;

public class EtlRunResponse {
    private Long jobId;
    private String status;
    private String message;

    public EtlRunResponse() {
    }

    public EtlRunResponse(Long jobId, String status, String message) {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
