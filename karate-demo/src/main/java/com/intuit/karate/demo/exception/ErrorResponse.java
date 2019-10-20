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

  /**
   * @return the code
   */
  public int getCode() {
  return code;}

  /**
   * @param code the code to set
   */
  public void setCode(int code) {
  this.code = code;}

  /**
   * @return the path
   */
  public String getPath() {
  return path;}

  /**
   * @param path the path to set
   */
  public void setPath(String path) {
  this.path = path;}

  /**
   * @return the method
   */
  public String getMethod() {
  return method;}

  /**
   * @param method the method to set
   */
  public void setMethod(String method) {
  this.method = method;}

  /**
   * @return the message
   */
  public String getMessage() {
  return message;}

  /**
   * @param message the message to set
   */
  public void setMessage(String message) {
  this.message = message;}

    
}

