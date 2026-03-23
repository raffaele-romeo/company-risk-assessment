package com.tunicpay.riskassessment.model;

public record AdverseMediaFinding(
        String source,
        String headline,
        String date,
        String summary
) {}
