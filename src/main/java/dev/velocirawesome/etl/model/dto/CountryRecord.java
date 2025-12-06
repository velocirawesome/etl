package dev.velocirawesome.etl.model.dto;

import tools.jackson.databind.JsonNode;

public class CountryRecord {
    private String code;
    private JsonNode data;

    public CountryRecord() {
    }

    public CountryRecord(String code, JsonNode data) {
        this.code = code;
        this.data = data;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
    }
}
