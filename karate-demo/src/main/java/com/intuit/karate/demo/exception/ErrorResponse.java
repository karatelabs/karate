package com.intuit.karate.demo.exception;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ErrorResponse {

    @JsonProperty("status_code")
    private int code;
    @JsonProperty("uri_path")
    private String path;
    private String method;
    @JsonProperty("error_message")
    private String message;

    public ErrorResponse() {
    }

    public ErrorResponse(int code, String path, String method, String message) {
        this.code = code;
        this.path = path;
        this.method = method;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
