package com.tunicpay.riskassessment.controller;

public class UpstreamFailureException extends RuntimeException {

    public UpstreamFailureException(String message) {
        super(message);
    }
}
