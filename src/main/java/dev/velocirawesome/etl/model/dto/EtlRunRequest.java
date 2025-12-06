package dev.velocirawesome.etl.model.dto;

public class EtlRunRequest {
    private String sourceUrl;
    private Integer delayMs;

    public EtlRunRequest() {
    }

    public EtlRunRequest(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public EtlRunRequest(String sourceUrl, Integer delayMs) {
        this.sourceUrl = sourceUrl;
        this.delayMs = delayMs;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public Integer getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(Integer delayMs) {
        this.delayMs = delayMs;
    }
}
