package com.tunicpay.riskassessment.exception;

public class UpstreamApiException extends ExternalServiceException {

    private final int statusCode;
    private final String serviceName;

    public UpstreamApiException(String serviceName, int statusCode, String message) {
        super(serviceName + " returned status " + statusCode + ": " + message);
        this.statusCode = statusCode;
        this.serviceName = serviceName;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getServiceName() {
        return serviceName;
    }
}
