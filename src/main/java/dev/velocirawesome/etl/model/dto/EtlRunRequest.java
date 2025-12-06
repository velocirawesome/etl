package dev.velocirawesome.etl.model.dto;

public class EtlRunRequest {
    private String sourceUrl;

    public EtlRunRequest() {
    }

    public EtlRunRequest(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }
}
