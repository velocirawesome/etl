package dev.velocirawesome.etl.model.entity;

import tools.jackson.databind.JsonNode;

public class Country {
    private String code;
    private JsonNode data;

    public Country() {
    }

    public Country(String code, JsonNode data) {
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
