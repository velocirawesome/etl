package dev.velocirawesome.etl.model.entity;

import tools.jackson.databind.JsonNode;

public class Country {
    private String code;
    private JsonNode data;

    public Country() {
    }

    public Country(String code, Object data) {
        this.code = code;
        this.data = (JsonNode) data;
    }

    // Getters and Setters
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = (JsonNode) data;
    }
}
